package hr.alu.okiru.doksusserver.features.user

import hr.alu.okiru.doksusserver.security.JwtService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Any> {
        if (userRepository.existsByEmail(request.email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "Email is already registered"))
        }

        val user = User(
            name = request.name,
            surname = request.surname,
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
        )

        userRepository.save(user)
        return ResponseEntity.ok(mapOf("message" to "User registered successfully"))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val userDetails = userDetailsService.loadUserByUsername(request.email)
        val jwtToken = jwtService.generateToken(userDetails)
        val user = userRepository.findByEmail(request.email)!!

        val userDto = UserDto(
            id = user.id!!,
            name = user.name,
            surname = user.surname,
            email = user.email,
        )

        return ResponseEntity.ok(AuthResponse(jwtToken, userDto))
    }
}