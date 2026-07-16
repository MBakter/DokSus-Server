package hr.alu.okiru.doksusserver.features.user

data class RegisterRequest(
    val name: String,
    val surname: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: String,
    val name: String,
    val surname: String,
    val email: String,
    val role: String
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)