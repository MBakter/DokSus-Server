package hr.alu.okiru.doksusserver.features.reference_data.group

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * A collection/group name (e.g. a series of artworks) that documents can belong to.
 * Stored in the `groups` collection; entries are created and removed automatically by [GroupService].
 */
@Document(collection = "groups")
data class Group(
    @Id
    var id: String? = null,

    @Indexed(unique = true)
    var name: String
)