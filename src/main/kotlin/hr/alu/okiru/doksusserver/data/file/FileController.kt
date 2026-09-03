package hr.alu.okiru.doksusserver.data.file

import hr.alu.okiru.doksusserver.features.reference_data.Category
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileStorageService: FileStorageService
) {

    @GetMapping
    fun downloadFile(@RequestParam("path") path: String): ResponseEntity<Resource> {
        // Defaults to user-files (isServerFile = false)
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