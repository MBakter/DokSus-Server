package hr.alu.okiru.doksusserver.security.jwt

import hr.alu.okiru.doksusserver.security.CustomUserDetailsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Runs before every request. A valid token in the `Authorization: Bearer` header, or in the
 * `jwt_token` cookie (used by images and other files the browser loads directly), marks the
 * request as coming from that user. Otherwise the request continues as anonymous.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val jwt = resolveToken(request)
        if (jwt == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val userEmail = jwtService.extractUsername(jwt)

            if (SecurityContextHolder.getContext().authentication == null) {
                val userDetails = userDetailsService.loadUserByUsername(userEmail)

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    val authToken = UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.authorities
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
        } catch (e: Exception) {
            // Log it and let the request proceed as unauthenticated.
            println("Invalid JWT Token: ${e.message}")
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            if (isUsable(token)) return token
        }

        val cookie = request.cookies?.firstOrNull { it.name == "jwt_token" }?.value
        return if (isUsable(cookie)) cookie else null
    }

    private fun isUsable(token: String?): Boolean {
        return !token.isNullOrBlank() && token != "null" && token != "undefined"
    }
}