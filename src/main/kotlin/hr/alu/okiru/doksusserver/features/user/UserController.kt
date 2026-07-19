package hr.alu.okiru.doksusserver.features.user

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userRepository: UserRepository
) {

    @GetMapping("/profiles")
    fun getProfiles(@RequestParam emails: List<String>): ResponseEntity<List<UserDto>> {
        if (emails.isEmpty()) {
            return ResponseEntity.ok(emptyList())
        }

        val users = userRepository.findByEmailIn(emails)
        val userDtos = users.map { user ->
            UserDto(
                id = user.id!!,
                name = user.name,
                surname = user.surname,
                email = user.email,
                role = user.role
            )
        }

        return ResponseEntity.ok(userDtos)
    }
}