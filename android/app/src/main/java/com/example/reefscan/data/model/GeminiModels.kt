package com.bitcraftapps.reefscan.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ============================================================================
// Gemini API Request Models
// https://ai.google.dev/api/generate-content
// ============================================================================

/**
 * Request body for Gemini generateContent API with vision support
 */
@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents")
    val contents: List<GeminiContent>,
    
    @Json(name = "generationConfig")
    val generationConfig: GeminiGenerationConfig? = null,
    
    @Json(name = "safetySettings")
    val safetySettings: List<GeminiSafetySetting>? = null
)

/**
 * Content block containing parts (text, images, etc.)
 */
@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role")
    val role: String? = null, // "user" or "model"
    
    @Json(name = "parts")
    val parts: List<GeminiPart>
)

/**
 * A part of the content - can be text or inline data (image)
 */
@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text")
    val text: String? = null,
    
    @Json(name = "inlineData")
    val inlineData: GeminiInlineData? = null
)

/**
 * Inline data for images/media
 */
@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType")
    val mimeType: String, // e.g., "image/jpeg"
    
    @Json(name = "data")
    val data: String // Base64-encoded image data
)

/**
 * Generation configuration for the model
 */
@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature")
    val temperature: Double? = 0.2,
    
    @Json(name = "topP")
    val topP: Double? = null,
    
    @Json(name = "topK")
    val topK: Int? = null,
    
    @Json(name = "maxOutputTokens")
    val maxOutputTokens: Int? = 2048,
    
    @Json(name = "responseMimeType")
    val responseMimeType: String? = "application/json"
)

/**
 * Safety settings for content filtering
 */
@JsonClass(generateAdapter = true)
data class GeminiSafetySetting(
    @Json(name = "category")
    val category: String,
    
    @Json(name = "threshold")
    val threshold: String
)

// ============================================================================
// Gemini API Response Models
// ============================================================================

/**
 * Response from Gemini generateContent API
 */
@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates")
    val candidates: List<GeminiCandidate>?,
    
    @Json(name = "usageMetadata")
    val usageMetadata: GeminiUsageMetadata?,
    
    @Json(name = "error")
    val error: GeminiError? = null
)

/**
 * A candidate response from the model
 */
@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content")
    val content: GeminiContent?,
    
    @Json(name = "finishReason")
    val finishReason: String?,
    
    @Json(name = "index")
    val index: Int?,
    
    @Json(name = "safetyRatings")
    val safetyRatings: List<GeminiSafetyRating>?
)

/**
 * Safety rating for generated content
 */
@JsonClass(generateAdapter = true)
data class GeminiSafetyRating(
    @Json(name = "category")
    val category: String,
    
    @Json(name = "probability")
    val probability: String
)

/**
 * Token usage metadata
 */
@JsonClass(generateAdapter = true)
data class GeminiUsageMetadata(
    @Json(name = "promptTokenCount")
    val promptTokenCount: Int?,
    
    @Json(name = "candidatesTokenCount")
    val candidatesTokenCount: Int?,
    
    @Json(name = "totalTokenCount")
    val totalTokenCount: Int?
)

/**
 * Error response from Gemini API
 */
@JsonClass(generateAdapter = true)
data class GeminiError(
    @Json(name = "code")
    val code: Int?,
    
    @Json(name = "message")
    val message: String?,
    
    @Json(name = "status")
    val status: String?
)

// ============================================================================
// Helper Object for Building Gemini Requests
// ============================================================================

/**
 * Helper object to build Gemini requests for reef scanning
 */
object GeminiRequestBuilder {
    
    /**
     * Build a Gemini request with image for reef scanning
     * 
     * @param base64Image Base64-encoded image data (without data URI prefix)
     * @param prompt The analysis prompt to use
     * @return Map ready to send to Gemini API
     */
    fun buildRequest(base64Image: String, prompt: String): Map<String, Any> {
        val parts = listOf(
            mapOf("text" to prompt),
            mapOf(
                "inlineData" to mapOf(
                    "mimeType" to "image/jpeg",
                    "data" to base64Image
                )
            )
        )
        
        val contents = listOf(
            mapOf(
                "role" to "user",
                "parts" to parts
            )
        )
        
        val generationConfig = mapOf(
            "temperature" to 0.2,
            "maxOutputTokens" to 2048,
            "responseMimeType" to "application/json"
        )
        
        // Relaxed safety settings for scientific/educational content
        val safetySettings = listOf(
            mapOf("category" to "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold" to "BLOCK_NONE"),
            mapOf("category" to "HARM_CATEGORY_HARASSMENT", "threshold" to "BLOCK_ONLY_HIGH"),
            mapOf("category" to "HARM_CATEGORY_HATE_SPEECH", "threshold" to "BLOCK_ONLY_HIGH"),
            mapOf("category" to "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold" to "BLOCK_ONLY_HIGH")
        )
        
        return mapOf(
            "contents" to contents,
            "generationConfig" to generationConfig,
            "safetySettings" to safetySettings
        )
    }
}

