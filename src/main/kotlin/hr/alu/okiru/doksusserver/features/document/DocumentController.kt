package hr.alu.okiru.doksusserver.features.document

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentRepository: DocumentRepository
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
}