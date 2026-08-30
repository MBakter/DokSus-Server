package hr.alu.okiru.doksusserver.data.file

import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files

@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileStorageService: FileStorageService
) {

    @GetMapping
    fun downloadFile(@RequestParam("path") path: String): ResponseEntity<Resource> {
        val resource = fileStorageService.loadFileAsResource(path)

        // Try to determine the file's content type
        var contentType = Files.probeContentType(resource.file.toPath())
        if (contentType == null) {
            contentType = "application/octet-stream"
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
            .body(resource)
    }
}