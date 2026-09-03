package hr.alu.okiru.doksusserver.features.document

import hr.alu.okiru.doksusserver.features.reference_data.AnalysisType
import hr.alu.okiru.doksusserver.features.reference_data.Category
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

    // Metadata
    var isPublished: Boolean = false,
    var visibility: Visibility,
    var creatorEmail: String,
    var coCreatorEmails: List<String> = emptyList(),
    var mentorEmails: List<String> = emptyList(),
    var creationDate: Instant = Clock.System.now(),
    var publicationDate: Instant? = null,

    var restorationData: RestorationData,
    var files: DocumentFiles = DocumentFiles(),

    @Transient
    var profiles: DocumentProfiles = DocumentProfiles()
) {
    enum class Visibility {
        PUBLIC,
        OKIRU
    }
}

// Grouped file paths
data class DocumentFiles(
    var coverPath: String? = null,
    var pdfPath: String? = null,
    var models3d: List<NamedFile> = emptyList(),

    var projectPhotos: List<NamedFile> = emptyList(),
    var video: NamedFile? = null
)

// Grouped transient profiles
data class DocumentProfiles(
    var creatorProfile: UserDto? = null,
    var coCreatorProfiles: List<UserDto> = emptyList(),
    var mentorProfiles: List<UserDto> = emptyList()
)

data class RestorationData(
    var category: Category = Category.UNSPECIFIED,
    var inventoryNumber: String,
    var name: String,
    var author: String,
    var date: String,
    var group: String,
    var material: String,
    var technique: String,
    var location: String,
    var storage: String,
    var typeOfAnalysis: List<Analysis> = emptyList(),
    var works: List<Work> = emptyList(),
    var pigment: String,
    var binder: String,
    var finishingLayer: String,
    var keywords: String
)

data class Analysis(
    var type: AnalysisType,
    var goal: String
)

data class Work(
    var name: String,
    var material: String
)

data class DocumentSaveRequest(
    val content: RestorationData,
    val isPublished: Boolean
)

data class DocumentMetadataUpdateRequest(
    val content: RestorationData,
    val isPublished: Boolean,
    val visibility: Document.Visibility,
    val coCreatorEmails: List<String> = emptyList(), // Renamed
    val mentorEmails: List<String> = emptyList()     // Added
)

data class SyncFilesRequest(
    val existingFiles: List<NamedFile>
)

data class SyncFileRequest(
    val existingFile: NamedFile?
)