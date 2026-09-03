package hr.alu.okiru.doksusserver.features.reference_data.group

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "groups")
data class Group(
    @Id
    var id: String? = null,

    @Indexed(unique = true)
    var name: String
)