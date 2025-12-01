package com.example.reefscan.data.local

import android.content.Context
import android.net.Uri
import com.example.reefscan.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Repository for managing saved scans
 */
class ScanRepository(context: Context) {
    
    private val database = ScanDatabase.getInstance(context)
    private val scanDao = database.scanDao()
    private val appContext = context.applicationContext
    
    /**
     * Save a scan result with its image
     * 
     * @param scanResult The scan result to save
     * @param imageUri URI of the image (will be copied to internal storage)
     * @return The ID of the saved scan, or null on failure
     */
    suspend fun saveScan(scanResult: ScanResult, imageUri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            // Copy image to permanent storage
            val imagePath = copyImageToPermanentStorage(imageUri)
                ?: return@withContext null
            
            // Create entity
            val entity = ScanEntity.fromScanResult(scanResult, imagePath)
            
            // Insert and get ID
            val id = scanDao.insertScan(entity)
            
            // Clean up old scans if needed
            scanDao.deleteOldestScans()
            
            id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get all saved scans as a Flow
     */
    fun getAllScans(): Flow<List<ScanEntity>> {
        return scanDao.getAllScans()
    }
    
    /**
     * Get all saved scans as a one-time list
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
     * Copy image to permanent storage in the saved_scans directory
     */
    private fun copyImageToPermanentStorage(sourceUri: Uri): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis())
            val fileName = "saved_scan_$timestamp.jpg"
            
            val savedScansDir = File(appContext.filesDir, "saved_scans")
            if (!savedScansDir.exists()) {
                savedScansDir.mkdirs()
            }
            
            val destFile = File(savedScansDir, fileName)
            
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

