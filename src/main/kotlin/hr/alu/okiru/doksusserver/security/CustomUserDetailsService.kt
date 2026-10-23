package hr.alu.okiru.doksusserver.security

import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/** Tells Spring Security how to load a user (by email) from MongoDB during login and token checks. */
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("User not found")

        return org.springframework.security.core.userdetails.User(
            user.email,
            user.passwordHash,
            listOf(SimpleGrantedAuthority(user.role))
        )
    }
}