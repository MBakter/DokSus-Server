package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.features.user.UserDto
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    fun getDocuments(category: String?, search: String?, allowedVisibilities: List<Document.Visibility>, pageable: Pageable): Page<Document> {
        val page = documentRepository.searchDocuments(category, search, allowedVisibilities, pageable)
        enrich(page.content)
        return page
    }

    fun getMyDocuments(email: String): List<Document> {
        val documents = documentRepository.findByOwnerEmail(email)
        return enrich(documents)
    }

    fun getUserPublishedDocuments(email: String, allowedVisibilities: List<Document.Visibility>): List<Document> {
        val documents = documentRepository.findByOwnerEmailAndIsPublishedTrueAndVisibilityIn(email, allowedVisibilities)
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
        // 1. Extract all unique emails
        val allEmails = documents.flatMap { it.coAuthorEmails + it.ownerEmail }.toSet()
        if (allEmails.isEmpty()) return

        // 2. Fetch all required users in one single database query
        val usersMap = userRepository.findByEmailIn(allEmails.toList())
            .associateBy { it.email }
            .mapValues { (_, user) ->
                UserDto(user.id!!, user.name, user.surname, user.email)
            }

        // 3. Map the data back to the documents
        documents.forEach { doc ->
            doc.ownerProfile = usersMap[doc.ownerEmail]
            doc.authorProfiles = doc.coAuthorEmails.mapNotNull { usersMap[it] }
        }
    }
}