package hr.alu.okiru.doksusserver.features.reference_data.group

import hr.alu.okiru.doksusserver.features.document.DocumentRepository
import org.springframework.stereotype.Service

/**
 * Keeps the group list in sync with published documents: a group is added when the first published
 * document uses it and removed when no published document uses it any more.
 */
@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val documentRepository: DocumentRepository
) {

    fun addGroupIfNotExists(groupName: String?) {
        if (groupName.isNullOrBlank() || groupName.length > 80) return

        val name = groupName.trim()
        if (!groupRepository.existsByName(name)) {
            groupRepository.save(Group(name = name))
        }
    }

    fun removeGroupIfOrphaned(groupName: String?) {
        if (groupName.isNullOrBlank()) return

        val name = groupName.trim()
        val isStillInUse = documentRepository.existsByRestorationDataGroupAndIsPublishedTrue(name)

        if (!isStillInUse) {
            groupRepository.deleteByName(name)
        }
    }
}