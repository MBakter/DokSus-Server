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
 *
 * The email is the folder name so files stay tied to the person even if the database id changes.
 * Characters Windows rejects in a path (`< > : " / \ | ? *`), `%`, trailing dots and spaces, and
 * reserved device names such as `CON` are percent-encoded. A normal address is left unchanged.
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

        val folderName = emailToFolderName(userEmail)
        var userDirectory = rootLocation.resolve(folderName)
        if (subfolder != null) {
            userDirectory = userDirectory.resolve(subfolder)
        }
        Files.createDirectories(userDirectory)

        val fileExtension = file.originalFilename?.substringAfterLast(".", "")
        val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"
        val destinationFile = userDirectory.resolve(uniqueFilename).normalize().toAbsolutePath()

        Files.copy(file.inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING)

        return if (subfolder != null) {
            "$folderName/$subfolder/$uniqueFilename"
        } else {
            "$folderName/$uniqueFilename"
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

/** Windows-illegal path characters, plus `%` so the encoding cannot collide with a real address. */
private val illegalFolderChars = charArrayOf('<', '>', ':', '"', '/', '\\', '|', '?', '*', '%')

/** Names Windows rejects even with an extension, matched against the text before the first dot. */
private val reservedFolderNames = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
)

/**
 * Turns an email into one folder name. Ordinary addresses are returned as-is, so existing
 * `user-files/<email>/` directories and stored paths keep working.
 */
internal fun emailToFolderName(email: String): String {
    val encoded = buildString(email.length) {
        for (ch in email) {
            if (ch.code < 32 || ch.code == 127 || ch in illegalFolderChars) {
                append(percentEncode(ch))
            } else {
                append(ch)
            }
        }
    }

    val safe = buildString(encoded.length) {
        encoded.forEachIndexed { index, ch ->
            val remainderIsTrailing = encoded.substring(index).all { it == '.' || it == ' ' }
            if ((ch == '.' || ch == ' ') && remainderIsTrailing) {
                append(percentEncode(ch))
            } else {
                append(ch)
            }
        }
    }

    if (safe.isEmpty()) throw IllegalArgumentException("Email is empty.")

    val stem = safe.substringBefore('.')
    if (stem.uppercase() in reservedFolderNames) {
        return percentEncode(safe.first()) + safe.drop(1)
    }
    return safe
}

private fun percentEncode(ch: Char): String =
    "%" + ch.code.toString(16).uppercase().padStart(2, '0')