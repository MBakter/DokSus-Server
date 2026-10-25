package hr.alu.okiru.doksusserver.features.document

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import org.bson.Document as BsonDocument

/**
 * Database access for the `documents` collection. Spring Data generates the queries
 * automatically from the method names; complex searches live in [DocumentRepositoryCustomImpl].
 */
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

/** Handwritten queries that cannot be expressed by Spring Data method names. */
interface DocumentRepositoryCustom {
    fun searchDocuments(
        category: String?,
        keyword: String?,
        group: String?,
        visibilities: List<Document.Visibility>,
        pageable: Pageable
    ): Page<Document>

    fun findByFilePath(path: String): Document?

    /**
     * Updates one document without replacing it. A full save would write back every field from
     * the copy loaded at the start of the request, and a slower request could erase files stored
     * by a request that finished in between.
     */
    fun updateByCreator(id: String, creatorEmail: String, update: Update): Document

    /** Renames and removes entries in one file list. Newly pushed files are left in place. */
    fun syncNamedFiles(
        id: String,
        creatorEmail: String,
        field: String,
        current: List<NamedFile>,
        requested: List<NamedFile>
    ): Pair<Document, List<String>>
}

/**
 * Implements the home page search: filters published documents by visibility, category, group
 * and a case-insensitive keyword across several text fields, with pagination.
 */
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

        // Newest documents first. epochSeconds covers the stored Kotlin Instant; creationDate
        // covers a date value. Whichever form is present decides the order.
        query.with(
            Sort.by(Sort.Order.desc("creationDate.epochSeconds"))
                .and(Sort.by(Sort.Order.desc("creationDate")))
        )

        // Count total matching records for pagination metadata
        val countQuery = Query.of(query).limit(-1).skip(-1)
        val count = mongoTemplate.count(countQuery, Document::class.java)

        val documents = mongoTemplate.find(query, Document::class.java)
        return PageImpl(documents, pageable, count)
    }

    override fun findByFilePath(path: String): Document? {
        val query = Query(
            Criteria().orOperator(
                Criteria.where("files.coverPath").`is`(path),
                Criteria.where("files.pdfPath").`is`(path),
                Criteria.where("files.video.path").`is`(path),
                Criteria.where("files.additionalPdfs.path").`is`(path),
                Criteria.where("files.projectPhotos.path").`is`(path),
                Criteria.where("files.models3d.path").`is`(path)
            )
        )
        return mongoTemplate.findOne(query, Document::class.java)
    }

    override fun updateByCreator(id: String, creatorEmail: String, update: Update): Document {
        val query = Query(Criteria.where("_id").`is`(id).and("creatorEmail").`is`(creatorEmail))
        val options = FindAndModifyOptions.options().returnNew(true)
        return mongoTemplate.findAndModify(query, update, options, Document::class.java)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Dokument je u međuvremenu promijenjen.")
    }

    override fun syncNamedFiles(
        id: String,
        creatorEmail: String,
        field: String,
        current: List<NamedFile>,
        requested: List<NamedFile>
    ): Pair<Document, List<String>> {
        require(field == "files.additionalPdfs" || field == "files.projectPhotos" || field == "files.models3d") {
            "Nepoznato polje datoteka."
        }

        val currentByPath = current.associateBy { it.path }
        val kept = requested.mapNotNull { item ->
            currentByPath[item.path]?.let { item }
        }
        val keptPaths = kept.map { it.path }.toSet()
        val removed = current.filter { it.path !in keptPaths }.map { it.path }

        var updated: Document? = null
        for (path in removed) {
            updated = updateByCreator(id, creatorEmail, Update().pull(field, BsonDocument("path", path)))
        }
        for (item in kept) {
            val previous = currentByPath[item.path] ?: continue
            if (previous.name == item.name) continue
            val update = Update()
                .set("$field.\$[file].name", item.name)
                .filterArray(Criteria.where("file.path").`is`(item.path))
            updated = updateByCreator(id, creatorEmail, update)
        }

        val document = updated ?: mongoTemplate.findById(id, Document::class.java)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dokument nije pronađen.")
        return document to removed
    }
}