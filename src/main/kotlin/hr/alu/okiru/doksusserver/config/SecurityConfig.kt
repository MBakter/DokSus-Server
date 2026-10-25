package hr.alu.okiru.doksusserver.config

import hr.alu.okiru.doksusserver.security.jwt.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfigurationSource

/**
 * Central security setup. Decides which endpoints are public and which need a logged-in user,
 * plugs in the JWT filter and defines how passwords are hashed (BCrypt).
 * When adding a new endpoint that must be reachable without login, register it here.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter
) {

    @Bean
    fun filterChain(http: HttpSecurity, corsConfigurationSource: CorsConfigurationSource): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                exceptions.accessDeniedHandler { _, response, _ ->
                    val auth = SecurityContextHolder.getContext().authentication
                    val anonymous = auth == null || auth is AnonymousAuthenticationToken
                    val status = if (anonymous) HttpServletResponse.SC_UNAUTHORIZED else HttpServletResponse.SC_FORBIDDEN
                    response.sendError(status)
                }
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/documents/me").authenticated() // Keep /me protected
                    .requestMatchers(HttpMethod.GET, "/api/documents/**").permitAll() // Open GET for fetching docs
                    .requestMatchers("/api/reference-data/**").permitAll()
                    .requestMatchers("/api/users/profiles").permitAll()
                    .requestMatchers("/api/users/search").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/files", "/api/files/template/**").permitAll()
                    .anyRequest().authenticated()

            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager {
        return config.authenticationManager
    }
}