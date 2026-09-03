package hr.alu.okiru.doksusserver.features.user

import hr.alu.okiru.doksusserver.security.WhitelistService
import hr.alu.okiru.doksusserver.security.jwt.JwtService
import hr.alu.okiru.doksusserver.util.ApiError
import hr.alu.okiru.doksusserver.util.ErrorCode
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
    private val userDetailsService: UserDetailsService,
    private val whitelistService: WhitelistService
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Any> {
        try {
            if (!whitelistService.isWhitelisted(request.email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiError(ErrorCode.NOT_WHITELISTED))
            }

            if (userRepository.existsByEmail(request.email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiError(ErrorCode.ALREADY_REGISTERED))
            }

            val user = User(
                name = request.name,
                surname = request.surname,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                isProfessor = whitelistService.isProfessor(request.email)
            )

            userRepository.save(user)
            return ResponseEntity.ok(ApiError(ErrorCode.SUCCESS))

        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError(ErrorCode.INTERNAL_ERROR, e.message))
        }
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
            isProfessor = user.isProfessor
        )

        return ResponseEntity.ok(AuthResponse(jwtToken, userDto))
    }
}