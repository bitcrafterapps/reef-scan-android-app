package com.example.reefscan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.reefscan.data.model.Identification
import com.example.reefscan.data.model.ScanResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Room entity for storing scan results locally
 */
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Path to the saved image file in internal storage
     */
    val imagePath: String,
    
    /**
     * Overall tank health assessment
     */
    val tankHealth: String = "Unknown",
    
    /**
     * Summary of findings
     */
    val summary: String = "",
    
    /**
     * JSON string of identifications list
     */
    val identificationsJson: String = "[]",
    
    /**
     * Identified name of the marine life (legacy/primary)
     */
    val name: String,
    
    /**
     * Category classification (legacy/primary)
     */
    val category: String,
    
    /**
     * Confidence percentage (0-100)
     */
    val confidence: Int,
    
    /**
     * Whether this is a problem/concern
     */
    val isProblem: Boolean,
    
    /**
     * Severity level (Low/Medium/High or null)
     */
    val severity: String?,
    
    /**
     * Description of the identified subject
     */
    val description: String,
    
    /**
     * JSON string of recommendations list
     */
    val recommendationsJson: String,
    
    /**
     * Timestamp when the scan was performed
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    
    /**
     * Convert recommendations JSON back to list
     */
    fun getRecommendationsList(): List<String> {
        return try {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(type)
            adapter.fromJson(recommendationsJson) ?: emptyList()
        } catch (e: Exception) {
            // Fallback to manual parsing for legacy data
            try {
                recommendationsJson
                    .removeSurrounding("[", "]")
                    .split("\",\"")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Convert identifications JSON back to list
     */
    fun getIdentificationsList(): List<Identification> {
        return try {
            if (identificationsJson.isBlank() || identificationsJson == "[]") {
                return emptyList()
            }
            val type = Types.newParameterizedType(List::class.java, Identification::class.java)
            val adapter = moshi.adapter<List<Identification>>(type)
            adapter.fromJson(identificationsJson) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Convert to ScanResult domain model
     */
    fun toScanResult(): ScanResult {
        return ScanResult(
            tankHealth = tankHealth,
            summary = summary,
            identifications = getIdentificationsList(),
            recommendations = getRecommendationsList(),
            name = name,
            category = category,
            confidence = confidence,
            isProblem = isProblem,
            severity = severity,
            description = description
        )
    }
    
    /**
     * Get count of problems found
     */
    fun getProblemCount(): Int {
        return getIdentificationsList().count { it.isProblem }
    }
    
    /**
     * Get count of healthy items found
     */
    fun getHealthyCount(): Int {
        return getIdentificationsList().count { !it.isProblem }
    }
    
    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        
        /**
         * Create ScanEntity from ScanResult
         */
        fun fromScanResult(
            scanResult: ScanResult,
            imagePath: String
        ): ScanEntity {
            // Serialize recommendations
            val recommendationsType = Types.newParameterizedType(List::class.java, String::class.java)
            val recommendationsAdapter = moshi.adapter<List<String>>(recommendationsType)
            val recommendationsJson = recommendationsAdapter.toJson(scanResult.recommendations)
            
            // Serialize identifications
            val identificationsType = Types.newParameterizedType(List::class.java, Identification::class.java)
            val identificationsAdapter = moshi.adapter<List<Identification>>(identificationsType)
            val identificationsJson = identificationsAdapter.toJson(scanResult.identifications)
            
            // Get primary identification for legacy fields
            val primary = scanResult.getPrimaryIdentification()
            
            return ScanEntity(
                imagePath = imagePath,
                tankHealth = scanResult.tankHealth,
                summary = scanResult.summary,
                identificationsJson = identificationsJson,
                name = primary?.name ?: scanResult.name,
                category = primary?.category ?: scanResult.category,
                confidence = primary?.confidence ?: scanResult.confidence,
                isProblem = primary?.isProblem ?: scanResult.isProblem,
                severity = primary?.severity ?: scanResult.severity,
                description = primary?.description ?: scanResult.description,
                recommendationsJson = recommendationsJson
            )
        }
    }
}
