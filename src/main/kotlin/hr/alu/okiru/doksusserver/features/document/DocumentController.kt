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
        @RequestParam(required = false) category: String?,
        principal: Principal?
    ): Page<Document> {
        val allowedVisibilities = if (principal != null) {
            listOf(Document.Visibility.PUBLIC, Document.Visibility.OKIRU)
        } else {
            listOf(Document.Visibility.PUBLIC)
        }

        return documentService.getDocuments(category?.uppercase(), search, allowedVisibilities, PageRequest.of(page, size))
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

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createDocument(
        @RequestPart("document") restorationData: RestorationData,
        @RequestParam("isPublished") isPublished: Boolean,
        @RequestParam("visibility", defaultValue = "OKIRU") visibility: Document.Visibility,
        @RequestParam("coCreatorEmails", required = false) coCreatorEmails: List<String>?,
        @RequestParam("mentorEmails", required = false) mentorEmails: List<String>?,
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
        val creatorEmail = authentication.name

        val coverPath = cover?.let { fileStorageService.saveFile(it, creatorEmail) }
        val pdfPath = pdf?.let { fileStorageService.saveFile(it, creatorEmail) }
        val processedVideo = videoFile?.let {
            NamedFile(
                name = videoName ?: it.originalFilename ?: "Video",
                path = fileStorageService.saveFile(it, creatorEmail)
            )
        }

        val processedPhotos = processMultipleFiles(projectPhotos, projectPhotoNames, creatorEmail, "project-photos")
        val processedModels = processMultipleFiles(models3d, models3dNames, creatorEmail, "3d-models")

        val newDocument = Document(
            restorationData = restorationData,
            isPublished = isPublished,
            visibility = visibility,
            creatorEmail = creatorEmail,
            coCreatorEmails = coCreatorEmails ?: emptyList(),
            mentorEmails = mentorEmails ?: emptyList(),
            publicationDate = if (isPublished) Clock.System.now() else null,
            files = DocumentFiles(
                coverPath = coverPath,
                pdfPath = pdfPath,
                video = processedVideo,
                projectPhotos = processedPhotos,
                models3d = processedModels
            )
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