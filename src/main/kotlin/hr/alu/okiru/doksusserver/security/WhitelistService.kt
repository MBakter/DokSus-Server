package hr.alu.okiru.doksusserver.security

import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

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
    private val whitelist = mutableMapOf<String, Boolean>()

    @PostConstruct
    fun loadWhitelist() {
        val resource = ClassPathResource("server-files/account-whitelist.json")
        if (resource.exists()) {
            val typeRef = object : TypeReference<List<WhitelistEntry>>() {}
            val entries: List<WhitelistEntry> = objectMapper.readValue(resource.inputStream, typeRef)
            entries.forEach { whitelist[it.email] = it.isProfessor }
        }
    }

    fun isWhitelisted(email: String): Boolean = whitelist.containsKey(email)
    fun isProfessor(email: String): Boolean = whitelist[email] ?: false
}