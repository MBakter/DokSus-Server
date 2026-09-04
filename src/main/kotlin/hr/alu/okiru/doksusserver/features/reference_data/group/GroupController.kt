package hr.alu.okiru.doksusserver.features.reference_data.group

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/reference-data/groups")
class GroupController(
    private val groupRepository: GroupRepository
) {
    @GetMapping
    fun getAllGroups(): List<String> {
        return groupRepository.findAll().map { it.name }
    }

    @GetMapping("/{id}")
    fun getGroupById(@PathVariable id: String): ResponseEntity<Group> {
        val group = groupRepository.findById(id).orElse(null)
        return if (group != null) ResponseEntity.ok(group) else ResponseEntity.notFound().build()
    }
}