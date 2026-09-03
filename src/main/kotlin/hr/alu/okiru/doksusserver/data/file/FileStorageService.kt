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
    private val serverLocation: Path = Paths.get("server-files")

    init {
        Files.createDirectories(rootLocation)
        Files.createDirectories(serverLocation)
    }

    fun saveFile(file: MultipartFile, userEmail: String, subfolder: String? = null): String {
        if (file.isEmpty) throw IllegalArgumentException("Failed to store empty file.")

        var userDirectory = rootLocation.resolve(userEmail)
        if (subfolder != null) {
            userDirectory = userDirectory.resolve(subfolder)
        }
        Files.createDirectories(userDirectory)

        val fileExtension = file.originalFilename?.substringAfterLast(".", "")
        val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"
        val destinationFile = userDirectory.resolve(uniqueFilename).normalize().toAbsolutePath()

        Files.copy(file.inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING)

        return if (subfolder != null) {
            "$userEmail/$subfolder/$uniqueFilename"
        } else {
            "$userEmail/$uniqueFilename"
        }
    }

    fun loadFileAsResource(relativePath: String, isServerFile: Boolean = false): Resource {
        try {
            if (relativePath.contains("..")) {
                throw SecurityException("Cannot access files outside the storage directory.")
            }

            // Dynamically select the base path
            val basePath = if (isServerFile) serverLocation else rootLocation
            val filePath = basePath.resolve(relativePath).normalize()
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