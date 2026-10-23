package hr.alu.okiru.doksusserver.features.reference_data

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints for basic reference data like enums or any other constant values which is needed by frontend.
 * The frontend loads categories and analysis types from here, so they only need to be defined once.
 */
@RestController
@RequestMapping("/api/reference-data")
class ReferenceDataController {

    @GetMapping("/categories")
    fun getCategories(): List<Map<String, String>> {
        return Category.entries.map {
            mapOf("id" to it.name, "name" to it.displayName)
        }
    }

    @GetMapping("/analysis-types")
    fun getAnalysisTypes(): List<Map<String, String>> {
        return AnalysisType.entries.map {
            mapOf(
                "id" to it.name,
                "abbr" to it.abbr,
                "nameEn" to it.nameEn,
                "nameHr" to it.nameHr
            )
        }
    }
}