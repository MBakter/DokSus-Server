package hr.alu.okiru.doksusserver.data.file

import hr.alu.okiru.doksusserver.features.document.Document
import hr.alu.okiru.doksusserver.features.document.DocumentRepository
import hr.alu.okiru.doksusserver.features.reference_data.Category
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.Principal

/**
 * Serves stored files to the browser: uploaded user files (images, PDFs, 3D models, videos)
 * and the downloadable Word templates for each category.
 * A user file is only served to someone who is allowed to see the document it belongs to.
 */
@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileStorageService: FileStorageService,
    private val documentRepository: DocumentRepository
) {

    @GetMapping
    fun downloadFile(
        @RequestParam("path") path: String,
        principal: Principal?
    ): ResponseEntity<Resource> {
        val document = documentRepository.findByFilePath(path)
            ?: return ResponseEntity.notFound().build()

        if (!canRead(document, principal)) {
            return ResponseEntity.status(403).build()
        }

        val resource = fileStorageService.loadFileAsResource(path)

        var contentType = Files.probeContentType(resource.file.toPath())
        if (contentType == null) {
            contentType = "application/octet-stream"
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
            .body(resource)
    }

    @GetMapping("/template/{categoryId}")
    fun downloadTemplate(@PathVariable categoryId: String): ResponseEntity<Resource> {
        val category = try {
            Category.valueOf(categoryId)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        val templatePath = getTemplatePathForCategory(category)
        if (templatePath.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        return try {
            val resource = fileStorageService.loadFileAsResource(templatePath, isServerFile = true)

            val contentType = Files.probeContentType(resource.file.toPath())
                ?: "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

            // PROPER UTF-8 ENCODING FOR CROATIAN CHARACTERS
            val contentDisposition = ContentDisposition.attachment()
                .filename(resource.filename ?: "obrazac.docx", StandardCharsets.UTF_8)
                .build()

            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource)
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }

    private fun canRead(document: Document, principal: Principal?): Boolean {
        if (!document.isPublished) {
            return principal != null && document.creatorEmail == principal.name
        }
        if (document.visibility == Document.Visibility.OKIRU && principal == null) {
            return false
        }
        return true
    }

    private fun getTemplatePathForCategory(category: Category): String {
        val templatesUrl = "templates/"
        return when (category) {
            Category.DRVENI_PREDMETI -> templatesUrl + "Obrazac za dokumentiranje polikromiranih drvenih skulptura.docx"
            Category.SLIKE_NA_PLATNU -> templatesUrl + "Obrazac za dokumentiranje štafelajnih slika na platnu.docx"
            Category.ZIDNE_SLIKE -> templatesUrl + "Obrazac za dokumentiranje zidnih slika.docx"
            Category.KAMENA_I_ARHITEKTONSKA_PLASTIKA -> templatesUrl + "Obrazac za dokumentiranje kamena i arhitektonske plastike.docx"
            Category.OSTALI_MATERIJALI -> templatesUrl + "Obrazac za dokumentiranje ostalih materijala.docx"
            Category.ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI -> templatesUrl + "Obrazac za istraživačke radove i referentne materijale.docx"
            else -> ""
        }
    }
}