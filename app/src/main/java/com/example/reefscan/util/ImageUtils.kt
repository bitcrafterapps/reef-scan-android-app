package com.example.reefscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utility functions for image processing
 */
object ImageUtils {
    
    private const val MAX_IMAGE_DIMENSION = 1024
    private const val JPEG_QUALITY = 85
    
    /**
     * Load an image from URI and encode it as Base64 string
     * The image is resized and compressed for efficient API transmission
     * 
     * @param context Android context
     * @param imageUri URI of the image to encode
     * @param filterColor Optional color to apply as a filter (overlay)
     * @return Base64-encoded string of the compressed image, or null on failure
     */
    fun encodeImageToBase64(context: Context, imageUri: Uri, filterColor: Int? = null): String? {
        return try {
            // Load and process the bitmap
            var bitmap = loadAndProcessBitmap(context, imageUri) ?: return null
            
            // Apply filter if requested
            if (filterColor != null) {
                val filteredBitmap = applyColorFilter(bitmap, filterColor)
                if (bitmap != filteredBitmap) {
                    bitmap.recycle()
                    bitmap = filteredBitmap
                }
            }
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val byteArray = outputStream.toByteArray()
            
            // Recycle bitmap to free memory
            bitmap.recycle()
            
            // Encode to Base64
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Apply color filter to an image file and save it
     * This modifies the actual file with the tint applied
     * 
     * @param context Android context
     * @param imageUri URI of the image to filter
     * @param filterColor Color integer to apply
     * @return URI of the filtered image (same file, modified), or original on failure
     */
    fun applyFilterToFile(context: Context, imageUri: Uri, filterColor: Int): Uri {
        return try {
            // Load the bitmap
            val bitmap = loadAndProcessBitmap(context, imageUri) ?: return imageUri
            
            // Apply the filter
            val filteredBitmap = applyColorFilter(bitmap, filterColor)
            
            // Get the file path from URI
            val filePath = when (imageUri.scheme) {
                "file" -> imageUri.path
                else -> null
            }
            
            if (filePath != null) {
                // Save the filtered bitmap back to the file
                val file = File(filePath)
                FileOutputStream(file).use { outputStream ->
                    filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                
                // Clean up
                if (bitmap != filteredBitmap) bitmap.recycle()
                filteredBitmap.recycle()
                
                Uri.fromFile(file)
            } else {
                // For content:// URIs, create a new filtered file
                val timestamp = System.currentTimeMillis()
                val filteredFile = File(context.filesDir, "reef_scans/FILTERED_$timestamp.jpg")
                filteredFile.parentFile?.mkdirs()
                
                FileOutputStream(filteredFile).use { outputStream ->
                    filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                
                // Clean up
                if (bitmap != filteredBitmap) bitmap.recycle()
                filteredBitmap.recycle()
                
                Uri.fromFile(filteredFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            imageUri
        }
    }
    
    /**
     * Apply color tint to a bitmap
     * This simulates a lens filter used in reef photography
     */
    private fun applyColorFilter(source: Bitmap, color: Int): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // Draw original
        canvas.drawBitmap(source, 0f, 0f, null)
        
        // Draw color overlay
        val paint = Paint()
        paint.color = color
        paint.alpha = (0.20 * 255).toInt() // 20% alpha to match preview
        
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), paint)
        
        return result
    }
    
    /**
     * Load a bitmap from URI, resize it, and apply EXIF rotation
     */
    private fun loadAndProcessBitmap(context: Context, imageUri: Uri): Bitmap? {
        return try {
            // First, get the image dimensions without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            // Calculate sample size for efficient loading
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            
            // Load the bitmap with sampling
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, loadOptions)
            } ?: return null
            
            // Scale to exact dimensions if needed
            val scaledBitmap = scaleBitmapToMaxDimension(bitmap)
            
            // Apply EXIF rotation
            val rotatedBitmap = applyExifRotation(context, imageUri, scaledBitmap)
            
            // Clean up intermediate bitmaps
            if (bitmap != scaledBitmap) bitmap.recycle()
            if (scaledBitmap != rotatedBitmap) scaledBitmap.recycle()
            
            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Calculate sample size for bitmap loading
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_IMAGE_DIMENSION * 2 || 
               height / sampleSize > MAX_IMAGE_DIMENSION * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
    
    /**
     * Scale bitmap to fit within MAX_IMAGE_DIMENSION
     */
    private fun scaleBitmapToMaxDimension(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }
        
        val scale = minOf(
            MAX_IMAGE_DIMENSION.toFloat() / width,
            MAX_IMAGE_DIMENSION.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Apply EXIF rotation to bitmap
     */
    private fun applyExifRotation(context: Context, imageUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val rotation = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            
            if (rotation == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            bitmap
        }
    }
    
    /**
     * Get file size in bytes for a URI
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Copy image to internal storage
     */
    fun copyToInternalStorage(context: Context, sourceUri: Uri, fileName: String): File? {
        return try {
            val destFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
