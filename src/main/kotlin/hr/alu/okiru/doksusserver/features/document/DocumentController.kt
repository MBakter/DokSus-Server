package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.data.file.FileStorageService
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal
import kotlin.time.Clock

@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentService: DocumentService,
    private val documentRepository: DocumentRepository,
    private val userRepository: UserRepository,
    private val fileStorageService: FileStorageService,
) {

    @GetMapping
    fun getDocuments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) group: String?, // NEW: General metadata filtering
        principal: Principal?
    ): Page<Document> {
        val allowedVisibilities = if (principal != null) {
            listOf(Document.Visibility.PUBLIC, Document.Visibility.OKIRU)
        } else {
            listOf(Document.Visibility.PUBLIC)
        }

        // You will need to update your DocumentService and custom repository method
        // to accept and filter by this `group` parameter.
        return documentService.getDocuments(category?.uppercase(), search, group, allowedVisibilities, PageRequest.of(page, size))
    }

    @GetMapping("/{id}")
    fun getDocumentById(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Document> {
        val document = documentService.getDocumentById(id)
            ?: return ResponseEntity.notFound().build()

        if (!document.isPublished) {
            if (principal == null || document.creatorEmail != principal.name) {
                return ResponseEntity.status(403).build()
            }
        } else if (document.visibility == Document.Visibility.OKIRU && principal == null) {
            return ResponseEntity.status(403).build()
        }

        return ResponseEntity.ok(document)
    }

    @GetMapping("/me")
    fun getMyDocuments(principal: Principal): ResponseEntity<List<Document>> {
        return ResponseEntity.ok(documentService.getMyDocuments(principal.name))
    }

    @GetMapping("/user")
    fun getUserPublishedDocuments(
        @RequestParam email: String,
        principal: Principal?
    ): ResponseEntity<List<Document>> {
        val allowedVisibilities = if (principal != null) {
            listOf(Document.Visibility.PUBLIC, Document.Visibility.OKIRU)
        } else {
            listOf(Document.Visibility.PUBLIC)
        }
        return ResponseEntity.ok(documentService.getUserPublishedDocuments(email, allowedVisibilities))
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun createDocument(
        @RequestBody request: DocumentMetadataUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Document> {
        val creatorEmail = authentication.name

        val newDocument = Document(
            restorationData = request.content,
            isPublished = request.isPublished,
            visibility = request.visibility,
            creatorEmail = creatorEmail,
            coCreatorEmails = request.coCreatorEmails,
            mentorEmails = request.mentorEmails,
            publicationDate = if (request.isPublished) Clock.System.now() else null
        )

        val savedDocument = documentService.createDocument(newDocument)
        return ResponseEntity.ok(savedDocument)
    }

    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateMetadata(
        @PathVariable id: String,
        @RequestBody request: DocumentMetadataUpdateRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val updatedDocument = documentService.updateMetadata(id, request, principal.name)
        return ResponseEntity.ok(updatedDocument)
    }

    @DeleteMapping("/{id}")
    fun deleteDocument(
        @PathVariable id: String,
        principal: Principal
    ): ResponseEntity<Void> {
        documentService.deleteDocument(id, principal.name)
        return ResponseEntity.noContent().build()
    }

    /**
     * Update the creator of a document. Only for professors
     */
    @PutMapping("/{id}/creator", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateDocumentCreator(
        @PathVariable id: String,
        @RequestBody request: CreatorUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> {
        try {
            val currentUserEmail = authentication.name
            val currentUser = userRepository.findByEmail(currentUserEmail)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

            if (!currentUser.isProfessor) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Samo profesori mogu mijenjati vlasnika dokumenta.")
            }

            val document = documentRepository.findById(id).orElse(null)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Dokument nije pronađen.")

            val newCreatorEmail = request.email.trim()
            if (!userRepository.existsByEmail(newCreatorEmail)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Korisnik s tom e-mail adresom ne postoji.")
            }

            // Prevent redundant saves if they enter the exact same email
            if (document.creatorEmail == newCreatorEmail) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ovaj korisnik je već vlasnik dokumenta.")
            }

            val oldCreatorEmail = document.creatorEmail

            // 1. Assign the new creator
            document.creatorEmail = newCreatorEmail

            // 2. Rebuild the co-creator list
            // Add the old creator, remove the new creator (in case they were a co-creator), and remove duplicates
            val updatedCoCreators = (document.coCreatorEmails + oldCreatorEmail)
                .filter { it != newCreatorEmail }
                .distinct()

            document.coCreatorEmails = updatedCoCreators

            documentRepository.save(document)

            return ResponseEntity.ok().build()

        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.message ?: "Dogodila se interna greška.")
        }
    }

    // ----------------------------------------------------------------
    // FILE UPLOAD ENDPOINTS
    // ----------------------------------------------------------------

    @PostMapping("/{id}/cover", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadCover(
        @PathVariable id: String,
        @RequestPart("cover") cover: MultipartFile,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.coverPath = fileStorageService.saveFile(cover, principal.name)
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @DeleteMapping("/{id}/cover")
    fun deleteCover(@PathVariable id: String, principal: Principal): ResponseEntity<Void> {
        val document = getDocumentIfCreator(id, principal)
        document.files.coverPath = null
        documentRepository.save(document)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/pdf", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadPdf(
        @PathVariable id: String,
        @RequestPart("pdf") pdf: MultipartFile,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.pdfPath = fileStorageService.saveFile(pdf, principal.name)
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @DeleteMapping("/{id}/pdf")
    fun deletePdf(@PathVariable id: String, principal: Principal): ResponseEntity<Void> {
        val document = getDocumentIfCreator(id, principal)
        document.files.pdfPath = null
        documentRepository.save(document)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/additional-pdfs", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addAdditionalPdfs(
        @PathVariable id: String,
        @RequestPart("pdfs") pdfs: List<MultipartFile>,
        @RequestParam("pdfNames") pdfNames: List<String>,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val newPdfs = processMultipleFiles(pdfs, pdfNames, principal.name, "additional-pdfs")
        document.files.additionalPdfs += newPdfs
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PutMapping("/{id}/additional-pdfs", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncAdditionalPdfs(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.additionalPdfs = request.existingFiles
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PostMapping("/{id}/video", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadVideo(
        @PathVariable id: String,
        @RequestPart("video") videoFile: MultipartFile,
        @RequestParam("videoName", required = false) videoName: String?,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.video = NamedFile(
            name = videoName ?: videoFile.originalFilename ?: "Video",
            path = fileStorageService.saveFile(videoFile, principal.name)
        )
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PutMapping("/{id}/video", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncVideo(
        @PathVariable id: String,
        @RequestBody request: SyncFileRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.video = request.existingFile
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PostMapping("/{id}/photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addPhotos(
        @PathVariable id: String,
        @RequestPart("projectPhotos") projectPhotos: List<MultipartFile>,
        @RequestParam("projectPhotoNames") projectPhotoNames: List<String>,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val newPhotos = processMultipleFiles(projectPhotos, projectPhotoNames, principal.name, "project-photos")
        document.files.projectPhotos += newPhotos
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PutMapping("/{id}/photos", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncPhotos(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.projectPhotos = request.existingFiles
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PostMapping("/{id}/models", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addModels(
        @PathVariable id: String,
        @RequestPart("models3d") models3d: List<MultipartFile>,
        @RequestParam("models3dNames") models3dNames: List<String>,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val newModels = processMultipleFiles(models3d, models3dNames, principal.name, "3d-models")
        document.files.models3d += newModels
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PutMapping("/{id}/models", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncModels(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        document.files.models3d = request.existingFiles
        return ResponseEntity.ok(documentRepository.save(document))
    }

    private fun getDocumentIfCreator(id: String, principal: Principal): Document {
        val document = documentRepository.findById(id).orElseThrow { RuntimeException("Not found") }
        if (document.creatorEmail != principal.name) throw RuntimeException("Access denied")
        return document
    }

    private fun processMultipleFiles(
        files: List<MultipartFile>?,
        names: List<String>?,
        userEmail: String,
        subfolder: String
    ): List<NamedFile> {
        if (files == null || names == null || files.size != names.size) return emptyList()

        return files.zip(names).map { (file, name) ->
            val path = fileStorageService.saveFile(file, userEmail, subfolder)
            NamedFile(name = name, path = path)
        }
    }
}