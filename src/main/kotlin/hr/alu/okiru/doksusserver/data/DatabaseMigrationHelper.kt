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
        migrateToNewDocumentStructure()

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

    private fun migrateToNewDocumentStructure() {
        val collection = mongoTemplate.getCollection("documents")
        val rawDocs = collection.find()
        var migratedCount = 0

        for (doc in rawDocs) {
            val update = org.bson.Document()
            val set = org.bson.Document()
            val unset = org.bson.Document()

            // 1. Migrate Root Metadata Fields
            if (doc.containsKey("ownerEmail")) {
                set["creatorEmail"] = doc.getString("ownerEmail")
                unset["ownerEmail"] = ""
            }
            if (doc.containsKey("coAuthorEmails")) {
                set["coCreatorEmails"] = doc["coAuthorEmails"]
                unset["coAuthorEmails"] = ""
            }
            if (!doc.containsKey("mentorEmails")) {
                set["mentorEmails"] = emptyList<String>()
            }

            // 2. Migrate Files
            val filesDoc = org.bson.Document()
            listOf("coverPath", "pdfPath", "projectPhotos", "models3d", "video").forEach { field ->
                if (doc.containsKey(field)) {
                    filesDoc[field] = doc[field]
                    unset[field] = ""
                }
            }
            if (!filesDoc.isEmpty()) {
                set["files"] = filesDoc
            } else if (!doc.containsKey("files")) {
                set["files"] = org.bson.Document()
            }

            // 3. Migrate Content -> RestorationData
            // Check if old 'content' exists, OR if we already renamed it to 'restorationData'
            val baseData = doc.get("content", org.bson.Document::class.java)
                ?: doc.get("restorationData", org.bson.Document::class.java)

            if (baseData != null) {
                val newData = org.bson.Document(baseData)

                // Fix Category Enum Mapping (Handles 1, "1", "SLIKE NA PLATNU", etc.)
                val rawCategory = newData["category"]
                val catStr = rawCategory?.toString()?.trim()?.uppercase() ?: ""

                newData["category"] = when (catStr) {
                    "1", "DRVENI PREDMETI", "DRVENI_PREDMETI" -> "DRVENI_PREDMETI"
                    "2", "SLIKE NA PLATNU", "SLIKE_NA_PLATNU" -> "SLIKE_NA_PLATNU"
                    "3", "ZIDNE SLIKE", "ZIDNE_SLIKE" -> "ZIDNE_SLIKE"
                    "4", "KAMENA I ARHITEKTONSKA PLASTIKA", "KAMENA_I_ARHITEKTONSKA_PLASTIKA" -> "KAMENA_I_ARHITEKTONSKA_PLASTIKA"
                    "5", "OSTALI MATERIJALI", "OSTALI_MATERIJALI" -> "OSTALI_MATERIJALI"
                    "6", "ISTRAZIVACKI RADOVI I REFERENTNI MATERIJALI", "ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI" -> "ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI"
                    "7", "DIPLOMSKI I SEMINARSKI RADOVI", "DIPLOMSKI_I_SEMINARSKI_RADOVI" -> "DIPLOMSKI_I_SEMINARSKI_RADOVI"
                    else -> "UNSPECIFIED" // Fallback for "0", null, or unrecognized test data
                }

                // Rename invNumber -> inventoryNumber
                if (newData.containsKey("invNumber")) {
                    newData["inventoryNumber"] = newData.getString("invNumber")
                    newData.remove("invNumber")
                }

                // Reset Analysis array safely to empty
                newData["typeOfAnalysis"] = emptyList<Any>()
                newData.remove("goalOfAnalysis")

                // Migrate Works safely (Idempotent: handles both String and ArrayList)
                val worksObj = newData["works"]

                if (worksObj is String) {
                    // It's the old format, convert it
                    val oldMaterialsUsed = newData.getString("materialsUsed") ?: ""
                    if (worksObj.isNotBlank() || oldMaterialsUsed.isNotBlank()) {
                        val workItem = org.bson.Document("name", worksObj).append("material", oldMaterialsUsed)
                        newData["works"] = listOf(workItem)
                    } else {
                        newData["works"] = emptyList<Any>()
                    }
                } else if (worksObj == null) {
                    // It doesn't exist at all
                    newData["works"] = emptyList<Any>()
                }
                // If worksObj is already a List (from the previous migration), we do nothing and leave it intact!

                newData.remove("materialsUsed")

                // Clean up obsolete properties
                newData.remove("student")
                newData.remove("professor")

                // Assign to new root field name 'restorationData'
                set["restorationData"] = newData

                // Delete old 'content' field if it exists
                if (doc.containsKey("content")) {
                    unset["content"] = ""
                }
            }

            // Execute DB updates
            if (!set.isEmpty()) update["\$set"] = set
            if (!unset.isEmpty()) update["\$unset"] = unset

            if (!update.isEmpty()) {
                collection.updateOne(org.bson.Document("_id", doc["_id"]), update)
                migratedCount++
            }
        }

        if (migratedCount > 0) {
            log.info("Successfully migrated $migratedCount documents to the new schema structure.")
        }
    }
}