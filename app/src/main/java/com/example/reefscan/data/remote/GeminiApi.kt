package com.example.reefscan.data.remote

import com.example.reefscan.data.model.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for Google Gemini API
 * Using Gemini 2.0 Flash for cost-effective vision processing
 */
interface GeminiApi {
    
    /**
     * Generate content using Gemini 2.0 Flash with vision support
     * 
     * @param apiKey The Gemini API key passed as query parameter
     * @param request The request body containing content and generation config
     */
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<GeminiResponse>
}

