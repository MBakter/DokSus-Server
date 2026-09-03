package hr.alu.okiru.doksusserver.features.reference_data.group

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupRepository : MongoRepository<Group, String> {
    fun existsByName(name: String): Boolean
    fun deleteByName(name: String)
}