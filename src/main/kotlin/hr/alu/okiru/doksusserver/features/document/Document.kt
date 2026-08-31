package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.features.user.User
import hr.alu.okiru.doksusserver.features.user.UserDto
import org.springframework.data.annotation.Id
import kotlin.time.Clock
import kotlin.time.Instant
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument

data class NamedFile(
    val name: String,
    val path: String
)

@MongoDocument(collection = "documents")
data class Document(
    @Id
    val id: String? = null,

    //Metadata
    var isPublished: Boolean = false,
    var ownerEmail: String,
    var visibility: Visibility,
    var creationDate: Instant = Clock.System.now(),
    var publicationDate: Instant? = null,
    var coAuthorEmails: List<String> = emptyList(),

    @Transient
    var ownerProfile: UserDto? = null,

    @Transient
    var authorProfiles: List<UserDto> = emptyList(),

    // Files
    var coverPath: String? = null,
    var pdfPath: String? = null,
    var projectPhotos: List<NamedFile> = emptyList(),
    var models3d: List<NamedFile> = emptyList(),
    var video: NamedFile? = null,

    var content: DocumentContent,
) {
    enum class Visibility {
        PUBLIC,
        OKIRU
    }
}

data class DocumentContent(
    var category: String,
    var student: String,
    var professor: String,
    var invNumber: String,
    var name: String,
    var author: String,
    var material: String,
    var technique: String,
    var keywords: String,
    var location: String,
    var storage: String,
    var typeOfAnalysis: String,
    var goalOfAnalysis: String,
    var works: String,
    var materialsUsed: String,
    var pigment: String,
    var binder: String,
    var finishingLayer: String,
    var date: String
)

data class DocumentSaveRequest(
    val content: DocumentContent,
    val isPublished: Boolean
)