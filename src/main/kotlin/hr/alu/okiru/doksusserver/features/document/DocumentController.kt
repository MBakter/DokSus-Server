package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.data.file.FileStorageService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
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
    private val documentRepository: DocumentRepository,
    private val fileStorageService: FileStorageService,
) {

    @GetMapping
    fun getDocuments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestHeader(value = "Category", required = false) category: String?
    ): Page<Document> {

        val pageable: Pageable = PageRequest.of(page, size)
        return documentRepository.searchDocuments(category, search, pageable)
    }

    @GetMapping("/me")
    fun getMyDocuments(principal: Principal): ResponseEntity<List<Document>> {
        // principal.name contains the email extracted from the JWT token
        val userEmail = principal.name

        val userDocuments = documentRepository.findByOwnerEmail(userEmail)
        return ResponseEntity.ok(userDocuments)
    }

    @GetMapping("/user")
    fun getUserPublishedDocuments(@RequestParam email: String): ResponseEntity<List<Document>> {
        val publicDocuments = documentRepository.findByOwnerEmailAndIsPublishedTrue(email)
        return ResponseEntity.ok(publicDocuments)
    }

    @GetMapping("/{id}")
    fun getDocumentById(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Document> {
        val document = documentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // HARD SECURITY ENFORCEMENT: If private, verify ownership
        if (!document.isPublished) {
            if (principal == null || document.ownerEmail != principal.name) {
                return ResponseEntity.status(403).build()
            }
        }

        return ResponseEntity.ok(document)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createDocument(
        @RequestPart("document") documentContent: DocumentContent,
        @RequestParam("isPublished") isPublished: Boolean,
        @RequestPart("cover", required = false) cover: MultipartFile?,
        @RequestPart("pdf", required = false) pdf: MultipartFile?,
        @RequestPart("video", required = false) videoFile: MultipartFile?,
        @RequestParam("videoName", required = false) videoName: String?,
        @RequestPart("projectPhotos", required = false) projectPhotos: List<MultipartFile>?,
        @RequestParam("projectPhotoNames", required = false) projectPhotoNames: List<String>?, // Changed to @RequestParam
        @RequestPart("models3d", required = false) models3d: List<MultipartFile>?,
        @RequestParam("models3dNames", required = false) models3dNames: List<String>?, // Changed to @RequestParam

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
            ownerEmail = ownerEmail,
            publicationDate = if (isPublished) Clock.System.now() else null, // Handle publication date
            coverPath = coverPath,
            pdfPath = pdfPath,
            video = processedVideo,
            projectPhotos = processedPhotos,
            models3d = processedModels
        )

        val savedDocument = documentRepository.save(newDocument)
        return ResponseEntity.ok(savedDocument)
    }

    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateDocument(
        @PathVariable id: String,
        @RequestPart("document") documentContent: DocumentContent,
        @RequestParam("isPublished") isPublished: Boolean,

        // --- Cover ---
        @RequestPart("cover", required = false) cover: MultipartFile?,
        @RequestParam("removeServerCover", required = false, defaultValue = "false") removeServerCover: Boolean,

        // --- PDF ---
        @RequestPart("pdf", required = false) pdf: MultipartFile?,
        @RequestParam("removeServerPdf", required = false, defaultValue = "false") removeServerPdf: Boolean,

        // --- Video ---
        @RequestPart("video", required = false) videoFile: MultipartFile?,
        @RequestParam("videoName", required = false) videoName: String?,
        @RequestPart("existingVideo", required = false) existingVideo: NamedFile?,
        @RequestParam("removeServerVideo", required = false, defaultValue = "false") removeServerVideo: Boolean,

        // --- Project Photos ---
        @RequestPart("existingProjectPhotos", required = false) existingProjectPhotos: List<NamedFile>?,
        @RequestPart("projectPhotos", required = false) projectPhotos: List<MultipartFile>?,
        @RequestParam("projectPhotoNames", required = false) projectPhotoNames: List<String>?,

        // --- 3D Models ---
        @RequestPart("existingModels3d", required = false) existingModels3d: List<NamedFile>?,
        @RequestPart("models3d", required = false) models3d: List<MultipartFile>?,
        @RequestParam("models3dNames", required = false) models3dNames: List<String>?,

        principal: Principal
    ): ResponseEntity<Any> {
        val existingDocument = documentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (existingDocument.ownerEmail != principal.name) {
            return ResponseEntity.status(403).body("Access denied: You do not have permission to edit this document.")
        }

        val ownerEmail = principal.name

        // 1. Handle Cover Deletion & Update
        if (removeServerCover) {
            existingDocument.coverPath = null
        }
        cover?.let { existingDocument.coverPath = fileStorageService.saveFile(it, ownerEmail) }

        // 2. Handle PDF Deletion & Update
        if (removeServerPdf) {
            existingDocument.pdfPath = null
        }
        pdf?.let { existingDocument.pdfPath = fileStorageService.saveFile(it, ownerEmail) }

        // 3. Handle Video Rename, Deletion, & Update
        if (removeServerVideo) {
            existingDocument.video = null
        } else if (existingVideo != null) {
            existingDocument.video = existingVideo
        }
        videoFile?.let {
            existingDocument.video = NamedFile(
                name = videoName ?: it.originalFilename ?: "Video",
                path = fileStorageService.saveFile(it, ownerEmail)
            )
        }

        // 4. Handle Photo Deletions/Renames (Overwrites existing list) & Append New
        if (existingProjectPhotos != null) {
            existingDocument.projectPhotos = existingProjectPhotos
        }
        val newPhotos = processMultipleFiles(projectPhotos, projectPhotoNames, ownerEmail, "project-photos")
        if (newPhotos.isNotEmpty()) {
            existingDocument.projectPhotos = existingDocument.projectPhotos + newPhotos
        }

        // 5. Handle 3D Model Deletions/Renames (Overwrites existing list) & Append New
        if (existingModels3d != null) {
            existingDocument.models3d = existingModels3d
        }
        val newModels = processMultipleFiles(models3d, models3dNames, ownerEmail, "3d-models")
        if (newModels.isNotEmpty()) {
            existingDocument.models3d = existingDocument.models3d + newModels
        }

        // Handle publication date logic
        if (isPublished && !existingDocument.isPublished) {
            existingDocument.publicationDate = Clock.System.now()
        } else if (!isPublished && existingDocument.isPublished) {
            existingDocument.publicationDate = null
        }

        existingDocument.content = documentContent
        existingDocument.isPublished = isPublished

        val updatedDocument = documentRepository.save(existingDocument)
        return ResponseEntity.ok(updatedDocument)
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