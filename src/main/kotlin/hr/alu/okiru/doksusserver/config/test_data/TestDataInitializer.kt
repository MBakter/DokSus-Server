package hr.alu.okiru.doksusserver.config.test_data

import hr.alu.okiru.doksusserver.features.document.Document
import hr.alu.okiru.doksusserver.features.document.DocumentContent
import hr.alu.okiru.doksusserver.features.document.DocumentRepository
import hr.alu.okiru.doksusserver.features.user.User
import hr.alu.okiru.doksusserver.features.user.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import kotlin.time.Clock
import kotlin.time.Instant

@Component
class TestDataInitializer(
    private val userRepository: UserRepository,
    private val documentRepository: DocumentRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val user1Email = "elsoteszt@test.com"
        val user2Email = "masodikteszt@test.com"
        val defaultPassword = passwordEncoder.encode("lozinka123") // The password for both users is "lozinka123"

        // Initialize User 1
        if (!userRepository.existsByEmail(user1Email)) {
            val user1 = User(
                name = "Prvi",
                surname = "Korisnik",
                email = user1Email,
                passwordHash = defaultPassword,
                role = "ROLE_USER"
            )
            userRepository.save(user1)

            // Document 1: Published
            val doc1 = Document(
                isPublished = true,
                ownerEmail = user1Email,
                creationDate = Clock.System.now(),
                publicationDate = Instant.fromEpochMilliseconds(1784239552355),
                content = DocumentContent(
                    category = "DRVENI PREDMETI",
                    student = "Prvi Korisnik",
                    professor = "Prof. Horvat",
                    invNumber = "OKIRU-TEST-001",
                    name = "Restauracija Drvenog Kipa",
                    author = "Nepoznat",
                    material = "Drvo",
                    technique = "Polikromija",
                    keywords = "drvo, polikromija, skulptura",
                    location = "Muzej",
                    storage = "Depo 1",
                    typeOfAnalysis = "UV, IR",
                    goalOfAnalysis = "Stanje slojeva",
                    works = "Čišćenje, konsolidacija",
                    materialsUsed = "Konzervatorsko ljepilo",
                    pigment = "Oker, Cinober",
                    binder = "Tutkalo",
                    finishingLayer = "Lak",
                    date = "18. stoljeće"
                )
            )

            // Document 2: Private (Not Published)
            val doc2 = Document(
                isPublished = false,
                ownerEmail = user1Email,
                creationDate = Clock.System.now(),
                publicationDate = Instant.fromEpochMilliseconds(1784239552355),
                content = DocumentContent(
                    category = "OSTALI MATERIJALI",
                    student = "Prvi Korisnik",
                    professor = "Prof. Horvat",
                    invNumber = "OKIRU-TEST-002",
                    name = "Skica: Analiza Keramike",
                    author = "Lokalna radionica",
                    material = "Keramika",
                    technique = "Glazura",
                    keywords = "keramika, posuda",
                    location = "Laboratorij",
                    storage = "Radni stol",
                    typeOfAnalysis = "Kemijska",
                    goalOfAnalysis = "Sastav gline",
                    works = "U planu",
                    materialsUsed = "-",
                    pigment = "-",
                    binder = "-",
                    finishingLayer = "-",
                    date = "Antika"
                )
            )

            documentRepository.saveAll(listOf(doc1, doc2))
            println("Created test data for $user1Email")
        }

        // Initialize User 2
        if (!userRepository.existsByEmail(user2Email)) {
            val user2 = User(
                name = "Drugi",
                surname = "Korisnik",
                email = user2Email,
                passwordHash = defaultPassword,
                role = "ROLE_USER"
            )
            userRepository.save(user2)

            // Document 3: Published - Category 1
            val doc3 = Document(
                isPublished = true,
                ownerEmail = user2Email,
                creationDate = Clock.System.now(),
                publicationDate = Instant.fromEpochMilliseconds(1784239552355),
                content = DocumentContent(
                    category = "SLIKE NA PLATNU",
                    student = "Drugi Korisnik",
                    professor = "Prof. Kovač",
                    invNumber = "OKIRU-TEST-003",
                    name = "Konzervacija Ulja na Platnu",
                    author = "Ivan Ivić",
                    material = "Platno",
                    technique = "Ulje",
                    keywords = "slika, platno, ulje",
                    location = "Galerija",
                    storage = "Stalni postav",
                    typeOfAnalysis = "XRF",
                    goalOfAnalysis = "Identifikacija pigmenata",
                    works = "Lakiranje",
                    materialsUsed = "Damar lak",
                    pigment = "Olovno bijela",
                    binder = "Laneno ulje",
                    finishingLayer = "Smolni lak",
                    date = "1912"
                )
            )

            // Document 4: Published - Category 2
            val doc4 = Document(
                isPublished = true,
                ownerEmail = user2Email,
                creationDate = Clock.System.now(),
                publicationDate = Instant.fromEpochMilliseconds(1784239552355),
                content = DocumentContent(
                    category = "ZIDNE SLIKE",
                    student = "Drugi Korisnik",
                    professor = "Prof. Kovač",
                    invNumber = "OKIRU-TEST-004",
                    name = "Istraživanje Freske",
                    author = "Nepoznati majstor",
                    material = "Zid",
                    technique = "Freska",
                    keywords = "zidna slika, crkva",
                    location = "Crkva Sv. Marka",
                    storage = "In situ",
                    typeOfAnalysis = "Mikroskopska",
                    goalOfAnalysis = "Presjeci slojeva",
                    works = "Injektiranje",
                    materialsUsed = "Vapnena žbuka",
                    pigment = "Zelena zemlja",
                    binder = "Vapno",
                    finishingLayer = "Nema",
                    date = "15. stoljeće"
                )
            )

            documentRepository.saveAll(listOf(doc3, doc4))
            println("Created test data for $user2Email")
        }
    }
}