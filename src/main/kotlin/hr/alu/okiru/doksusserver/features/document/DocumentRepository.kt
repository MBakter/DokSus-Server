package hr.alu.okiru.doksusserver.features.document

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface DocumentRepository : MongoRepository<Document, String>, DocumentRepositoryCustom {

    // Retrieves both private and public documents, but ONLY for the specified creator
    fun findByCreatorEmail(creatorEmail: String): List<Document>

    // Finds published documents for a specific user, filtered by allowed visibilities
    fun findByCreatorEmailAndIsPublishedTrueAndVisibilityIn(
        creatorEmail: String,
        visibilities: List<Document.Visibility>
    ): List<Document>

    fun findByCoCreatorEmailsContainingAndIsPublishedTrueAndVisibilityIn(
        email: String,
        visibilities: List<Document.Visibility>
    ): List<Document>

    fun findByMentorEmailsContainingAndIsPublishedTrueAndVisibilityIn(
        email: String,
        visibilities: List<Document.Visibility>
    ): List<Document>

    fun existsByRestorationDataGroupAndIsPublishedTrue(group: String): Boolean
}

// Custom Repository for more complex queries
interface DocumentRepositoryCustom {
    fun searchDocuments(
        category: String?,
        keyword: String?,
        group: String?,
        visibilities: List<Document.Visibility>,
        pageable: Pageable
    ): Page<Document>
}

class DocumentRepositoryCustomImpl(
    private val mongoTemplate: MongoTemplate
) : DocumentRepositoryCustom {

    override fun searchDocuments(
        category: String?,
        keyword: String?,
        group: String?,
        visibilities: List<Document.Visibility>,
        pageable: Pageable
    ): Page<Document> {
        val query = Query().with(pageable)

        // Must be published AND match the allowed visibilities based on authentication context
        query.addCriteria(Criteria.where("isPublished").`is`(true))
        query.addCriteria(Criteria.where("visibility").`in`(visibilities))

        // Category filter using the new String enum representation
        if (!category.isNullOrBlank() && category != "UNSPECIFIED") {
            query.addCriteria(Criteria.where("restorationData.category").`is`(category.uppercase()))
        }

        // Group filter (Exact match)
        if (!group.isNullOrBlank()) {
            query.addCriteria(Criteria.where("restorationData.group").`is`(group))
        }

        if (!keyword.isNullOrBlank()) {
            query.addCriteria(
                Criteria().orOperator(
                    Criteria.where("restorationData.name").regex(keyword, "i"),
                    Criteria.where("restorationData.author").regex(keyword, "i"),
                    Criteria.where("restorationData.technique").regex(keyword, "i"),
                    Criteria.where("restorationData.keywords").regex(keyword, "i"),
                    Criteria.where("restorationData.storage").regex(keyword, "i"),
                    Criteria.where("restorationData.location").regex(keyword, "i"),
                    Criteria.where("restorationData.inventoryNumber").regex(keyword, "i"),
                    Criteria.where("restorationData.works.name").regex(keyword, "i"),
                    Criteria.where("restorationData.works.material").regex(keyword, "i")
                )
            )
        }

        // Count total matching records for pagination metadata
        val countQuery = Query.of(query).limit(-1).skip(-1)
        val count = mongoTemplate.count(countQuery, Document::class.java)

        // Execute paginated query
        val documents = mongoTemplate.find(query, Document::class.java)
        documents.sortByDescending { it.creationDate }

        return PageImpl(documents, pageable, count)
    }
}