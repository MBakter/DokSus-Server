package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.data.file.FileStorageService
import hr.alu.okiru.doksusserver.features.reference_data.Category
import hr.alu.okiru.doksusserver.features.reference_data.group.GroupService
import hr.alu.okiru.doksusserver.features.user.UserDto
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import kotlin.time.Clock

/**
 * Business logic for documents: ownership checks, publication dates, keeping the group list
 * in sync with published documents, and attaching user profiles before data is returned.
 */
@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val userRepository: UserRepository,
    private val groupService: GroupService,
    private val fileStorageService: FileStorageService
) {

    fun getDocumentById(id: String): Document? {
        val document = documentRepository.findById(id).orElse(null) ?: return null
        return enrich(listOf(document)).first()
    }

    fun getDocuments(
        category: String?,
        search: String?,
        group: String?,
        allowedVisibilities: List<Document.Visibility>,
        pageable: Pageable
    ): Page<Document> {
        val page = documentRepository.searchDocuments(category, search, group, allowedVisibilities, pageable)
        enrich(page.content)
        return page
    }

    fun getMyDocuments(email: String): List<Document> {
        val documents = documentRepository.findByCreatorEmail(email)
        return enrich(documents.sortedByDescending { it.creationDate })
    }

    fun getUserPublishedDocuments(email: String, allowedVisibilities: List<Document.Visibility>): List<Document> {
        val createdDocs = documentRepository.findByCreatorEmailAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)
        val coCreatedDocs = documentRepository.findByCoCreatorEmailsContainingAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)
        val mentoredDocs = documentRepository.findByMentorEmailsContainingAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)

        // Combine all three lists and ensure no duplicates by ID
        val allDocuments = (createdDocs + coCreatedDocs + mentoredDocs).distinctBy { it.id }
        return enrich(allDocuments.sortedByDescending { it.creationDate })
    }

    fun createDocument(document: Document): Document {
        if (document.isPublished) {
            requirePublishable(document)
        }
        val saved = documentRepository.save(document)

        // If created as published immediately, register the group
        if (saved.isPublished) {
            groupService.addGroupIfNotExists(saved.restorationData.group)
        }
        return enrich(listOf(saved)).first()
    }

    fun updateMetadata(id: String, request: DocumentMetadataUpdateRequest, principalEmail: String): Document {
        val existingDocument = documentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Dokument nije pronađen.")
        }

        if (existingDocument.creatorEmail != principalEmail) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pravo uređivati ovaj dokument.")
        }

        val wasPublished = existingDocument.isPublished
        val isNowPublished = request.isPublished
        val oldGroup = existingDocument.restorationData.group
        val newGroup = request.content.group

        val publicationDate = when {
            request.isPublished && existingDocument.publicationDate == null -> Clock.System.now()
            !request.isPublished -> null
            else -> existingDocument.publicationDate
        }

        if (request.isPublished) {
            requirePublishable(
                existingDocument.copy(
                    restorationData = request.content,
                    isPublished = true,
                    visibility = request.visibility,
                    coCreatorEmails = request.coCreatorEmails,
                    mentorEmails = request.mentorEmails,
                    publicationDate = publicationDate
                )
            )
        }

        // Only the metadata fields are written. Saving the whole document would overwrite files
        // that another request stored after this one was loaded.
        val update = Update()
            .set("restorationData", request.content)
            .set("isPublished", request.isPublished)
            .set("visibility", request.visibility)
            .set("coCreatorEmails", request.coCreatorEmails)
            .set("mentorEmails", request.mentorEmails)
        if (publicationDate != null) {
            update.set("publicationDate", publicationDate)
        } else {
            update.unset("publicationDate")
        }

        val saved = documentRepository.updateByCreator(id, principalEmail, update)

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
        val document = documentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Dokument nije pronađen.")
        }

        if (document.creatorEmail != principalEmail) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pravo uređivati ovaj dokument.")
        }

        val groupToCleanUp = document.restorationData.group
        val wasPublished = document.isPublished
        val filePaths = storedPaths(document.files)

        // Delete the document first so existsBy... evaluates correctly
        documentRepository.delete(document)
        filePaths.forEach { fileStorageService.deleteFile(it) }

        // If it was published, check if its group is now completely orphaned
        if (wasPublished) {
            groupService.removeGroupIfOrphaned(groupToCleanUp)
        }
    }


private fun storedPaths(files: DocumentFiles): List<String> = buildList {
        files.coverPath?.let { add(it) }
        files.pdfPath?.let { add(it) }
        files.video?.path?.let { add(it) }
        addAll(files.additionalPdfs.map { it.path })
        addAll(files.projectPhotos.map { it.path })
        addAll(files.models3d.map { it.path })
    }

    /**
     * Same rules as the editor: a published document needs its core fields, a mentor,
     * a cover image and the main PDF. Object-specific fields are skipped for research and thesis categories.
     */
    private fun requirePublishable(document: Document) {
        val data = document.restorationData
        val keywordCount = data.keywords.split(',').count { it.isNotBlank() }
        val missingCore = data.category == Category.UNSPECIFIED || data.name.isBlank() || keywordCount < 3
                || document.mentorEmails.none { it.isNotBlank() }
        val exempt = data.category == Category.ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI
                || data.category == Category.DIPLOMSKI_I_SEMINARSKI_RADOVI
        val missingObjectFields = !exempt && listOf(
            data.inventoryNumber, data.author, data.date, data.material, data.technique, data.storage
        ).any { it.isBlank() }
        val missingFiles = document.files.coverPath.isNullOrBlank() || document.files.pdfPath.isNullOrBlank()

        if (missingCore || missingObjectFields || missingFiles) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Nedostaju obavezni podaci ili datoteke za objavu.")
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