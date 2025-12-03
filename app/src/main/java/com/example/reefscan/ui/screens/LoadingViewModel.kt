package com.example.reefscan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reefscan.billing.SubscriptionTier
import com.example.reefscan.billing.UsageData
import com.example.reefscan.billing.UsageTracker
import com.example.reefscan.data.local.ScanRepository
import com.example.reefscan.data.model.ScanResult
import com.example.reefscan.data.remote.ApiException
import com.example.reefscan.data.remote.GeminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the loading screen
 */
sealed class LoadingState {
    data object Analyzing : LoadingState()
    data class Success(val scanId: String, val scanResult: ScanResult) : LoadingState()
    data class Error(val message: String) : LoadingState()
    
    /**
     * User has reached their daily scan limit
     */
    data class UsageLimitReached(
        val usageData: UsageData,
        val tier: SubscriptionTier
    ) : LoadingState()
}

/**
 * ViewModel for the loading/analyzing screen
 * Uses Gemini 2.0 Flash for cost-effective image analysis
 * Integrates with UsageTracker for subscription tier limits
 */
class LoadingViewModel(
    private val geminiRepository: GeminiRepository,
    private val scanRepository: ScanRepository,
    private val usageTracker: UsageTracker
) : ViewModel() {
    
    private val _state = MutableStateFlow<LoadingState>(LoadingState.Analyzing)
    val state: StateFlow<LoadingState> = _state.asStateFlow()
    
    // Store the last scan result for retrieval
    private var lastScanResult: ScanResult? = null
    private var lastImageUri: Uri? = null
    
    /**
     * Get current usage data
     */
    val usageData: UsageData
        get() = usageTracker.currentUsage
    
    /**
     * Check if user can perform a scan
     */
    fun canScan(): Boolean = usageTracker.canScan()
    
    /**
     * Get remaining scans for today
     */
    fun getRemainingScans(): Int = usageTracker.getRemainingScans()
    
    /**
     * Analyze an image using Gemini 2.5 Flash Vision
     * Checks usage limits before proceeding
     */
    fun analyzeImage(imageUri: Uri, mode: String = "COMPREHENSIVE", tankId: Long) {
        // First check if user has reached their daily limit
        if (!usageTracker.canScan()) {
            _state.value = LoadingState.UsageLimitReached(
                usageData = usageTracker.currentUsage,
                tier = usageTracker.getCurrentTier()
            )
            return
        }
        
        lastImageUri = imageUri
        _state.value = LoadingState.Analyzing
        
        viewModelScope.launch {
            val result = geminiRepository.analyzeImage(imageUri, mode)
            
            result.fold(
                onSuccess = { scanResult ->
                    // Increment usage count on successful scan
                    usageTracker.incrementScanCount()
                    
                    lastScanResult = scanResult
                    // Save temporarily and get ID
                    val savedId = scanRepository.saveScan(scanResult, imageUri, tankId)
                    if (savedId != null) {
                        _state.value = LoadingState.Success(
                            scanId = savedId.toString(),
                            scanResult = scanResult
                        )
                    } else {
                        // If save failed, use "temp" as ID and store in memory
                        ScanResultHolder.currentResult = scanResult
                        ScanResultHolder.currentImageUri = imageUri.toString()
                        _state.value = LoadingState.Success(
                            scanId = "temp",
                            scanResult = scanResult
                        )
                    }
                },
                onFailure = { exception ->
                    // Don't count failed scans against usage
                    val errorMessage = when (exception) {
                        is ApiException -> {
                            when (exception.statusCode) {
                                401 -> "API key invalid. Please check your configuration."
                                429 -> "Rate limit exceeded. Please try again later."
                                500, 502, 503 -> "Server error. Please try again."
                                else -> exception.message ?: "Unknown error occurred"
                            }
                        }
                        else -> exception.message ?: "Failed to analyze image"
                    }
                    _state.value = LoadingState.Error(errorMessage)
                }
            )
        }
    }
    
    /**
     * Reset state to analyzing (for retry)
     */
    fun resetState() {
        _state.value = LoadingState.Analyzing
    }
}

/**
 * Temporary holder for scan results when not saved to database
 */
object ScanResultHolder {
    var currentResult: ScanResult? = null
    var currentImageUri: String? = null
}

/**
 * Factory for creating LoadingViewModel with dependencies
 */
class LoadingViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoadingViewModel::class.java)) {
            return LoadingViewModel(
                geminiRepository = GeminiRepository(context),
                scanRepository = ScanRepository(context),
                usageTracker = UsageTracker.getInstance(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
