package hr.alu.okiru.doksusserver.features.user

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<User, String> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByEmailIn(emails: List<String>): List<User>

    // Case-insensitive regex search across name, surname, or email
    @Query("{ '\$and': [ " +
            "{ '\$or': [ " +
            "{ 'name': { '\$regex': ?0, '\$options': 'i' } }, " +
            "{ 'surname': { '\$regex': ?0, '\$options': 'i' } }, " +
            "{ 'email': { '\$regex': ?0, '\$options': 'i' } } " +
            "] }, " +
            "{ 'isProfessor': ?1 } " +
            "] }")
    fun searchUsers(keyword: String, isProfessor: Boolean = false): List<User>
}