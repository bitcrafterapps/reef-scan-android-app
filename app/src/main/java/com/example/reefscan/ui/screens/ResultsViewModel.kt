package com.example.reefscan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reefscan.data.local.ScanRepository
import com.example.reefscan.data.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the results screen
 */
sealed class ResultsState {
    data object Loading : ResultsState()
    data class Success(
        val scanResult: ScanResult,
        val imageUri: String?,
        val isSaved: Boolean = true
    ) : ResultsState()
    data class Error(val message: String) : ResultsState()
}

/**
 * ViewModel for the results screen
 */
class ResultsViewModel(
    private val context: Context,
    private val scanRepository: ScanRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow<ResultsState>(ResultsState.Loading)
    val state: StateFlow<ResultsState> = _state.asStateFlow()
    
    private var currentScanId: String? = null
    private var currentImageUri: String? = null
    private var currentScanResult: ScanResult? = null
    
    /**
     * Load scan data by ID
     */
    fun loadScan(scanId: String) {
        currentScanId = scanId
        _state.value = ResultsState.Loading
        
        viewModelScope.launch {
            try {
                if (scanId == "temp") {
                    // Load from temporary holder
                    val tempResult = ScanResultHolder.currentResult
                    val tempUri = ScanResultHolder.currentImageUri
                    
                    if (tempResult != null) {
                        currentScanResult = tempResult
                        currentImageUri = tempUri
                        _state.value = ResultsState.Success(
                            scanResult = tempResult,
                            imageUri = tempUri,
                            isSaved = false
                        )
                    } else {
                        _state.value = ResultsState.Error("Scan result not found")
                    }
                } else {
                    // Load from database
                    val scanEntity = scanRepository.getScanById(scanId.toLongOrNull() ?: -1)
                    
                    if (scanEntity != null) {
                        val scanResult = scanEntity.toScanResult()
                        currentScanResult = scanResult
                        currentImageUri = scanEntity.imagePath
                        _state.value = ResultsState.Success(
                            scanResult = scanResult,
                            imageUri = scanEntity.imagePath,
                            isSaved = true
                        )
                    } else {
                        _state.value = ResultsState.Error("Scan not found in database")
                    }
                }
            } catch (e: Exception) {
                _state.value = ResultsState.Error(e.message ?: "Failed to load scan")
            }
        }
    }
    
    /**
     * Save the current scan to database
     */
    fun saveScan() {
        val result = currentScanResult ?: return
        val imageUri = currentImageUri ?: return
        
        viewModelScope.launch {
            try {
                val savedId = scanRepository.saveScan(result, Uri.parse(imageUri))
                if (savedId != null) {
                    currentScanId = savedId.toString()
                    // Clear the temp holder
                    ScanResultHolder.currentResult = null
                    ScanResultHolder.currentImageUri = null
                    
                    // Update state to show saved
                    _state.value = ResultsState.Success(
                        scanResult = result,
                        imageUri = imageUri,
                        isSaved = true
                    )
                }
            } catch (e: Exception) {
                // Keep current state, save failed silently
            }
        }
    }
}

/**
 * Factory for creating ResultsViewModel with dependencies
 */
class ResultsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResultsViewModel::class.java)) {
            return ResultsViewModel(
                context = context,
                scanRepository = ScanRepository(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

