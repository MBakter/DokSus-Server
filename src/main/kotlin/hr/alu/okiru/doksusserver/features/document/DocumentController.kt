package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.data.file.FileStorageService
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import kotlin.time.Clock

/**
 * REST endpoints under `/api/documents`: listing and searching documents, reading one document,
 * creating/updating/deleting, changing the owner, and uploading or syncing attached files.
 * Visibility rules (public vs. OKIRU-only vs. draft) are enforced here based on the logged-in user.
 */
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
        @RequestParam(required = false) group: String?,
        principal: Principal?
    ): Page<Document> {
        val allowedVisibilities = if (principal != null) {
            listOf(Document.Visibility.PUBLIC, Document.Visibility.OKIRU)
        } else {
            listOf(Document.Visibility.PUBLIC)
        }

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
        val previous = document.files.coverPath
        val saved = documentRepository.updateByCreator(
            id,
            principal.name,
            Update().set("files.coverPath", fileStorageService.saveFile(cover, principal.name))
        )
        fileStorageService.deleteFile(previous)
        return ResponseEntity.ok(saved)
    }

    @DeleteMapping("/{id}/cover")
    fun deleteCover(@PathVariable id: String, principal: Principal): ResponseEntity<Void> {
        val document = getDocumentIfCreator(id, principal)
        val previous = document.files.coverPath
        documentRepository.updateByCreator(id, principal.name, Update().unset("files.coverPath"))
        fileStorageService.deleteFile(previous)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/pdf", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadPdf(
        @PathVariable id: String,
        @RequestPart("pdf") pdf: MultipartFile,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val previous = document.files.pdfPath
        val saved = documentRepository.updateByCreator(
            id,
            principal.name,
            Update().set("files.pdfPath", fileStorageService.saveFile(pdf, principal.name))
        )
        fileStorageService.deleteFile(previous)
        return ResponseEntity.ok(saved)
    }

    @DeleteMapping("/{id}/pdf")
    fun deletePdf(@PathVariable id: String, principal: Principal): ResponseEntity<Void> {
        val document = getDocumentIfCreator(id, principal)
        val previous = document.files.pdfPath
        documentRepository.updateByCreator(id, principal.name, Update().unset("files.pdfPath"))
        fileStorageService.deleteFile(previous)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/additional-pdfs", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addAdditionalPdfs(
        @PathVariable id: String,
        @RequestPart("pdfs") pdf: MultipartFile,
        // One string, not a list: Spring splits list parameters on commas, so a name like
        // "Izvještaj, final" used to be stored as two names and the file was dropped.
        @RequestParam("pdfNames") pdfName: String,
        principal: Principal
    ): ResponseEntity<Document> {
        getDocumentIfCreator(id, principal)
        val stored = NamedFile(
            name = pdfName,
            path = fileStorageService.saveFile(pdf, principal.name, "additional-pdfs")
        )
        val saved = documentRepository.updateByCreator(
            id,
            principal.name,
            Update().push("files.additionalPdfs", stored)
        )
        return ResponseEntity.ok(saved)
    }

    @PutMapping("/{id}/additional-pdfs", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncAdditionalPdfs(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val (saved, removed) = documentRepository.syncNamedFiles(
            id,
            principal.name,
            "files.additionalPdfs",
            document.files.additionalPdfs,
            request.existingFiles
        )
        removed.forEach { fileStorageService.deleteFile(it) }
        return ResponseEntity.ok(saved)
    }

    @PostMapping("/{id}/video", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadVideo(
        @PathVariable id: String,
        @RequestPart("video") videoFile: MultipartFile,
        @RequestParam("videoName", required = false) videoName: String?,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val previous = document.files.video?.path
        val stored = NamedFile(
            name = videoName ?: videoFile.originalFilename ?: "Video",
            path = fileStorageService.saveFile(videoFile, principal.name)
        )
        val saved = documentRepository.updateByCreator(id, principal.name, Update().set("files.video", stored))
        fileStorageService.deleteFile(previous)
        return ResponseEntity.ok(saved)
    }

    @PutMapping("/{id}/video", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncVideo(
        @PathVariable id: String,
        @RequestBody request: SyncFileRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val previous = document.files.video
        val kept = request.existingFile?.let { requested ->
            if (previous?.path == requested.path) previous.copy(name = requested.name) else null
        }
        val update = if (kept == null) Update().unset("files.video") else Update().set("files.video", kept)
        val saved = documentRepository.updateByCreator(id, principal.name, update)
        if (previous != null && previous.path != kept?.path) {
            fileStorageService.deleteFile(previous.path)
        }
        return ResponseEntity.ok(saved)
    }

    @PostMapping("/{id}/photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addPhotos(
        @PathVariable id: String,
        @RequestPart("projectPhotos") projectPhoto: MultipartFile,
        @RequestParam("projectPhotoNames") projectPhotoName: String, // scalar: a list would split the name on commas
        principal: Principal
    ): ResponseEntity<Document> {
        getDocumentIfCreator(id, principal)
        val stored = NamedFile(
            name = projectPhotoName,
            path = fileStorageService.saveFile(projectPhoto, principal.name, "project-photos")
        )
        val saved = documentRepository.updateByCreator(
            id,
            principal.name,
            Update().push("files.projectPhotos", stored)
        )
        return ResponseEntity.ok(saved)
    }

    @PutMapping("/{id}/photos", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncPhotos(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val (saved, removed) = documentRepository.syncNamedFiles(
            id,
            principal.name,
            "files.projectPhotos",
            document.files.projectPhotos,
            request.existingFiles
        )
        removed.forEach { fileStorageService.deleteFile(it) }
        return ResponseEntity.ok(saved)
    }

    @PostMapping("/{id}/models", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addModels(
        @PathVariable id: String,
        @RequestPart("models3d") model: MultipartFile,
        @RequestParam("models3dNames") modelName: String, // scalar: a list would split the name on commas
        principal: Principal
    ): ResponseEntity<Document> {
        getDocumentIfCreator(id, principal)
        val stored = NamedFile(
            name = modelName,
            path = fileStorageService.saveFile(model, principal.name, "3d-models")
        )
        val saved = documentRepository.updateByCreator(
            id,
            principal.name,
            Update().push("files.models3d", stored)
        )
        return ResponseEntity.ok(saved)
    }

    @PutMapping("/{id}/models", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncModels(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfCreator(id, principal)
        val (saved, removed) = documentRepository.syncNamedFiles(
            id,
            principal.name,
            "files.models3d",
            document.files.models3d,
            request.existingFiles
        )
        removed.forEach { fileStorageService.deleteFile(it) }
        return ResponseEntity.ok(saved)
    }

    private fun getDocumentIfCreator(id: String, principal: Principal): Document {
        val document = documentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Dokument nije pronađen.")
        }
        if (document.creatorEmail != principal.name) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pravo uređivati ovaj dokument.")
        }
        return document
    }

    }