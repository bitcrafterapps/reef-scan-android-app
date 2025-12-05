package com.bitcraftapps.reefscan.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitcraftapps.reefscan.data.local.ScanRepository
import com.bitcraftapps.reefscan.data.local.TankEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeScreenViewModel(
    private val repository: ScanRepository
) : ViewModel() {

    private val _tank = MutableStateFlow<TankEntity?>(null)
    val tank: StateFlow<TankEntity?> = _tank
    
    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount
    
    private val _galleryCount = MutableStateFlow(0)
    val galleryCount: StateFlow<Int> = _galleryCount

    fun loadTank(tankId: Long) {
        viewModelScope.launch {
            _tank.value = repository.getTankById(tankId)
            _scanCount.value = repository.getScanCountForTank(tankId)
            _galleryCount.value = repository.getGalleryCountForTank(tankId)
        }
    }
    
    fun refreshCounts(tankId: Long) {
        viewModelScope.launch {
            _scanCount.value = repository.getScanCountForTank(tankId)
            _galleryCount.value = repository.getGalleryCountForTank(tankId)
        }
    }

    fun updateTank(
        id: Long,
        name: String,
        description: String,
        size: String,
        manufacturer: String,
        imageUri: Uri?,
        currentImagePath: String?
    ) {
        viewModelScope.launch {
            var finalImagePath = currentImagePath
            
            if (imageUri != null) {
                val newPath = repository.copyTankImage(imageUri)
                if (newPath != null) {
                    finalImagePath = newPath
                }
            }

            val updatedTank = TankEntity(
                id = id,
                name = name,
                description = description,
                size = size,
                manufacturer = manufacturer,
                imagePath = finalImagePath
            )
            repository.updateTank(updatedTank)
            loadTank(id) // Refresh
        }
    }

    fun deleteTank(tank: TankEntity) {
        viewModelScope.launch {
            repository.deleteTank(tank)
            // Navigation back should be handled by UI observing this or explicit callback
        }
    }
}

class HomeScreenViewModelFactory(private val repository: ScanRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeScreenViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeScreenViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

