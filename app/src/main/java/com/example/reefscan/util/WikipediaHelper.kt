package com.example.reefscan.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Helper class for Wikipedia API interactions
 * Uses the Wikipedia API to search for articles and get correct URLs
 */
object WikipediaHelper {
    
    private const val TAG = "WikipediaHelper"
    private const val WIKIPEDIA_API_BASE = "https://en.wikipedia.org/w/api.php"
    private const val WIKIPEDIA_MOBILE_BASE = "https://en.m.wikipedia.org/wiki/"
    
    /**
     * Search Wikipedia for an article and return the best matching URL
     * Falls back to a search URL if no exact match is found
     * 
     * @param query The search term (e.g., species name)
     * @return The Wikipedia URL for the article or search results
     */
    suspend fun getWikipediaUrl(query: String): String = withContext(Dispatchers.IO) {
        try {
            // Clean up the query - extract scientific name if present
            val searchTerm = extractBestSearchTerm(query)
            Log.d(TAG, "Searching Wikipedia for: $searchTerm")
            
            // First try to get an exact article match
            val articleTitle = searchForArticle(searchTerm)
            
            if (articleTitle != null) {
                val encodedTitle = URLEncoder.encode(articleTitle.replace(" ", "_"), "UTF-8")
                Log.d(TAG, "Found article: $articleTitle")
                return@withContext "$WIKIPEDIA_MOBILE_BASE$encodedTitle"
            }
            
            // If no exact match, try with the common name
            val commonName = extractCommonName(query)
            if (commonName != searchTerm) {
                val commonArticle = searchForArticle(commonName)
                if (commonArticle != null) {
                    val encodedTitle = URLEncoder.encode(commonArticle.replace(" ", "_"), "UTF-8")
                    Log.d(TAG, "Found article via common name: $commonArticle")
                    return@withContext "$WIKIPEDIA_MOBILE_BASE$encodedTitle"
                }
            }
            
            // Fall back to Wikipedia search results page
            val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
            Log.d(TAG, "No exact match, falling back to search")
            return@withContext "https://en.m.wikipedia.org/wiki/Special:Search?search=$encodedQuery"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Wikipedia", e)
            // Return a search URL as fallback
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            return@withContext "https://en.m.wikipedia.org/wiki/Special:Search?search=$encodedQuery"
        }
    }
    
    /**
     * Search Wikipedia API for an article matching the query
     * Returns the exact article title if found, null otherwise
     */
    private fun searchForArticle(query: String): String? {
        try {
            // Use Wikipedia's opensearch API for better results
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "$WIKIPEDIA_API_BASE?action=opensearch&search=$encodedQuery&limit=5&namespace=0&format=json"
            
            val response = URL(apiUrl).readText()
            val jsonArray = org.json.JSONArray(response)
            
            // opensearch returns: [query, [titles], [descriptions], [urls]]
            if (jsonArray.length() >= 2) {
                val titles = jsonArray.getJSONArray(1)
                if (titles.length() > 0) {
                    // Return the first (best) match
                    return titles.getString(0)
                }
            }
            
            // Try the search API as backup
            return searchWithQueryApi(query)
            
        } catch (e: Exception) {
            Log.e(TAG, "opensearch failed, trying query API", e)
            return searchWithQueryApi(query)
        }
    }
    
    /**
     * Backup search using Wikipedia's query API
     */
    private fun searchWithQueryApi(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "$WIKIPEDIA_API_BASE?action=query&list=search&srsearch=$encodedQuery&srlimit=1&format=json"
            
            val response = URL(apiUrl).readText()
            val json = JSONObject(response)
            
            val searchResults = json.optJSONObject("query")?.optJSONArray("search")
            if (searchResults != null && searchResults.length() > 0) {
                return searchResults.getJSONObject(0).optString("title")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Query API search failed", e)
        }
        return null
    }
    
    /**
     * Extract the best search term from a name
     * Prefers scientific name (in parentheses) as it's more specific
     */
    private fun extractBestSearchTerm(name: String): String {
        // Try to extract scientific name from parentheses
        val scientificRegex = "\\(([A-Z][a-z]+ [a-z]+)\\)".toRegex()
        val scientificMatch = scientificRegex.find(name)
        if (scientificMatch != null) {
            return scientificMatch.groupValues[1]
        }
        
        // Try generic parentheses content
        val parenRegex = "\\((.*?)\\)".toRegex()
        val parenMatch = parenRegex.find(name)
        if (parenMatch != null) {
            val content = parenMatch.groupValues[1]
            // Only use if it looks like a scientific name or useful term
            if (content.isNotBlank() && !content.contains("?")) {
                return content
            }
        }
        
        // Clean up and use the whole name
        return name
            .replace(Regex("\\(.*?\\)"), "") // Remove parenthetical content
            .replace(Regex("[^a-zA-Z\\s]"), "") // Remove special chars
            .trim()
    }
    
    /**
     * Extract just the common name (before parentheses)
     */
    private fun extractCommonName(name: String): String {
        return name
            .replace(Regex("\\(.*?\\)"), "")
            .trim()
    }
}

