package hr.alu.okiru.doksusserver.features.reference_data.group

import org.springframework.web.bind.annotation.GetMapping
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
}