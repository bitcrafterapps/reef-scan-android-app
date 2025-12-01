package com.example.reefscan.data.remote

import com.example.reefscan.data.model.ChatCompletionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for OpenAI Chat Completions API
 */
interface OpenAIApi {
    
    /**
     * Create a chat completion with vision support
     * Uses GPT-4o model for image analysis
     */
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<ChatCompletionResponse>
}

