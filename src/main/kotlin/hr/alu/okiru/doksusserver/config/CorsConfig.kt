package hr.alu.okiru.doksusserver.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS (Cross-Origin Resource Sharing) settings. Lists which website addresses are allowed
 * to call this API from a browser. The list comes from `app.cors.allowed-origins`, so the
 * production address is added with an environment variable and not by editing this file.
 */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: String
) {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        val origins = allowedOrigins.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOrigins(*origins.toTypedArray())
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
            }
        }
    }
}