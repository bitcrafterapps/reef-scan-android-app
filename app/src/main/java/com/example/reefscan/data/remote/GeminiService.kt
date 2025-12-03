package com.example.reefscan.data.remote

import com.example.reefscan.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton service for creating and managing the Gemini API client
 * Uses Gemini 2.0 Flash for cost-effective image analysis
 */
object GeminiService {
    
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val TIMEOUT_SECONDS = 60L
    
    /**
     * Moshi instance for JSON serialization/deserialization
     */
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
    
    /**
     * OkHttp client with logging (no auth header - Gemini uses query param)
     */
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Retrofit instance
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Gemini API interface implementation
     */
    val api: GeminiApi by lazy {
        retrofit.create(GeminiApi::class.java)
    }
    
    /**
     * Get the Gemini API key from BuildConfig
     * The key should be set in local.properties as GEMINI_API_KEY=your_key
     */
    fun getApiKey(): String {
        return try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
        } catch (e: Exception) {
            // Fallback: return empty string if not configured
            ""
        }
    }
    
    /**
     * Check if the API key is configured
     */
    fun isApiKeyConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }
}

