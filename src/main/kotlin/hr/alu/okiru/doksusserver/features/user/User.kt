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