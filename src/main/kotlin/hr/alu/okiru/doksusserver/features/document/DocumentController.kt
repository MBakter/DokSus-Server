package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.data.filestorage.FileStorageService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal

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

//    @PostMapping
//    fun createDocument(
//        @RequestBody request: DocumentSaveRequest,
//        principal: Principal
//    ): ResponseEntity<Document> {
//        val newDocument = Document(
//            ownerEmail = principal.name, // Extracted securely from the JWT token
//            isPublished = request.isPublished,
//            content = request.content,
//        )
//
//        val savedDocument = documentRepository.save(newDocument)
//        return ResponseEntity.ok(savedDocument)
//    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createDocument(
        @RequestPart("document") documentContent: DocumentContent,
        @RequestParam("isPublished") isPublished: Boolean,
        @RequestPart("cover", required = false) cover: MultipartFile?,
        @RequestPart("pdf", required = false) pdf: MultipartFile?,
        @RequestPart("model3d", required = false) model3d: MultipartFile?,
        authentication: Authentication
    ): ResponseEntity<Document> {

        // Extract the email from the JWT Subject
        val ownerEmail = authentication.name

        var coverPath: String? = null
        var pdfPath: String? = null
        var model3dPath: String? = null

        // Process files if they were included in the request
        if (cover != null) coverPath = fileStorageService.saveFile(cover, ownerEmail)
        if (pdf != null) pdfPath = fileStorageService.saveFile(pdf, ownerEmail)
        if (model3d != null) model3dPath = fileStorageService.saveFile(model3d, ownerEmail)

        // Pass the paths to your MongoDB service to be saved alongside the document content
        val savedDocument = documentRepository.save(
            Document(
                content = documentContent,
                isPublished = isPublished,
                ownerEmail = ownerEmail,
                coverPath = coverPath,
                pdfPath = pdfPath,
                model3dPath = model3dPath
            )
        )

        return ResponseEntity.ok(savedDocument)
    }

    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateDocument(
        @PathVariable id: String,
        @RequestPart("document") documentContent: DocumentContent,
        @RequestParam("isPublished") isPublished: Boolean,
        @RequestPart("cover", required = false) cover: MultipartFile?,
        @RequestPart("pdf", required = false) pdf: MultipartFile?,
        @RequestPart("model3d", required = false) model3d: MultipartFile?,
        principal: Principal
    ): ResponseEntity<Any> {

        val existingDocument = documentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (existingDocument.ownerEmail != principal.name) {
            return ResponseEntity.status(403).body("Access denied: You don't have the right to modify this document.")
        }

        val ownerEmail = principal.name

        cover?.let { existingDocument.coverPath = fileStorageService.saveFile(it, ownerEmail) }
        pdf?.let { existingDocument.pdfPath = fileStorageService.saveFile(it, ownerEmail) }
        model3d?.let { existingDocument.model3dPath = fileStorageService.saveFile(it, ownerEmail) }

        existingDocument.content = documentContent
        existingDocument.isPublished = isPublished

        val updatedDocument = documentRepository.save(existingDocument)
        return ResponseEntity.ok(updatedDocument)
    }
}