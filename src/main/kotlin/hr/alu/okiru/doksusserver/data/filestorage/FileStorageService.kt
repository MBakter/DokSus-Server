package hr.alu.okiru.doksusserver.data.filestorage

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
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

    fun saveFile(file: MultipartFile, userEmail: String): String {
        if (file.isEmpty) throw IllegalArgumentException("Failed to store empty file.")

        // Create a user-specific directory
        val userDirectory = rootLocation.resolve(userEmail)
        Files.createDirectories(userDirectory)

        // Generate a unique filename to prevent overwriting existing files
        val fileExtension = file.originalFilename?.substringAfterLast(".", "")
        val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"
        val destinationFile = userDirectory.resolve(uniqueFilename).normalize().toAbsolutePath()

        // Copy the file to the target location
        Files.copy(file.inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING)

        // Return the relative path to store in MongoDB
        return "$userEmail/$uniqueFilename"
    }
}