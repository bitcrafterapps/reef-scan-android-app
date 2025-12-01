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
 * Singleton service for creating and managing the OpenAI API client
 */
object OpenAIService {
    
    private const val BASE_URL = "https://api.openai.com/"
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
     * OkHttp client with auth header and logging
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
                val apiKey = getApiKey()
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
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
     * OpenAI API interface implementation
     */
    val api: OpenAIApi by lazy {
        retrofit.create(OpenAIApi::class.java)
    }
    
    /**
     * Get the OpenAI API key from BuildConfig
     * The key should be set in local.properties as OPENAI_API_KEY=your_key
     */
    private fun getApiKey(): String {
        return try {
            BuildConfig::class.java.getField("OPENAI_API_KEY").get(null) as? String ?: ""
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

