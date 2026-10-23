package hr.alu.okiru.doksusserver.data

import hr.alu.okiru.doksusserver.features.document.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

/**
 * Runs one-off database updates on startup. Use it when the shape of stored data changes
 * (e.g. a new required field) so that old records in MongoDB are brought up to date.
 */
@Service
class DatabaseMigrationHelper(
    private val mongoTemplate: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * This method runs automatically exactly once after the Spring Boot application fully starts.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun runMigrations() {
        log.info("Checking database migrations...")

        /**
         * Register all migrations here in order
         * If a migration ran and all documents are up to date the function can be deleted
         */

        //for example: addVisibilityToDocuments()
        //migrateToNewDocumentStructure()

        log.info("Database migrations check completed.")
    }

    /**
     * Example for migration. This adds 'visibility = OKIRU' to any Document missing the field.
     */
    private fun addVisibilityToDocuments() {
        val query = Query(Criteria.where("visibility").exists(false))

        val update = Update().set("visibility", Document.Visibility.OKIRU.name)

        val result = mongoTemplate.updateMulti(query, update, Document::class.java)

        if (result.modifiedCount > 0) {
            log.info("MIGRATION APPLIED: Updated ${result.modifiedCount} documents to include visibility=OKIRU")
        }
    }
}