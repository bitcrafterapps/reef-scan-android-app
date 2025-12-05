package com.bitcraftapps.reefscan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitcraftapps.reefscan.data.local.ScanRepository
import com.bitcraftapps.reefscan.data.local.TankEntity
import com.bitcraftapps.reefscan.data.model.GalleryImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: ScanRepository,
    private val tankId: Long
) : ViewModel() {

    // Tank Info
    private val _tank = MutableStateFlow<TankEntity?>(null)
    val tank: StateFlow<TankEntity?> = _tank.asStateFlow()

    // List of (DateString, ThumbnailPath?)
    private val _folders = MutableStateFlow<List<Pair<String, String?>>>(emptyList())
    val folders: StateFlow<List<Pair<String, String?>>> = _folders.asStateFlow()

    // List of GalleryImages for a specific date
    private val _images = MutableStateFlow<List<GalleryImage>>(emptyList())
    val images: StateFlow<List<GalleryImage>> = _images.asStateFlow()
    
    // All images for this tank
    private val _allImages = MutableStateFlow<List<GalleryImage>>(emptyList())
    val allImages: StateFlow<List<GalleryImage>> = _allImages.asStateFlow()

    private var currentDateString: String? = null

    init {
        loadTank()
        loadAllImages()
    }

    fun loadTank() {
        viewModelScope.launch {
            _tank.value = repository.getTankById(tankId)
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _folders.value = repository.getGalleryFolders(tankId)
        }
    }
    
    fun loadAllImages() {
        viewModelScope.launch {
            _allImages.value = repository.getAllGalleryImages(tankId)
        }
    }

    fun loadImagesForDate(dateString: String) {
        currentDateString = dateString
        viewModelScope.launch {
            _images.value = repository.getGalleryImagesForDate(tankId, dateString)
        }
    }
    
    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                repository.saveGalleryImage(tankId, uri)
            }
            loadFolders() // Refresh folders
            loadAllImages() // Refresh all images
            currentDateString?.let { loadImagesForDate(it) } // Refresh images if viewing date
        }
    }

    fun setRating(image: GalleryImage, rating: Int) {
        viewModelScope.launch {
            repository.setGalleryImageRating(image.path, rating)
            // Update local state optimistically or reload
            loadAllImages()
            currentDateString?.let { loadImagesForDate(it) }
        }
    }

    fun deleteImage(image: GalleryImage) {
        viewModelScope.launch {
            repository.deleteGalleryImage(image.path)
            loadFolders() // Refresh folders in case it was the last image
            loadAllImages() // Refresh all images
            currentDateString?.let { loadImagesForDate(it) }
        }
    }

    fun updateTank(
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
                id = tankId,
                name = name,
                description = description,
                size = size,
                manufacturer = manufacturer,
                imagePath = finalImagePath
            )
            repository.updateTank(updatedTank)
            loadTank()
        }
    }

    fun deleteTank() {
        viewModelScope.launch {
            _tank.value?.let { repository.deleteTank(it) }
        }
    }
}

class GalleryViewModelFactory(
    private val context: Context,
    private val tankId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            return GalleryViewModel(ScanRepository(context), tankId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
