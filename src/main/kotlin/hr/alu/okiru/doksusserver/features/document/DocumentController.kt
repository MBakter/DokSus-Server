package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.data.file.FileStorageService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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
    private val fileStorageService: FileStorageService,
) {

    @GetMapping
    fun getDocuments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestHeader(value = "Category", required = false) category: String?,
        principal: Principal?
    ): Page<Document> {
        val allowedVisibilities = if (principal != null) {
            listOf(Document.Visibility.PUBLIC, Document.Visibility.OKIRU)
        } else {
            listOf(Document.Visibility.PUBLIC)
        }

        return documentService.getDocuments(category, search, allowedVisibilities, PageRequest.of(page, size))
    }

    @GetMapping("/{id}")
    fun getDocumentById(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Document> {
        val document = documentService.getDocumentById(id)
            ?: return ResponseEntity.notFound().build()

        // Security Enforcement
        if (!document.isPublished) {
            if (principal == null || document.ownerEmail != principal.name) {
                return ResponseEntity.status(403).build()
            }
        } else if (document.visibility == Document.Visibility.OKIRU && principal == null) {
            return ResponseEntity.status(403).build()
        }

        return ResponseEntity.ok(document)
    }

    @GetMapping("/me")
    fun getMyDocuments(principal: Principal): ResponseEntity<List<Document>> {
        val userDocuments = documentService.getMyDocuments(principal.name)
        return ResponseEntity.ok(userDocuments)
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

        val docs = documentService.getUserPublishedDocuments(email, allowedVisibilities)
        return ResponseEntity.ok(docs)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createDocument(
        @RequestPart("document") documentContent: DocumentContent,
        @RequestParam("isPublished") isPublished: Boolean,
        @RequestParam("visibility", defaultValue = "OKIRU") visibility: Document.Visibility,
        @RequestParam("coAuthorEmails", required = false) coAuthorEmails: List<String>?,
        @RequestPart("cover", required = false) cover: MultipartFile?,
        @RequestPart("pdf", required = false) pdf: MultipartFile?,
        @RequestPart("video", required = false) videoFile: MultipartFile?,
        @RequestParam("videoName", required = false) videoName: String?,
        @RequestPart("projectPhotos", required = false) projectPhotos: List<MultipartFile>?,
        @RequestParam("projectPhotoNames", required = false) projectPhotoNames: List<String>?,
        @RequestPart("models3d", required = false) models3d: List<MultipartFile>?,
        @RequestParam("models3dNames", required = false) models3dNames: List<String>?,
        authentication: Authentication
    ): ResponseEntity<Document> {
        val ownerEmail = authentication.name

        val coverPath = cover?.let { fileStorageService.saveFile(it, ownerEmail) }
        val pdfPath = pdf?.let { fileStorageService.saveFile(it, ownerEmail) }
        val processedVideo = videoFile?.let {
            NamedFile(
                name = videoName ?: it.originalFilename ?: "Video",
                path = fileStorageService.saveFile(it, ownerEmail)
            )
        }

        val processedPhotos = processMultipleFiles(projectPhotos, projectPhotoNames, ownerEmail, "project-photos")
        val processedModels = processMultipleFiles(models3d, models3dNames, ownerEmail, "3d-models")

        val newDocument = Document(
            content = documentContent,
            isPublished = isPublished,
            visibility = visibility,
            ownerEmail = ownerEmail,
            coAuthorEmails = coAuthorEmails ?: emptyList(),
            publicationDate = if (isPublished) Clock.System.now() else null,
            coverPath = coverPath,
            pdfPath = pdfPath,
            video = processedVideo,
            projectPhotos = processedPhotos,
            models3d = processedModels
        )

        val savedDocument = documentRepository.save(newDocument)

        return savedDocument.id?.let { id ->
            ResponseEntity.ok(documentService.getDocumentById(id))
        } ?: ResponseEntity.ok(savedDocument)
    }

    @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateMetadata(
        @PathVariable id: String,
        @RequestBody request: DocumentMetadataUpdateRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)

        document.content = request.content
        document.isPublished = request.isPublished
        document.visibility = request.visibility
        document.coAuthorEmails = request.coAuthorEmails

        if (request.isPublished && document.publicationDate == null) {
            document.publicationDate = Clock.System.now()
        } else if (!request.isPublished) {
            document.publicationDate = null
        }

        val saved = documentRepository.save(document)
        return ResponseEntity.ok(documentService.getDocumentById(saved.id!!))
    }

    @PostMapping("/{id}/cover", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadCover(
        @PathVariable id: String,
        @RequestPart("cover") cover: MultipartFile,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        document.coverPath = fileStorageService.saveFile(cover, principal.name)
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @DeleteMapping("/{id}/cover")
    fun deleteCover(@PathVariable id: String, principal: Principal): ResponseEntity<Void> {
        val document = getDocumentIfOwner(id, principal)
        document.coverPath = null
        documentRepository.save(document)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/pdf", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadPdf(
        @PathVariable id: String,
        @RequestPart("pdf") pdf: MultipartFile,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        document.pdfPath = fileStorageService.saveFile(pdf, principal.name)
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @DeleteMapping("/{id}/pdf")
    fun deletePdf(@PathVariable id: String, principal: Principal): ResponseEntity<Void> {
        val document = getDocumentIfOwner(id, principal)
        document.pdfPath = null
        documentRepository.save(document)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/video", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadVideo(
        @PathVariable id: String,
        @RequestPart("video") videoFile: MultipartFile,
        @RequestParam("videoName", required = false) videoName: String?,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        document.video = NamedFile(
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
        val document = getDocumentIfOwner(id, principal)
        document.video = request.existingFile
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PostMapping("/{id}/photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addPhotos(
        @PathVariable id: String,
        @RequestPart("projectPhotos") projectPhotos: List<MultipartFile>,
        @RequestParam("projectPhotoNames") projectPhotoNames: List<String>,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        val newPhotos = processMultipleFiles(projectPhotos, projectPhotoNames, principal.name, "project-photos")
        document.projectPhotos += newPhotos
        return ResponseEntity.ok(documentRepository.save(document))
    }

    /**
     * Overwrites the list of photos with the renamed/reordered/deleted list from request
     */
    @PutMapping("/{id}/photos", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncPhotos(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        document.projectPhotos = request.existingFiles
        return ResponseEntity.ok(documentRepository.save(document))
    }

    @PostMapping("/{id}/models", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addModels(
        @PathVariable id: String,
        @RequestPart("models3d") models3d: List<MultipartFile>,
        @RequestParam("models3dNames") models3dNames: List<String>,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        val newModels = processMultipleFiles(models3d, models3dNames, principal.name, "3d-models")
        document.models3d += newModels
        return ResponseEntity.ok(documentRepository.save(document))
    }

    /**
     * Overwrites the list of models with the renamed/reordered/deleted list from request
     */
    @PutMapping("/{id}/models", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun syncModels(
        @PathVariable id: String,
        @RequestBody request: SyncFilesRequest,
        principal: Principal
    ): ResponseEntity<Document> {
        val document = getDocumentIfOwner(id, principal)
        document.models3d = request.existingFiles
        return ResponseEntity.ok(documentRepository.save(document))
    }

    private fun getDocumentIfOwner(id: String, principal: Principal): Document {
        val document = documentRepository.findById(id).orElseThrow { RuntimeException("Not found") }
        if (document.ownerEmail != principal.name) throw RuntimeException("Access denied")
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