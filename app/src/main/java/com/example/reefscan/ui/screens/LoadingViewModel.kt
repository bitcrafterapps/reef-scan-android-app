package com.example.reefscan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reefscan.data.local.ScanRepository
import com.example.reefscan.data.model.ScanResult
import com.example.reefscan.data.remote.ApiException
import com.example.reefscan.data.remote.OpenAIRepository
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
}

/**
 * ViewModel for the loading/analyzing screen
 */
class LoadingViewModel(
    private val openAIRepository: OpenAIRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow<LoadingState>(LoadingState.Analyzing)
    val state: StateFlow<LoadingState> = _state.asStateFlow()
    
    // Store the last scan result for retrieval
    private var lastScanResult: ScanResult? = null
    private var lastImageUri: Uri? = null
    
    /**
     * Analyze an image using GPT-4 Vision
     */
    fun analyzeImage(imageUri: Uri, mode: String = "COMPREHENSIVE", tankId: Long) {
        lastImageUri = imageUri
        _state.value = LoadingState.Analyzing
        
        viewModelScope.launch {
            val result = openAIRepository.analyzeImage(imageUri, mode)
            
            result.fold(
                onSuccess = { scanResult ->
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
                openAIRepository = OpenAIRepository(context),
                scanRepository = ScanRepository(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
