package hr.alu.okiru.doksusserver.data.test

import hr.alu.okiru.doksusserver.features.document.*
import hr.alu.okiru.doksusserver.features.reference_data.AnalysisType
import hr.alu.okiru.doksusserver.features.reference_data.Category
import hr.alu.okiru.doksusserver.features.reference_data.group.Group
import hr.alu.okiru.doksusserver.features.reference_data.group.GroupRepository
import hr.alu.okiru.doksusserver.features.user.User
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.util.FileSystemUtils
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Component
class DatabaseSeeder(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val documentRepository: DocumentRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String) {
        println("Clearing database collections and uploaded files...")

        documentRepository.deleteAll()
        groupRepository.deleteAll()
        userRepository.deleteAll()

        // Clean up old user files directory to prevent infinite file buildup on restarts
        val userFilesPath = Paths.get("user-files")
        FileSystemUtils.deleteRecursively(userFilesPath)

        val encodedPassword = passwordEncoder.encode("Test1234")

        // 1. Generate Test Users
        val users = listOf(
            User(name = "Ivan", surname = "Profesor", email = "ivan.profesor@test.com", passwordHash = encodedPassword, role = "ROLE_MENTOR", isProfessor = true),
            User(name = "Marija", surname = "Mentor", email = "marija.mentor@test.com", passwordHash = encodedPassword, role = "ROLE_MENTOR", isProfessor = true),
            User(name = "Josip", surname = "Glavni", email = "josip.glavni@test.com", passwordHash = encodedPassword, role = "ROLE_MENTOR", isProfessor = true),
            User(name = "Luka", surname = "Kovač", email = "luka.kovac@test.com", passwordHash = encodedPassword, role = "ROLE_USER"),
            User(name = "Ana", surname = "Babić", email = "ana.babic@test.com", passwordHash = encodedPassword, role = "ROLE_USER"),
            User(name = "Petar", surname = "Horvat", email = "petar.horvat@test.com", passwordHash = encodedPassword, role = "ROLE_USER"),
            User(name = "Jelena", surname = "Jukić", email = "jelena.jukic@test.com", passwordHash = encodedPassword, role = "ROLE_USER"),
            User(name = "Filip", surname = "Novak", email = "filip.novak@test.com", passwordHash = encodedPassword, role = "ROLE_USER"),
            User(name = "Nina", surname = "Vidić", email = "nina.vidic@test.com", passwordHash = encodedPassword, role = "ROLE_USER")
        )
        userRepository.saveAll(users)

        val mentors = users.filter { it.isProfessor }
        val students = users.filter { !it.isProfessor }

        // 2. Create Groups
        val groups = listOf(
            Group(name = "Nacionalni Muzej"),
            Group(name = "Zbirka Katedre"),
            Group(name = "Gradski Arhiv"),
            Group(name = "Privatna Zbirka")
        )
        groupRepository.saveAll(groups)

        // 3. Prepare Varied Categories Distribution (Exactly 50 items)
        val categoriesDistribution = mutableListOf<Category>().apply {
            repeat(18) { add(Category.SLIKE_NA_PLATNU) }
            repeat(9) { add(Category.DRVENI_PREDMETI) }
            repeat(7) { add(Category.DIPLOMSKI_I_SEMINARSKI_RADOVI) }
            repeat(5) { add(Category.ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI) }
            repeat(4) { add(Category.KAMENA_I_ARHITEKTONSKA_PLASTIKA) }
            repeat(3) { add(Category.ZIDNE_SLIKE) }
            repeat(2) { add(Category.OSTALI_MATERIJALI) }
            repeat(2) { add(Category.UNSPECIFIED) }
        }
        categoriesDistribution.shuffle()

        // 4. File Mapping Configurations
        val categoryPhotos = mapOf(
            Category.SLIKE_NA_PLATNU to listOf("canvas1.jpg", "canvas2.jpg", "canvas3.jpg"),
            Category.DRVENI_PREDMETI to listOf("wood1.jpg", "wood2.jpg", "wood3.jpg"),
            Category.KAMENA_I_ARHITEKTONSKA_PLASTIKA to listOf("stone1.jpg", "stone2.jpg", "stone3.jpg", "stucco1.jpg", "stucco2.jpg", "stucco3.jpg"),
            Category.ZIDNE_SLIKE to listOf("wall1.jpg", "wall2.jpg", "wall3.jpg"),
            Category.OSTALI_MATERIJALI to listOf("ostali1.jpg", "ostali2.jpg", "ostali3.jpg")
        )
        val defaultPhotos = listOf("ostali1.jpg", "ostali2.jpg", "ostali3.jpg")

        val categoryModels = mapOf(
            Category.DRVENI_PREDMETI to listOf("wood1.glb"),
            Category.KAMENA_I_ARHITEKTONSKA_PLASTIKA to listOf("stone1.glb", "stone2.glb"),
            Category.ZIDNE_SLIKE to listOf("wall1.glb"),
            Category.OSTALI_MATERIJALI to listOf("ostali1.glb", "ostali2.glb")
        )

        // 5. Create Documents
        val documents = mutableListOf<Document>()

        val materialsPool = listOf("Drvo, Platno", "Papir", "Kamen", "Bronca", "Keramika")
        val techniquesPool = listOf("Ulje na platnu", "Tisak", "Klesanje", "Lijevanje", "Glaziranje")
        val locationsPool = listOf("Crkva Sv. Marka", "Tavan", "Glavni trg", "Podrum arhive")

        val analysisPool = AnalysisType.entries.toTypedArray()

        val exemptCategories = listOf(
            Category.ISTRAZIVACKI_RADOVI_I_REFERENTNI_MATERIJALI,
            Category.DIPLOMSKI_I_SEMINARSKI_RADOVI,
            Category.UNSPECIFIED
        )

        val now = Clock.System.now()

        // Generate exactly 50 documents
        for (i in 1..50) {
            val category = categoriesDistribution[i - 1]

            var isPublished = (i % 5 != 0) // ~20% drafts
            if (category == Category.UNSPECIFIED) {
                isPublished = false // Enforce UNSPECIFIED must be a draft
            }

            val groupName = if (i <= 24) groups[(i - 1) / 6].name else ""
            val creator = students[i % students.size]

            val coCreatorCount = i % 6
            val availableCoAuthors = students.filter { it.email != creator.email }
            val coCreatorEmails = availableCoAuthors.shuffled().take(coCreatorCount).map { it.email }

            val mentorCount = (i % 3) + 1
            val mentorEmails = mentors.shuffled().take(mentorCount).map { it.email }

            val isExemptCategory = category in exemptCategories

            val hasInv = !isExemptCategory || (i % 2 == 0)
            val hasAuthor = !isExemptCategory || (i % 3 == 0)
            val hasDate = !isExemptCategory || (i % 4 == 1)
            val hasMaterial = !isExemptCategory || (i % 5 > 2)
            val hasTechnique = !isExemptCategory || (i % 2 != 0)
            val hasStorage = !isExemptCategory || (i % 3 != 0)

            val restorationData = RestorationData(
                category = category,
                name = "Example Artefact $i",
                keywords = "example, test, konzervacija, ${materialsPool[i % materialsPool.size]}",
                inventoryNumber = if (hasInv) "INV-TEST-${1000 + i}" else "",
                author = if (hasAuthor) (if (i % 3 == 0) "Nepoznati Autor" else "Majstor $i") else "",
                date = if (hasDate) "19. stoljeće" else "",
                material = if (hasMaterial) materialsPool[i % materialsPool.size] else "",
                technique = if (hasTechnique) techniquesPool[i % techniquesPool.size] else "",
                storage = if (hasStorage) "Depo ${i % 5}" else "",
                group = groupName,
                location = locationsPool[i % locationsPool.size],
                typeOfAnalysis = listOf(
                    Analysis(type = analysisPool[i % analysisPool.size], goal = "Identifikacija veziva"),
                    Analysis(type = analysisPool[(i + 1) % analysisPool.size], goal = "Analiza pigmenata")
                ),
                works = listOf(Work(name = "Čišćenje", material = "Destilirana voda, etanol")),
                pigment = if (i % 2 == 0) "Oker, Cinober" else "",
                binder = "Laneno ulje",
                finishingLayer = "Damar lak"
            )

            val documentFiles = DocumentFiles()

            // 6. Handle Files for Published Documents
            if (isPublished) {
                // PDF 1 is required
                documentFiles.pdfPath = copyExampleFile("pdf1.pdf", creator.email)

                // Cover Photo is required
                val photosPool = categoryPhotos[category] ?: defaultPhotos
                documentFiles.coverPath = copyExampleFile(photosPool.random(), creator.email)

                // Project Photos (0 to 5)
                val numPhotos = (0..5).random()
                val projectPhotos = mutableListOf<NamedFile>()
                repeat(numPhotos) { idx ->
                    val photoPath = copyExampleFile(photosPool.random(), creator.email)
                    if (photoPath != null) projectPhotos.add(NamedFile("Example Photo ${idx + 1}", photoPath))
                }
                documentFiles.projectPhotos = projectPhotos

                // Additional PDFs (0 to 3)
                val numPdfs = (0..3).random()
                val additionalPdfsList = mutableListOf<NamedFile>()
                val extraPdfNames = listOf("pdf2.pdf", "pdf3.pdf")
                repeat(numPdfs) { idx ->
                    val pdfPath = copyExampleFile(extraPdfNames.random(), creator.email)
                    if (pdfPath != null) additionalPdfsList.add(NamedFile("Dodatni Dokument ${idx + 1}", pdfPath))
                }
                documentFiles.additionalPdfs = additionalPdfsList

                // Video (40% chance)
                if (Math.random() < 0.40) {
                    val videoPath = copyExampleFile("video1.mp4", creator.email)
                    if (videoPath != null) documentFiles.video = NamedFile("Restauracija Video", videoPath)
                }

                // 3D Models (60% chance)
                if (Math.random() < 0.60) {
                    val modelsPool = categoryModels[category] ?: emptyList()
                    if (modelsPool.isNotEmpty()) {
                        val modelPath = copyExampleFile(modelsPool.random(), creator.email)
                        if (modelPath != null) documentFiles.models3d = listOf(NamedFile("3D Sken Artefakta", modelPath))
                    }
                }
            }

            val document = Document(
                isPublished = isPublished,
                visibility = if (i % 5 == 0) Document.Visibility.OKIRU else Document.Visibility.PUBLIC,
                creatorEmail = creator.email,
                coCreatorEmails = coCreatorEmails,
                mentorEmails = mentorEmails,
                creationDate = now.minus((60 - i).days),
                publicationDate = if (isPublished) now.minus((40 - i).days) else null,
                restorationData = restorationData,
                files = documentFiles
            )

            documents.add(document)
        }

        documentRepository.saveAll(documents)
        println("Successfully seeded database with 50 varied documents and simulated file uploads.")
    }

    /**
     * Simulates the behavior of your `saveFile` method by copying files from `example-data`
     * to `user-files/{userEmail}` and generating a UUID for the file name.
     */
    private fun copyExampleFile(sourceFileName: String, userEmail: String): String? {
        val exampleDir = Paths.get("example-data")
        val userFilesDir = Paths.get("user-files", userEmail)

        val sourceFile = exampleDir.resolve(sourceFileName)

        // If the file doesn't exist in example-data, gracefully skip without crashing
        if (!Files.exists(sourceFile)) {
            println("Warning: Example file not found: $sourceFile")
            return null
        }

        if (!Files.exists(userFilesDir)) {
            Files.createDirectories(userFilesDir)
        }

        val fileExtension = sourceFileName.substringAfterLast(".", "")
        val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"
        val destinationFile = userFilesDir.resolve(uniqueFilename).normalize().toAbsolutePath()

        Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING)

        return "$userEmail/$uniqueFilename"
    }
}