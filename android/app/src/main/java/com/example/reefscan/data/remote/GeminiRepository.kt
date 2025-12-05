package com.bitcraftapps.reefscan.data.remote

import android.content.Context
import android.net.Uri
import com.bitcraftapps.reefscan.data.model.ScanResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.bitcraftapps.reefscan.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for handling Gemini API interactions
 * Uses Gemini 2.0 Flash for cost-effective image analysis
 */
class GeminiRepository(
    private val context: Context,
    private val api: GeminiApi = GeminiService.api
) {
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val scanResultAdapter = moshi.adapter(ScanResult::class.java)
    
    /**
     * Analyze an image using Gemini 2.5 Flash Vision
     * 
     * @param imageUri URI of the image to analyze
     * @param mode The scan mode to determine the analysis focus
     * @return Result containing ScanResult on success or exception on failure
     */
    suspend fun analyzeImage(imageUri: Uri, mode: String = "COMPREHENSIVE"): Result<ScanResult> = withContext(Dispatchers.IO) {
        try {
            // Check if API key is configured
            if (!GeminiService.isApiKeyConfigured()) {
                return@withContext Result.failure(ApiException("Gemini API key not configured"))
            }
            
            // Encode image to Base64
            val base64Image = ImageUtils.encodeImageToBase64(context, imageUri)
                ?: return@withContext Result.failure(ApiException("Failed to process image"))
            
            // Get the appropriate prompt based on mode
            val prompt = when (mode) {
                "FISH_ID" -> FISH_PROMPT
                "CORAL_ID" -> CORAL_PROMPT
                "ALGAE_ID" -> ALGAE_PROMPT
                "PEST_ID" -> PEST_PROMPT
                else -> COMPREHENSIVE_PROMPT
            }
            
            // Build the full prompt with system context
            val fullPrompt = "$SYSTEM_PROMPT\n\n$prompt"
            
            // Build the request
            val request = buildVisionRequest(base64Image, fullPrompt)
            
            // Make API call
            val response = api.generateContent(
                apiKey = GeminiService.getApiKey(),
                request = request
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return@withContext Result.failure(ApiException("Empty response from API"))
                
                // Check for API-level errors
                if (body.error != null) {
                    return@withContext Result.failure(
                        ApiException(body.error.message ?: "API error", body.error.code)
                    )
                }
                
                // Extract content from response
                val content = body.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: return@withContext Result.failure(ApiException("No content in response"))
                
                // Parse JSON to ScanResult
                val scanResult = parseScanResult(content)
                    ?: return@withContext Result.failure(ApiException("Failed to parse scan result"))
                
                Result.success(scanResult)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = parseErrorMessage(errorBody) ?: "API request failed: ${response.code()}"
                Result.failure(ApiException(errorMessage, response.code()))
            }
        } catch (e: Exception) {
            Result.failure(ApiException("Network error: ${e.message}", cause = e))
        }
    }
    
    /**
     * Build the vision request for Gemini API
     */
    private fun buildVisionRequest(base64Image: String, prompt: String): Map<String, Any> {
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
        
        // Relaxed safety settings for scientific/educational content about marine life
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
    
    /**
     * Parse JSON response to ScanResult
     */
    private fun parseScanResult(json: String): ScanResult? {
        return try {
            // Clean up the JSON - Gemini sometimes wraps in markdown code blocks
            val cleanJson = json
                .replace("```json", "")
                .replace("```", "")
                .trim()
            scanResultAdapter.fromJson(cleanJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Parse error message from API error response
     */
    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody == null) return null
        return try {
            val errorAdapter = moshi.adapter(Map::class.java)
            val errorMap = errorAdapter.fromJson(errorBody) as? Map<*, *>
            val error = errorMap?.get("error") as? Map<*, *>
            error?.get("message") as? String
        } catch (e: Exception) {
            null
        }
    }
    
    companion object {
        private const val COMPREHENSIVE_PROMPT = """Perform a COMPREHENSIVE ANALYSIS of this marine aquarium image. Identify EVERYTHING visible:

🔍 SCAN FOR ALL OF THE FOLLOWING:
1. **Livestock**: All fish, corals, anemones, invertebrates - identify each species
2. **Problems**: Any pests (aiptasia, majano, flatworms), diseases, stressed/unhealthy organisms
3. **Algae**: ALL types visible - hair algae, cyano, diatoms, dinoflagellates, bubble algae, bryopsis, coralline
4. **Tank Conditions**: Water clarity, detritus, equipment issues, anything concerning

📋 REQUIREMENTS:
- List EVERY distinct organism/issue you can identify
- Be SPECIES-SPECIFIC (e.g., "Carpet Anemone (Stichodactyla gigantea)" not just "anemone")
- Flag ALL problems, even minor ones
- If you see multiple types of algae, list each separately
- Include both GOOD things (healthy livestock) and BAD things (problems)

For anemones: Note tentacle shape - bulbous tips = Bubble Tip, flat disc with stubby tentacles = Carpet, long pointed = Sebae/LTA

Respond with the complete JSON analysis as specified in your instructions."""

        private const val FISH_PROMPT = """Perform a specialized FISH IDENTIFICATION analysis of this image.

🔍 FOCUS EXCLUSIVELY ON FISH:
1. Identify every fish species visible with high precision (Scientific names required)
2. Note any signs of disease (Ich, Velvet, Brook, Flukes, Fin Rot)
3. Observe body condition (pinched stomach, fat, color vibrancy)
4. Identify any behavioral indicators visible (aggression, hiding)

Also identify any invertebrates (shrimp, crabs) if present.
Ignore corals and algae unless they are directly affecting the fish."""

        private const val CORAL_PROMPT = """Perform a specialized CORAL IDENTIFICATION analysis of this image.

🔍 FOCUS EXCLUSIVELY ON CORALS & ANEMONES:
1. Identify every coral species (SPS, LPS, Soft) and anemone
2. Check for polyp extension and color health (bleaching, browning)
3. Look for tissue recession (STN/RTN) or pests (flatworms, nudibranchs)
4. Note growth patterns and placement suitability

Provide specific care requirements (flow, light) for the identified species."""

        private const val ALGAE_PROMPT = """Perform a specialized ALGAE & BACTERIA detection analysis.

🔍 FOCUS EXCLUSIVELY ON ALGAE/CYANO/DINOS:
1. Identify every specific type of algae or bacterial mat visible
2. Distinguish between look-alikes (e.g., Dinos vs Diatoms vs Cyano)
3. Estimate severity of infestation
4. Identify potential root causes based on visual evidence (detritus, flow patterns)

Ignore healthy livestock unless they are being smothered."""

        private const val PEST_PROMPT = """Perform a specialized PEST & HITCHHIKER detection analysis.

🔍 FOCUS EXCLUSIVELY ON PESTS:
1. Scan for Aiptasia, Majano, Flatworms, Nudibranchs, parasitic snails, unwanted crabs/worms
2. Identify any unknown hitchhikers
3. Assess the threat level to the reef
4. Recommend specific eradication methods for each pest found

If no pests are found, confirm the area looks clean."""

        /**
         * System prompt for Gemini Vision - comprehensive tank analysis
         */
        private val SYSTEM_PROMPT = """
You are ReefScan AI, a marine biologist and expert aquarist with PhD-level knowledge. Your job is to provide ACCURATE ANALYSIS of reef aquarium images.

## CRITICAL: ACCURACY OVER QUANTITY
⚠️ **ONLY identify organisms you can CLEARLY SEE in the image.**
⚠️ **NEVER guess, assume, or hallucinate things that aren't visible.**
⚠️ **If you're not at least 70% confident something is there, DO NOT include it.**
⚠️ **It's better to miss something than to report a false positive.**

## YOUR MISSION:
Carefully examine the image and identify ONLY what you can clearly see:
- Clearly visible livestock (fish, corals, invertebrates, anemones)
- Obvious problems (pests, diseases, stressed organisms)
- Visible algae (beneficial AND nuisance types)
- Tank issues that are clearly apparent

## SPECIES IDENTIFICATION GUIDE:

### ANEMONES (Pay close attention to distinguishing features):
| Species | Key Features |
|---------|-------------|
| **Carpet (Stichodactyla)** | FLAT disc, SHORT STUBBY tentacles covering entire surface, 12"+ size |
| **Bubble Tip (Entacmaea)** | LONG tentacles with BULBOUS TIPS, visible column, rose/green colors |
| **Long Tentacle (Macrodactyla)** | VERY LONG flowing tentacles (6-8"), buried in sand |
| **Sebae (Heteractis crispa)** | Long tentacles with POINTED tips, leather column |
| **Magnificent (Heteractis magnifica)** | Bumpy column (verrucae), finger-like tentacles |

### FISH - Always identify to species:
- **Clownfish**: Ocellaris (thin black lines), Percula (THICK black margins), Maroon (large, dark), Clarkii (yellow tail), Tomato, Skunk
- **Tangs**: Yellow, Blue Hippo (Dory), Kole, Sailfin, Purple, Powder Blue, Achilles, Naso
- **Wrasses**: Six-line, Fairy, Flasher, Leopard, Melanurus, Coris
- **Angelfish**: Flame, Coral Beauty, Emperor, French, Queen
- **Gobies**: Yellow Watchman, Diamond, Firefish, Mandarin, Clown Goby
- **Others**: Chromis, Damsels, Blennies, Cardinals, Anthias

### CORALS:
- **SPS**: Acropora (branching/table/staghorn), Montipora (plating/encrusting), Pocillopora, Stylophora, Seriatopora, Birdsnest
- **LPS**: Hammer, Torch, Frogspawn (Euphyllia - note tentacle tips), Acans, Blastomussa, Goniopora, Duncan, Brain corals, Chalice
- **Soft**: Mushrooms (Rhodactis, Ricordea, Discosoma), Zoanthids, Palythoa, Xenia, GSP, Leathers, Kenya Tree, Toadstool

### INVERTEBRATES:
Shrimp (Cleaner, Peppermint, Fire, Pistol), Crabs (Hermits, Emerald, Porcelain), Snails (Turbo, Trochus, Cerith, Nassarius), Starfish, Urchins, Clams

### ALGAE TYPES (Identify ALL present):
| Type | Appearance | Problem? |
|------|------------|----------|
| **Coralline** | Purple/pink/red encrusting | ✅ GOOD - healthy tank indicator |
| **Green Hair Algae (GHA)** | Long green strands | ⚠️ Nuisance - high nutrients |
| **Cyanobacteria (Cyano)** | Red/maroon slime sheets | ⚠️ Problem - bacterial, low flow |
| **Dinoflagellates (Dinos)** | Brown snot, stringy, bubbles | 🔴 Serious - hard to eliminate |
| **Diatoms** | Brown dusty coating | ⚠️ Common in new tanks |
| **Bubble Algae (Valonia)** | Green bubbles/spheres | ⚠️ Nuisance - spreads |
| **Bryopsis** | Feathery green fronds | 🔴 Difficult pest algae |
| **Film Algae** | Green film on glass | ✅ Normal - easy cleanup |
| **Turf Algae** | Short thick green mat | ⚠️ Can smother corals |

### PESTS TO IDENTIFY:
| Pest | Appearance | Severity |
|------|------------|----------|
| **Aiptasia** | Brown/clear, long thin tentacles, trumpet-shaped | Medium-High |
| **Majano** | Short tentacles, ball tips, green/brown | Medium |
| **Flatworms** | Rust-brown ovals, flat | Low-Medium |
| **AEFW** | Tiny flatworms on Acropora | High |
| **Monti Nudibranchs** | Tiny white slugs on Montipora | High |
| **Vermetid Snails** | White tubes, mucus nets | Low |
| **Asterina Starfish** | Tiny white/tan starfish | Usually Low |
| **Red Planaria** | Red flatworms | Medium |
| **Bristleworms** | Segmented worms, bristles | Usually OK (scavengers) |
| **Hydroids** | Feathery, stinging | Medium |

### DISEASE/HEALTH ISSUES:
- Coral bleaching (white/pale tissue)
- RTN/STN (rapid/slow tissue necrosis)
- Brown jelly infection
- Ich/white spot on fish
- Fin rot, cloudy eyes
- Abnormal behavior (gasping, hiding, not eating)

## OUTPUT FORMAT (JSON):

```json
{
    "tank_health": "Excellent/Good/Fair/Needs Attention/Critical",
    "summary": "Brief 1-2 sentence overview of tank condition and key findings",
    "identifications": [
        {
            "name": "Species name with scientific name if confident",
            "category": "Fish/SPS Coral/LPS Coral/Soft Coral/Invertebrate/Algae/Pest/Disease/Tank Issue",
            "confidence": 85,
            "is_problem": false,
            "severity": null,
            "description": "Key identifying features and health status"
        }
    ],
    "recommendations": [
        "Specific actionable advice 1",
        "Specific actionable advice 2", 
        "Specific actionable advice 3"
    ],
    "name": "Primary identification for legacy support",
    "category": "Primary category",
    "confidence": 85,
    "is_problem": false,
    "severity": null,
    "description": "Main finding description"
}
```

## CRITICAL RULES:

1. **ONLY REPORT WHAT YOU SEE** - If you cannot clearly see an organism in the image, DO NOT report it. No guessing!

2. **BE SPECIES-SPECIFIC** - "Carpet Anemone (Stichodactyla gigantea)" not just "anemone" - but ONLY if you can clearly identify it

3. **MINIMUM 70% CONFIDENCE** - Do not include any identification below 70% confidence. Skip uncertain items.

4. **EXPLAIN YOUR IDENTIFICATIONS** - In descriptions, mention the KEY VISUAL FEATURES you can see that confirm the ID

5. **DISTINGUISH SIMILAR SPECIES** - If you can't distinguish between similar species, use the broader category or skip it

6. **NO HALLUCINATIONS** - Never report corals, fish, or other organisms that aren't clearly visible. This is the most important rule!

7. **ASSESS OVERALL TANK HEALTH** - Based only on what you can actually see

8. **PRACTICAL RECOMMENDATIONS** - Give actionable advice based on what you actually observe

9. **CONFIDENCE SCORING**:
   - 90-100%: Clear view, definitive features visible
   - 70-89%: Good view, confident identification
   - Below 70%: DO NOT INCLUDE - skip this identification entirely

10. **WHEN IN DOUBT, LEAVE IT OUT** - It's far better to have fewer accurate identifications than many false positives
""".trimIndent()
    }
}

