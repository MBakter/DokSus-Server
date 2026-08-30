package hr.alu.okiru.doksusserver.data.file

import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class FileStorageService {

    private val rootLocation: Path = Paths.get("user-files")

    init {
        Files.createDirectories(rootLocation)
    }

    fun saveFile(file: MultipartFile, userEmail: String, subfolder: String? = null): String {
        if (file.isEmpty) throw IllegalArgumentException("Failed to store empty file.")

        // Build the directory path
        var userDirectory = rootLocation.resolve(userEmail)
        if (subfolder != null) {
            userDirectory = userDirectory.resolve(subfolder)
        }
        Files.createDirectories(userDirectory)

        // Generate a unique filename
        val fileExtension = file.originalFilename?.substringAfterLast(".", "")
        val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"
        val destinationFile = userDirectory.resolve(uniqueFilename).normalize().toAbsolutePath()

        Files.copy(file.inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING)

        // Return the relative path for MongoDB
        return if (subfolder != null) {
            "$userEmail/$subfolder/$uniqueFilename"
        } else {
            "$userEmail/$uniqueFilename"
        }
    }

    fun loadFileAsResource(relativePath: String): Resource {
        try {
            // Security check to prevent directory traversal attacks
            if (relativePath.contains("..")) {
                throw SecurityException("Cannot access files outside the storage directory.")
            }

            val filePath = rootLocation.resolve(relativePath).normalize()
            val resource = UrlResource(filePath.toUri())

            if (resource.exists() || resource.isReadable) {
                return resource
            } else {
                throw FileNotFoundException("Could not read file: $relativePath")
            }
        } catch (e: Exception) {
            throw FileNotFoundException("Could not read file: $relativePath")
        }
    }
}