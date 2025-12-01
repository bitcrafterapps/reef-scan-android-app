package com.example.reefscan.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ============================================================================
// OpenAI Chat Completions API Request Models
// https://platform.openai.com/docs/api-reference/chat/create
// ============================================================================

/**
 * Request body for OpenAI Chat Completions API with vision support
 */
@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    @Json(name = "model")
    val model: String = "gpt-4o", // Using GPT-4o for vision capabilities
    
    @Json(name = "messages")
    val messages: List<ChatMessage>,
    
    @Json(name = "max_tokens")
    val maxTokens: Int = 1024,
    
    @Json(name = "temperature")
    val temperature: Double = 0.3, // Lower temperature for more consistent results
    
    @Json(name = "response_format")
    val responseFormat: ResponseFormat? = ResponseFormat(type = "json_object")
)

/**
 * Response format specification for JSON mode
 */
@JsonClass(generateAdapter = true)
data class ResponseFormat(
    @Json(name = "type")
    val type: String = "json_object"
)

/**
 * Chat message in the conversation
 */
@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role")
    val role: String, // "system", "user", or "assistant"
    
    @Json(name = "content")
    val content: Any // Can be String or List<ContentPart> for vision
)

/**
 * Content part for multimodal messages (text + images)
 */
@JsonClass(generateAdapter = true)
data class ContentPart(
    @Json(name = "type")
    val type: String, // "text" or "image_url"
    
    @Json(name = "text")
    val text: String? = null,
    
    @Json(name = "image_url")
    val imageUrl: ImageUrl? = null
)

/**
 * Image URL object for vision API
 */
@JsonClass(generateAdapter = true)
data class ImageUrl(
    @Json(name = "url")
    val url: String, // Can be URL or base64 data URI
    
    @Json(name = "detail")
    val detail: String = "high" // "low", "high", or "auto"
)

// ============================================================================
// OpenAI Chat Completions API Response Models
// ============================================================================

/**
 * Response from OpenAI Chat Completions API
 */
@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    @Json(name = "id")
    val id: String,
    
    @Json(name = "object")
    val objectType: String,
    
    @Json(name = "created")
    val created: Long,
    
    @Json(name = "model")
    val model: String,
    
    @Json(name = "choices")
    val choices: List<Choice>,
    
    @Json(name = "usage")
    val usage: Usage?
)

/**
 * A choice/completion from the model
 */
@JsonClass(generateAdapter = true)
data class Choice(
    @Json(name = "index")
    val index: Int,
    
    @Json(name = "message")
    val message: ResponseMessage,
    
    @Json(name = "finish_reason")
    val finishReason: String?
)

/**
 * Response message from the assistant
 */
@JsonClass(generateAdapter = true)
data class ResponseMessage(
    @Json(name = "role")
    val role: String,
    
    @Json(name = "content")
    val content: String?
)

/**
 * Token usage information
 */
@JsonClass(generateAdapter = true)
data class Usage(
    @Json(name = "prompt_tokens")
    val promptTokens: Int,
    
    @Json(name = "completion_tokens")
    val completionTokens: Int,
    
    @Json(name = "total_tokens")
    val totalTokens: Int
)

// ============================================================================
// Error Response Models
// ============================================================================

/**
 * Error response from OpenAI API
 */
@JsonClass(generateAdapter = true)
data class OpenAIErrorResponse(
    @Json(name = "error")
    val error: OpenAIError
)

/**
 * Error details
 */
@JsonClass(generateAdapter = true)
data class OpenAIError(
    @Json(name = "message")
    val message: String,
    
    @Json(name = "type")
    val type: String,
    
    @Json(name = "param")
    val param: String?,
    
    @Json(name = "code")
    val code: String?
)

// ============================================================================
// Helper Objects for Building Requests
// ============================================================================

/**
 * Helper object to build chat completion requests for reef scanning
 */
object ReefScanRequestBuilder {
    
    /**
     * System prompt for GPT-4 Vision to analyze marine aquarium images
     */
    private val SYSTEM_PROMPT = """
You are ReefScan AI, an expert marine aquarium analyzer with extensive knowledge of:
- Marine fish species (both common and rare)
- Coral species (SPS, LPS, Soft corals)
- Invertebrates (shrimp, crabs, snails, starfish, etc.)
- Algae types (beneficial and nuisance: cyano, dinos, GHA, diatoms, coralline)
- Pests (aiptasia, flatworms, vermetid snails, hydroids, red bugs, AEFW)
- Diseases and coral problems (RTN, STN, bleaching, bacterial infections, brown jelly)
- Tank issues (cloudy water, surface film, equipment problems)

Analyze the provided aquarium image and identify what you see. Provide your response in the following JSON format:

{
    "name": "Common name of the identified organism/issue",
    "category": "One of: Fish, SPS Coral, LPS Coral, Soft Coral, Invertebrate, Algae, Pest, Disease, Tank Issue, Unknown",
    "confidence": 0-100 (integer percentage of identification confidence),
    "is_problem": true/false (whether this poses a concern for the aquarium),
    "severity": "Low/Medium/High or null if not a problem",
    "description": "Brief 1-2 sentence description of what was identified",
    "recommendations": ["First recommendation", "Second recommendation", "Third recommendation"]
}

Guidelines:
1. Be specific in identification when possible (e.g., "Ocellaris Clownfish" not just "Clownfish")
2. For problems, always assess severity and provide actionable recommendations
3. If unable to identify clearly, set confidence below 50 and category to "Unknown"
4. Recommendations should be practical and specific to reef keeping
5. Always return valid JSON matching the exact format above
""".trimIndent()
    
    /**
     * Builds a chat completion request with an image for reef scanning
     * 
     * @param base64Image Base64-encoded image data
     * @return ChatCompletionRequest ready to send to OpenAI API
     */
    fun buildRequest(base64Image: String): ChatCompletionRequest {
        val imageUrl = "data:image/jpeg;base64,$base64Image"
        
        val contentParts = listOf(
            mapOf("type" to "text", "text" to "Analyze this marine aquarium image and identify what you see. Provide the response in the specified JSON format."),
            mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl, "detail" to "high"))
        )
        
        val messages = listOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT),
            ChatMessage(role = "user", content = contentParts)
        )
        
        return ChatCompletionRequest(
            model = "gpt-4o",
            messages = messages,
            maxTokens = 1024,
            temperature = 0.3,
            responseFormat = ResponseFormat(type = "json_object")
        )
    }
}

