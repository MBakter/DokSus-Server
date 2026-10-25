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
import java.util.*

/**
 * Saves and loads files on the server's disk. Uploads go to `user-files/<email>/` under a random
 * UUID name; only the relative path is stored in the database. Templates are read from `server-files/`.
 * Both folders are relative to the directory the server is started from.
 */
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

    /** Deletes one uploaded file. Missing files and unsafe paths are ignored. */
    fun deleteFile(relativePath: String?) {
        if (relativePath.isNullOrBlank() || relativePath.contains("..")) return

        try {
            val base = rootLocation.toAbsolutePath().normalize()
            val filePath = base.resolve(relativePath).normalize()
            if (!filePath.startsWith(base)) return
            Files.deleteIfExists(filePath)
        } catch (e: Exception) {
            println("Could not delete file $relativePath: ${e.message}")
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