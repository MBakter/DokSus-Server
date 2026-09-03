package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.features.user.UserDto
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val userRepository: UserRepository
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
        val documents = documentRepository.findByCreatorEmail(email) // Updated repository method
        return enrich(documents)
    }

    fun getUserPublishedDocuments(email: String, allowedVisibilities: List<Document.Visibility>): List<Document> {
        val documents = documentRepository.findByCreatorEmailAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities) // Updated repository method
        return enrich(documents)
    }

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