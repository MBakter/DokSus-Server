package hr.alu.okiru.doksusserver.security

import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.io.File

data class WhitelistEntry(
    val email: String,
    val isProfessor: Boolean = false
)

/**
 * Whitelist specifies which emails can be registered
 *
 * Note: If a user is registered, removing the email from the whitelist won't restrict them from logging in
 */
@Service
class WhitelistService(private val objectMapper: ObjectMapper) {
    private val file = File("server-files/account-whitelist.json")

    // This method reads the file fresh from the disk every time it is called
    private fun getWhitelistMap(): Map<String, Boolean> {
        if (!file.exists()) {
            println("WARNING: Whitelist file not found at ${file.absolutePath}")
            return emptyMap()
        }

        val typeRef = object : TypeReference<List<WhitelistEntry>>() {}
        val entries: List<WhitelistEntry> = objectMapper.readValue(file, typeRef)

        // Converts the list into a Map<Email, isProfessor> for instant lookups
        return entries.associate { it.email to it.isProfessor }
    }

    fun isWhitelisted(email: String): Boolean = getWhitelistMap().containsKey(email)
    fun isProfessor(email: String): Boolean = getWhitelistMap()[email] ?: false
}