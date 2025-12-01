package com.example.reefscan.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reefscan.data.local.ScanRepository
import com.example.reefscan.data.local.TankEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TanksViewModel(
    private val repository: ScanRepository
) : ViewModel() {

    val tanks: StateFlow<List<TankEntity>> = repository.getAllTanks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTank(
        name: String,
        description: String,
        size: String,
        manufacturer: String,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            var imagePath: String? = null
            if (imageUri != null) {
                imagePath = repository.copyTankImage(imageUri)
            }

            val newTank = TankEntity(
                name = name,
                description = description,
                size = size,
                manufacturer = manufacturer,
                imagePath = imagePath
            )
            repository.saveTank(newTank)
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
                // New image selected, save it
                val newPath = repository.copyTankImage(imageUri)
                if (newPath != null) {
                    finalImagePath = newPath
                    // Optionally delete old image here if needed
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
        }
    }

    fun deleteTank(tank: TankEntity) {
        viewModelScope.launch {
            repository.deleteTank(tank)
        }
    }
}

class TanksViewModelFactory(private val repository: ScanRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TanksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TanksViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

