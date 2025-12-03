package com.example.reefscan.data.local

import android.content.Context
import android.net.Uri
import com.example.reefscan.data.model.GalleryImage
import com.example.reefscan.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Repository for managing saved scans and tanks
 */
class ScanRepository(context: Context) {
    
    private val database = ScanDatabase.getInstance(context)
    private val scanDao = database.scanDao()
    private val tankDao = database.tankDao()
    private val galleryImageDao = database.galleryImageDao()
    private val appContext = context.applicationContext
    
    /**
     * Save a scan result with its image and associated tank
     * 
     * @param scanResult The scan result to save
     * @param imageUri URI of the image (will be copied to internal storage)
     * @param tankId The ID of the tank this scan belongs to
     * @return The ID of the saved scan, or null on failure
     */
    suspend fun saveScan(scanResult: ScanResult, imageUri: Uri, tankId: Long): Long? = withContext(Dispatchers.IO) {
        try {
            // Copy image to permanent storage
            val imagePath = copyImageToPermanentStorage(imageUri)
                ?: return@withContext null
            
            // Create entity
            val entity = ScanEntity.fromScanResult(scanResult, imagePath, tankId)
            
            // Insert and get ID
            val id = scanDao.insertScan(entity)
            
            id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get all saved scans as a Flow (Legacy/All)
     */
    fun getAllScans(): Flow<List<ScanEntity>> {
        return scanDao.getAllScans()
    }

    /**
     * Get all saved scans for a specific tank
     */
    fun getScansForTank(tankId: Long): Flow<List<ScanEntity>> {
        return scanDao.getScansForTank(tankId)
    }
    
    /**
     * Get all scans as a one-time list
     */
    suspend fun getAllScansOnce(): List<ScanEntity> = withContext(Dispatchers.IO) {
        scanDao.getAllScansOnce()
    }
    
    /**
     * Get a single scan by ID
     */
    suspend fun getScanById(scanId: Long): ScanEntity? = withContext(Dispatchers.IO) {
        scanDao.getScanById(scanId)
    }
    
    /**
     * Delete a scan and its associated image
     */
    suspend fun deleteScan(scanId: Long) = withContext(Dispatchers.IO) {
        val scan = scanDao.getScanById(scanId)
        scan?.let {
            // Delete the image file
            val imageFile = File(it.imagePath)
            if (imageFile.exists()) {
                imageFile.delete()
            }
            // Delete from database
            scanDao.deleteScanById(scanId)
        }
    }
    
    /**
     * Get the count of saved scans
     */
    suspend fun getScanCount(): Int = withContext(Dispatchers.IO) {
        scanDao.getScanCount()
    }
    
    /**
     * Get the count of scans for a specific tank
     */
    suspend fun getScanCountForTank(tankId: Long): Int = withContext(Dispatchers.IO) {
        scanDao.getScanCountForTank(tankId)
    }
    
    /**
     * Get the count of gallery images for a specific tank
     */
    suspend fun getGalleryCountForTank(tankId: Long): Int = withContext(Dispatchers.IO) {
        galleryImageDao.getImageCountForTank(tankId)
    }

    // ==================== Tank Operations ====================

    fun getAllTanks(): Flow<List<TankEntity>> = tankDao.getAllTanks()

    suspend fun getTankById(tankId: Long): TankEntity? = withContext(Dispatchers.IO) {
        tankDao.getTankById(tankId)
    }

    suspend fun saveTank(tank: TankEntity): Long = withContext(Dispatchers.IO) {
        tankDao.insertTank(tank)
    }

    suspend fun updateTank(tank: TankEntity) = withContext(Dispatchers.IO) {
        tankDao.updateTank(tank)
    }

    suspend fun deleteTank(tank: TankEntity) = withContext(Dispatchers.IO) {
        // 1. Get all scans for this tank to delete their images
        val scans = scanDao.getScansForTankList(tank.id)
        
        // 2. Delete images for each scan
        scans.forEach { scan ->
            try {
                val imageFile = File(scan.imagePath)
                if (imageFile.exists()) {
                    imageFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 3. Delete all scans from DB
        scanDao.deleteScansByTankId(tank.id)
        
        // 4. Delete tank image if exists
        tank.imagePath?.let { path ->
            try {
                val tankImage = File(path)
                if (tankImage.exists()) {
                    tankImage.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 5. Delete gallery folder for this tank
        try {
            val tankGalleryDir = File(appContext.filesDir, "tank_gallery/${tank.id}")
            if (tankGalleryDir.exists()) {
                tankGalleryDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Delete gallery entries from DB
        galleryImageDao.deleteImagesForTank(tank.id)

        // 6. Delete the tank itself
        tankDao.deleteTank(tank)
    }

    suspend fun copyTankImage(imageUri: Uri): String? = withContext(Dispatchers.IO) {
         copyImageToPermanentStorage(imageUri, "tank_images", "tank_img_")
    }
    
    // ==================== Gallery Operations ====================

    /**
     * Save a gallery image for a specific tank
     * Saves to filesDir/tank_gallery/{tankId}/{dateString}/image.jpg
     */
    suspend fun saveGalleryImage(tankId: Long, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
            val timestamp = System.currentTimeMillis()
            val timeString = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(timestamp)
            val fileName = "IMG_$timeString.jpg"
            
            val galleryDir = File(appContext.filesDir, "tank_gallery/$tankId/$dateString")
            if (!galleryDir.exists()) {
                galleryDir.mkdirs()
            }
            
            val destFile = File(galleryDir, fileName)
            
            appContext.contentResolver.openInputStream(imageUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Save to DB
            val relativePath = getRelativePath(destFile)
            val imageEntity = GalleryImageEntity(
                path = relativePath,
                tankId = tankId,
                dateTaken = timestamp,
                rating = 0
            )
            galleryImageDao.insertOrUpdate(imageEntity)
            
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Set rating for a gallery image
     */
    suspend fun setGalleryImageRating(path: String, rating: Int) = withContext(Dispatchers.IO) {
        val relativePath = getRelativePath(File(path))
        val existing = galleryImageDao.getImage(relativePath)
        if (existing != null) {
            galleryImageDao.insertOrUpdate(existing.copy(rating = rating))
        } else {
            // Fallback if not in DB (migrating old images?) - unlikely given new feature
            // But for safety, try to extract tankId and date from path if possible
            // Assuming we don't support untracked files yet.
        }
    }

    /**
     * Delete a gallery image
     */
    suspend fun deleteGalleryImage(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
            val relativePath = getRelativePath(file)
            galleryImageDao.deleteImage(relativePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get list of date folders for a tank
     * Returns a list of Pairs (DateString, FirstImagePath?)
     */
    suspend fun getGalleryFolders(tankId: Long): List<Pair<String, String?>> = withContext(Dispatchers.IO) {
        val tankGalleryDir = File(appContext.filesDir, "tank_gallery/$tankId")
        if (!tankGalleryDir.exists()) {
            return@withContext emptyList()
        }
        
        tankGalleryDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name } // Newest dates first
            ?.map { dir ->
                val firstImage = dir.listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) }
                    ?.minByOrNull { it.lastModified() } // First image taken
                    ?.absolutePath
                dir.name to firstImage
            } ?: emptyList()
    }

    /**
     * Get list of images for a specific date and tank
     */
    suspend fun getGalleryImagesForDate(tankId: Long, dateString: String): List<GalleryImage> = withContext(Dispatchers.IO) {
        val dateDir = File(appContext.filesDir, "tank_gallery/$tankId/$dateString")
        if (!dateDir.exists()) {
            return@withContext emptyList()
        }
        
        val files = dateDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
            
        files.map { file ->
            val relativePath = getRelativePath(file)
            val entity = galleryImageDao.getImage(relativePath)
            val rating = entity?.rating ?: 0
            GalleryImage(file.absolutePath, file.lastModified(), rating)
        }
    }
    
    /**
     * Get all gallery images for a tank (across all dates)
     */
    suspend fun getAllGalleryImages(tankId: Long): List<GalleryImage> = withContext(Dispatchers.IO) {
        val tankDir = File(appContext.filesDir, "tank_gallery/$tankId")
        if (!tankDir.exists()) {
            return@withContext emptyList()
        }
        
        val allImages = mutableListOf<GalleryImage>()
        
        // Iterate through all date directories
        tankDir.listFiles()?.filter { it.isDirectory }?.forEach { dateDir ->
            val files = dateDir.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) }
                ?: emptyList()
            
            files.forEach { file ->
                val relativePath = getRelativePath(file)
                val entity = galleryImageDao.getImage(relativePath)
                val rating = entity?.rating ?: 0
                allImages.add(GalleryImage(file.absolutePath, file.lastModified(), rating))
            }
        }
        
        allImages.sortedByDescending { it.timestamp }
    }
    
    private fun getRelativePath(file: File): String {
        val rootPath = appContext.filesDir.absolutePath
        return file.absolutePath.removePrefix("$rootPath/")
    }
    
    /**
     * Copy image to permanent storage
     */
    private fun copyImageToPermanentStorage(
        sourceUri: Uri, 
        directoryName: String = "saved_scans", 
        prefix: String = "saved_scan_"
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis())
            val fileName = "${prefix}$timestamp.jpg"
            
            val savedDir = File(appContext.filesDir, directoryName)
            if (!savedDir.exists()) {
                savedDir.mkdirs()
            }
            
            val destFile = File(savedDir, fileName)
            
            // Handle both file:// and content:// URIs
            if (sourceUri.scheme == "file") {
                val sourceFile = File(sourceUri.path!!)
                sourceFile.copyTo(destFile, overwrite = true)
            } else {
                appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Clean up temporary scan images that weren't saved
     */
    suspend fun cleanupTempImages() = withContext(Dispatchers.IO) {
        try {
            val reefScansDir = File(appContext.filesDir, "reef_scans")
            if (reefScansDir.exists()) {
                // Delete files older than 1 hour
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                reefScansDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < oneHourAgo) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
