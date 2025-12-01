package com.example.reefscan.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the complete analysis of a reef tank image.
 * Contains multiple identifications, overall tank assessment, and recommendations.
 */
@JsonClass(generateAdapter = true)
data class ScanResult(
    /**
     * Overall tank health assessment
     */
    @Json(name = "tank_health")
    val tankHealth: String = "Good",
    
    /**
     * Summary of what was found in the image
     */
    @Json(name = "summary")
    val summary: String = "",
    
    /**
     * List of all identified organisms, problems, and issues
     */
    @Json(name = "identifications")
    val identifications: List<Identification> = emptyList(),
    
    /**
     * Overall recommendations for the tank
     */
    @Json(name = "recommendations")
    val recommendations: List<String> = emptyList(),
    
    // Legacy fields for backward compatibility with saved scans
    @Json(name = "name")
    val name: String = "",
    
    @Json(name = "category")
    val category: String = "",
    
    @Json(name = "confidence")
    val confidence: Int = 0,
    
    @Json(name = "is_problem")
    val isProblem: Boolean = false,
    
    @Json(name = "severity")
    val severity: String? = null,
    
    @Json(name = "description")
    val description: String = ""
) {
    
    /**
     * Get the primary identification (first one, or most important)
     */
    fun getPrimaryIdentification(): Identification? {
        // Prioritize problems, then by confidence
        return identifications
            .sortedWith(compareByDescending<Identification> { it.isProblem }
                .thenByDescending { it.confidence })
            .firstOrNull()
    }
    
    /**
     * Get all problem identifications
     */
    fun getProblems(): List<Identification> {
        return identifications.filter { it.isProblem }
    }
    
    /**
     * Get all healthy/good identifications
     */
    fun getHealthyItems(): List<Identification> {
        return identifications.filter { !it.isProblem }
    }
    
    /**
     * Check if there are any problems detected
     */
    fun hasProblems(): Boolean {
        return identifications.any { it.isProblem }
    }
    
    /**
     * Returns the overall issue status for the scan
     */
    fun getIssueStatus(): IssueStatus {
        val problems = getProblems()
        return when {
            problems.any { it.severity == "High" } -> IssueStatus.PROBLEM
            problems.any { it.severity == "Medium" } -> IssueStatus.WARNING
            problems.isNotEmpty() -> IssueStatus.WARNING
            else -> IssueStatus.HEALTHY
        }
    }
    
    /**
     * Legacy support - get category type from primary identification
     */
    fun getCategoryType(): CategoryType {
        return getPrimaryIdentification()?.getCategoryType() 
            ?: CategoryType.fromString(category)
    }
    
    companion object {
        /**
         * Creates an empty/placeholder ScanResult for error states
         */
        fun empty(): ScanResult = ScanResult(
            tankHealth = "Unknown",
            summary = "Unable to analyze the image.",
            identifications = emptyList(),
            recommendations = listOf(
                "Try taking a clearer photo",
                "Ensure good lighting",
                "Get closer to subjects of interest"
            ),
            name = "Unknown",
            category = "Unknown",
            confidence = 0,
            isProblem = false,
            description = "Unable to identify the subject in the image."
        )
        
        /**
         * Creates a sample ScanResult for testing/preview
         */
        fun sample(): ScanResult = ScanResult(
            tankHealth = "Good",
            summary = "Healthy reef tank with thriving clownfish and bubble tip anemone. Minor algae growth detected.",
            identifications = listOf(
                Identification(
                    name = "Ocellaris Clownfish (Amphiprion ocellaris)",
                    category = "Fish",
                    confidence = 95,
                    isProblem = false,
                    severity = null,
                    description = "Healthy clownfish showing good coloration and activity."
                ),
                Identification(
                    name = "Bubble Tip Anemone (Entacmaea quadricolor)",
                    category = "Invertebrate",
                    confidence = 90,
                    isProblem = false,
                    severity = null,
                    description = "Healthy BTA with extended tentacles showing characteristic bulbous tips."
                )
            ),
            recommendations = listOf(
                "Continue current feeding schedule",
                "Monitor water parameters weekly",
                "Consider adding flow to prevent detritus buildup"
            ),
            name = "Ocellaris Clownfish",
            category = "Fish",
            confidence = 95,
            isProblem = false,
            description = "Healthy reef tank with clownfish and anemone."
        )
        
        /**
         * Creates a sample problem ScanResult for testing
         */
        fun sampleProblem(): ScanResult = ScanResult(
            tankHealth = "Needs Attention",
            summary = "Multiple issues detected including pest anemone and nuisance algae outbreak.",
            identifications = listOf(
                Identification(
                    name = "Aiptasia Anemone",
                    category = "Pest",
                    confidence = 92,
                    isProblem = true,
                    severity = "Medium",
                    description = "Pest anemone that can spread rapidly and sting corals."
                ),
                Identification(
                    name = "Cyanobacteria (Red Slime Algae)",
                    category = "Algae",
                    confidence = 88,
                    isProblem = true,
                    severity = "Medium",
                    description = "Bacterial bloom indicating excess nutrients or low flow."
                ),
                Identification(
                    name = "Green Hair Algae",
                    category = "Algae",
                    confidence = 85,
                    isProblem = true,
                    severity = "Low",
                    description = "Nuisance algae often caused by high phosphates."
                )
            ),
            recommendations = listOf(
                "Add Peppermint Shrimp or use Aiptasia-X for pest anemone",
                "Increase flow and reduce feeding to combat cyano",
                "Test phosphates and consider GFO reactor",
                "Manual removal of hair algae during water changes"
            ),
            name = "Aiptasia Anemone",
            category = "Pest",
            confidence = 92,
            isProblem = true,
            severity = "Medium",
            description = "Multiple issues detected in tank."
        )
    }
}

/**
 * Represents a single identified organism or issue
 */
@JsonClass(generateAdapter = true)
data class Identification(
    /**
     * Name of the identified organism/issue with scientific name if known
     */
    @Json(name = "name")
    val name: String,
    
    /**
     * Category classification
     */
    @Json(name = "category")
    val category: String,
    
    /**
     * Confidence percentage (0-100)
     */
    @Json(name = "confidence")
    val confidence: Int,
    
    /**
     * Whether this is a problem/concern
     */
    @Json(name = "is_problem")
    val isProblem: Boolean,
    
    /**
     * Severity if problem (Low/Medium/High)
     */
    @Json(name = "severity")
    val severity: String? = null,
    
    /**
     * Description with key identifying features
     */
    @Json(name = "description")
    val description: String
) {
    fun getIssueStatus(): IssueStatus {
        return when {
            !isProblem -> IssueStatus.HEALTHY
            severity == "High" -> IssueStatus.PROBLEM
            severity == "Medium" -> IssueStatus.WARNING
            severity == "Low" -> IssueStatus.WARNING
            isProblem -> IssueStatus.PROBLEM
            else -> IssueStatus.HEALTHY
        }
    }
    
    fun getCategoryType(): CategoryType {
        return CategoryType.fromString(category)
    }
}

/**
 * Category type enum for marine life classification
 */
enum class CategoryType(val displayName: String) {
    FISH("Fish"),
    SPS_CORAL("SPS Coral"),
    LPS_CORAL("LPS Coral"),
    SOFT_CORAL("Soft Coral"),
    INVERTEBRATE("Invertebrate"),
    ALGAE("Algae"),
    PEST("Pest"),
    DISEASE("Disease"),
    TANK_ISSUE("Tank Issue"),
    EQUIPMENT("Equipment"),
    UNKNOWN("Unknown");
    
    companion object {
        fun fromString(value: String): CategoryType {
            return entries.find { 
                it.displayName.equals(value, ignoreCase = true) ||
                it.name.equals(value.replace(" ", "_"), ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}
