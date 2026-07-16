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

    // Retrieves both private and public documents, but ONLY for the specified owner
    fun findByOwnerEmail(ownerEmail: String): List<Document>
}


//// Custom Repository for more complex queries

class DocumentRepositoryCustomImpl(
private val mongoTemplate: MongoTemplate
) : DocumentRepositoryCustom {

    override fun searchDocuments(category: String?, keyword: String?, pageable: Pageable): Page<Document> {
        val query = Query().with(pageable)

        query.addCriteria(Criteria.where("isPublished").`is`(true))

        if (!category.isNullOrBlank()) {
            query.addCriteria(Criteria.where("content.category").`is`(category))
        }

        if (!keyword.isNullOrBlank()) {
            query.addCriteria(Criteria().orOperator(
                Criteria.where("content.name").regex(keyword, "i"),
                Criteria.where("content.author").regex(keyword, "i"),
                Criteria.where("content.student").regex(keyword, "i"),
                Criteria.where("content.professor").regex(keyword, "i"),
                Criteria.where("content.technique").regex(keyword, "i"),
                Criteria.where("content.materialsUsed").regex(keyword, "i"),
                Criteria.where("content.keywords").regex(keyword, "i"),
                Criteria.where("content.storage").regex(keyword, "i"),
                Criteria.where("content.location").regex(keyword, "i"),
                Criteria.where("content.invNumber").regex(keyword, "i")
            ))
        }

        val countQuery = Query.of(query).limit(-1).skip(-1)
        val count = mongoTemplate.count(countQuery, Document::class.java)
        val documents = mongoTemplate.find(query, Document::class.java)

        return PageImpl(documents, pageable, count)
    }
}

interface DocumentRepositoryCustom {
    fun searchDocuments(category: String?, keyword: String?, pageable: Pageable): Page<Document>
}