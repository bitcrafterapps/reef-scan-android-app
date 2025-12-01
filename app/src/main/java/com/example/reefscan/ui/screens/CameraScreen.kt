package com.example.reefscan.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.reefscan.ui.components.ScanButton
import com.example.reefscan.ui.theme.AquaBlue
import com.example.reefscan.ui.theme.CoralAccent
import com.example.reefscan.ui.theme.DeepOcean
import com.example.reefscan.ui.theme.GlassWhite
import com.example.reefscan.ui.theme.GlassWhiteBorder
import com.example.reefscan.util.ImageUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraScreen"

// Filter colors for blue light tanks
private val BlueLightFilterOrange = Color(0xFFF96712)
private val BlueLightFilterYellow = Color(0xFFECCE5D)

enum class FilterType {
    NONE, ORANGE, YELLOW
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    mode: String = "COMPREHENSIVE",
    onNavigateBack: () -> Unit,
    onNavigateToLoading: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // Camera state
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    
    // Blue light filter state
    var activeFilter by remember { mutableStateOf(FilterType.NONE) }
    
    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Flash effect
    LaunchedEffect(showCaptureFlash) {
        if (showCaptureFlash) {
            delay(150)
            showCaptureFlash = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            cameraPermissionState.status.isGranted -> {
                // Camera Preview - Full Screen
                CameraPreviewContent(
                    lifecycleOwner = lifecycleOwner,
                    context = context,
                    onImageCaptureReady = { capture ->
                        imageCapture = capture
                    }
                )
                
                // Filter overlay for preview
                val filterColor = when (activeFilter) {
                    FilterType.ORANGE -> BlueLightFilterOrange
                    FilterType.YELLOW -> BlueLightFilterYellow
                    FilterType.NONE -> Color.Transparent
                }
                
                val filterAlpha by animateFloatAsState(
                    targetValue = if (activeFilter != FilterType.NONE) 0.20f else 0f,
                    animationSpec = tween(300),
                    label = "filter_alpha"
                )
                
                if (filterAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(filterColor.copy(alpha = filterAlpha))
                    )
                }
                
                // Top Gradient Protection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom Gradient Control Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
                
                // Capture flash overlay
                AnimatedVisibility(
                    visible = showCaptureFlash,
                    enter = fadeIn(animationSpec = tween(50)),
                    exit = fadeOut(animationSpec = tween(100))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
                
                // Camera UI Overlay
                CameraUIOverlay(
                    mode = mode,
                    onClose = onNavigateBack,
                    onCapture = {
                        imageCapture?.let { capture ->
                            isCapturing = true
                            showCaptureFlash = true
                            
                            // Determine filter color to apply
                            val colorToApply = when (activeFilter) {
                                FilterType.ORANGE -> BlueLightFilterOrange.toArgb()
                                FilterType.YELLOW -> BlueLightFilterYellow.toArgb()
                                FilterType.NONE -> null
                            }
                            
                            capturePhoto(
                                context = context,
                                imageCapture = capture,
                                filterColor = colorToApply,
                                onSuccess = { uri ->
                                    isCapturing = false
                                    onNavigateToLoading(uri.toString())
                                },
                                onError = { exception ->
                                    isCapturing = false
                                    Log.e(TAG, "Photo capture failed: ${exception.message}")
                                }
                            )
                        }
                    },
                    activeFilter = activeFilter,
                    onFilterSelected = { filter ->
                        activeFilter = if (activeFilter == filter) FilterType.NONE else filter
                    }
                )
            }
            
            cameraPermissionState.status.shouldShowRationale -> {
                PermissionRationaleContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onNavigateBack = onNavigateBack
                )
            }
            
            else -> {
                PermissionDeniedContent(
                    onNavigateBack = onNavigateBack,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: Context,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val previewView = remember { PreviewView(context) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    
    LaunchedEffect(Unit) {
        val cameraProvider = getCameraProvider(context)
        
        cameraProvider.let { provider ->
            provider.unbindAll()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            onImageCaptureReady(imageCapture)
            
            try {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}")
            }
        }
    }
    
    // Handle pinch to zoom
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    camera?.let { cam ->
                        val zoomState = cam.cameraInfo.zoomState.value ?: return@let
                        val currentZoom = zoomState.zoomRatio
                        val newZoom = (currentZoom * zoom).coerceIn(
                            zoomState.minZoomRatio,
                            zoomState.maxZoomRatio
                        )
                        cam.cameraControl.setZoomRatio(newZoom)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CameraUIOverlay(
    mode: String,
    onClose: () -> Unit,
    onCapture: () -> Unit,
    activeFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit
) {
    // Display friendly mode name
    val modeLabel = when (mode) {
        "FISH_ID" -> "Scan Fish"
        "CORAL_ID" -> "Scan Coral"
        "ALGAE_ID" -> "Scan Algae"
        "PEST_ID" -> "Scan Pests"
        else -> "Complete Scan"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top Left Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Instructions / Mode Label
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            if (mode != "COMPREHENSIVE") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Specialized Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = AquaBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Top Right - Filter Buttons
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Orange Filter Button
            FilterButton(
                color = BlueLightFilterOrange,
                isActive = activeFilter == FilterType.ORANGE,
                onClick = { onFilterSelected(FilterType.ORANGE) },
                label = "Orange"
            )
            
            // Yellow Filter Button
            FilterButton(
                color = BlueLightFilterYellow,
                isActive = activeFilter == FilterType.YELLOW,
                onClick = { onFilterSelected(FilterType.YELLOW) },
                label = "Yellow"
            )
        }

        // Bottom Center Shutter Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            ScanButton(
                onClick = onCapture,
                modifier = Modifier.size(88.dp)
            )
        }
        
        // Filter indicator when active
        if (activeFilter != FilterType.NONE) {
            val indicatorColor = if (activeFilter == FilterType.ORANGE) BlueLightFilterOrange else BlueLightFilterYellow
            val filterName = if (activeFilter == FilterType.ORANGE) "Orange Filter" else "Yellow Filter"
            
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(indicatorColor.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$filterName Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FilterButton(
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) color
                    else Color.Black.copy(alpha = 0.3f)
                )
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) Color.White else color.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Show inner circle of color if not active
            if (!isActive) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.8f))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRationaleContent(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(GlassWhite)
                .border(1.dp, GlassWhiteBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "📸", style = MaterialTheme.typography.displayMedium)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Camera Access Needed",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "ReefScan needs access to your camera to identify marine life.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = AquaBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Grant Access", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onNavigateBack: () -> Unit,
    context: Context
) {
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = CoralAccent,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Camera Blocked",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Please enable camera access in settings to use this feature.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                )
                settingsLauncher.launch(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AquaBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Open Settings", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go Back", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

// Helper functions for camera and storage
private suspend fun getCameraProvider(context: Context): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    filterColor: Int?,
    onSuccess: (Uri) -> Unit,
    onError: (Exception) -> Unit
) {
    val photoFile = createImageFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture.takePicture(
        outputOptions,
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val originalUri = Uri.fromFile(photoFile)
                
                // Apply filter if enabled
                val finalUri = if (filterColor != null) {
                    ImageUtils.applyFilterToFile(context, originalUri, filterColor)
                } else {
                    originalUri
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSuccess(finalUri)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError(exception)
                }
            }
        }
    )
}

private fun createImageFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val storageDir = File(context.filesDir, "reef_scans").apply { mkdirs() }
    return File(storageDir, "SCAN_${timestamp}.jpg")
}

// Duplicate helper used for Camera Screen logic if needed internally, 
// though primarily moved to Home for Gallery. Kept for camera capture saving.
private fun copyImageToInternalStorage(context: Context, sourceUri: Uri): Uri? {
    return try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val storageDir = File(context.filesDir, "reef_scans").apply { mkdirs() }
        val destFile = File(storageDir, "GALLERY_${timestamp}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        Uri.fromFile(destFile)
    } catch (e: Exception) {
        null
    }
}
