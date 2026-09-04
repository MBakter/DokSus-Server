package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.features.reference_data.group.GroupService
import hr.alu.okiru.doksusserver.features.user.UserDto
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import kotlin.time.Clock

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val userRepository: UserRepository,
    private val groupService: GroupService
) {

    fun getDocumentById(id: String): Document? {
        val document = documentRepository.findById(id).orElse(null) ?: return null
        return enrich(listOf(document)).first()
    }

    fun getDocuments(
        category: String?,
        search: String?,
        allowedVisibilities: List<Document.Visibility>,
        pageable: Pageable
    ): Page<Document> {
        val page = documentRepository.searchDocuments(category, search, allowedVisibilities, pageable)
        enrich(page.content)
        return page
    }

    fun getMyDocuments(email: String): List<Document> {
        val documents = documentRepository.findByCreatorEmail(email)
        return enrich(documents)
    }

    fun getUserPublishedDocuments(email: String, allowedVisibilities: List<Document.Visibility>): List<Document> {
        val createdDocs = documentRepository.findByCreatorEmailAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)
        val coCreatedDocs = documentRepository.findByCoCreatorEmailsContainingAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)
        val mentoredDocs = documentRepository.findByMentorEmailsContainingAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)

        // Combine all three lists and ensure no duplicates by ID
        val allDocuments = (createdDocs + coCreatedDocs + mentoredDocs).distinctBy { it.id }

        return enrich(allDocuments)
    }

    fun createDocument(document: Document): Document {
        val saved = documentRepository.save(document)

        // If created as published immediately, register the group
        if (saved.isPublished) {
            groupService.addGroupIfNotExists(saved.restorationData.group)
        }
        return enrich(listOf(saved)).first()
    }

    fun updateMetadata(id: String, request: DocumentMetadataUpdateRequest, principalEmail: String): Document {
        val existingDocument = documentRepository.findById(id).orElseThrow { RuntimeException("Not found") }

        if (existingDocument.creatorEmail != principalEmail) {
            throw RuntimeException("Access denied")
        }

        val wasPublished = existingDocument.isPublished
        val isNowPublished = request.isPublished
        val oldGroup = existingDocument.restorationData.group
        val newGroup = request.content.group

        // Apply updates
        existingDocument.restorationData = request.content
        existingDocument.isPublished = request.isPublished
        existingDocument.visibility = request.visibility
        existingDocument.coCreatorEmails = request.coCreatorEmails
        existingDocument.mentorEmails = request.mentorEmails

        if (request.isPublished && existingDocument.publicationDate == null) {
            existingDocument.publicationDate = Clock.System.now()
        } else if (!request.isPublished) {
            existingDocument.publicationDate = null
        }

        val saved = documentRepository.save(existingDocument)

        // --- GROUP LIFECYCLE SYNC ---

        // 1. If it is published now, ensure the new group exists
        if (isNowPublished) {
            groupService.addGroupIfNotExists(newGroup)
        }

        // 2. If it WAS published, check if we need to clean up the old group
        if (wasPublished) {
            // Clean up if it was unpublished, OR if the group name was changed
            if (!isNowPublished || oldGroup != newGroup) {
                groupService.removeGroupIfOrphaned(oldGroup)
            }
        }

        return enrich(listOf(saved)).first()
    }

    fun deleteDocument(id: String, principalEmail: String) {
        val document = documentRepository.findById(id).orElseThrow { RuntimeException("Not found") }

        if (document.creatorEmail != principalEmail) {
            throw RuntimeException("Access denied")
        }

        val groupToCleanUp = document.restorationData.group
        val wasPublished = document.isPublished

        // Delete the document first so existsBy... evaluates correctly
        documentRepository.delete(document)

        // If it was published, check if its group is now completely orphaned
        if (wasPublished) {
            groupService.removeGroupIfOrphaned(groupToCleanUp)
        }
    }


// HELPERS

    /**
     * Centralized pipeline for data aggregation.
     * As you add more relations in the future, add new steps here.
     */
    private fun enrich(documents: List<Document>): List<Document> {
        if (documents.isEmpty()) return documents

        enrichWithUserProfiles(documents)
        // add more here, for example: enrichWithComments(documents)

        return documents
    }

    private fun enrichWithUserProfiles(documents: List<Document>) {
        // 1. Extract all unique emails from creator, co-creators, and mentors
        val allEmails = documents.flatMap {
            it.coCreatorEmails + it.mentorEmails + it.creatorEmail
        }.toSet()

        if (allEmails.isEmpty()) return

        // 2. Fetch all required users in one single database query
        val usersMap = userRepository.findByEmailIn(allEmails.toList())
            .associateBy { it.email }
            .mapValues { (_, user) ->
                UserDto(user.id!!, user.name, user.surname, user.email, user.isProfessor)
            }

        // 3. Map the data back to the nested profiles object
        documents.forEach { doc ->
            // In case the transient object is somehow null (e.g., from DB mapping), initialize it
            if (doc.profiles == null) {
                doc.profiles = DocumentProfiles()
            }

            doc.profiles.creatorProfile = usersMap[doc.creatorEmail]
            doc.profiles.coCreatorProfiles = doc.coCreatorEmails.mapNotNull { usersMap[it] }
            doc.profiles.mentorProfiles = doc.mentorEmails.mapNotNull { usersMap[it] }
        }
    }
}