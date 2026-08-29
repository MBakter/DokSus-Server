package hr.alu.okiru.doksusserver.features.user

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "users")
data class User(
    @Id
    var id: String? = null,

    var name: String,
    var surname: String,
    var email: String,
    var passwordHash: String?,
    var role: String = "ROLE_USER"
)

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