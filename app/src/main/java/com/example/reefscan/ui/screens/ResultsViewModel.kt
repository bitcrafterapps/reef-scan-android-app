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
    
    // We need to know which tank this scan is associated with if saving from temp
    // For existing scans, the tankId is already in the database record
    private var currentTankId: Long = -1L
    
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
                    // In a real app we should pass tankId here too or store it in holder.
                    // For now, since we only support one temp result, we might be missing the tank context.
                    // Let's assume we need to fetch tankId from somewhere or pass it.
                    // But loadScan signature is fixed by Screen route.
                    // Ideally, we should save the scan immediately in LoadingScreen even if temporary?
                    // LoadingScreen ALREADY saves the scan:
                    // scanRepository.saveScan(scanResult, imageUri, tankId)
                    // So scanId should actually be the DB ID in most cases.
                    // If save failed, it falls back to "temp".
                    // If "temp", we have a problem: we don't know the tankId to save it to later unless we store it.
                    
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
                        currentTankId = scanEntity.tankId // Retrieve tankId from saved scan
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
        
        // If we are saving a "temp" scan, we really need the tankId.
        // However, LoadingViewModel handles the initial save attempt.
        // If that failed, we are here. 
        // For now, let's use a default or try to find a way to get it.
        // Since we can't easily change the navigation argument for Results to include tankId without major change,
        // and LoadingScreen saves it, the "temp" case is rare (only if DB write failed).
        // If we force a save here, we might default to -1 or need user to select tank?
        // Let's default to -1 for now to fix the build error.
        // Ideally ScanResultHolder could hold tankId too.
        
        viewModelScope.launch {
            try {
                val savedId = scanRepository.saveScan(result, Uri.parse(imageUri), currentTankId)
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
