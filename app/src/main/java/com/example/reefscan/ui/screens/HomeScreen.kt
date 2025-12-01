package com.example.reefscan.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.reefscan.R
import com.example.reefscan.ui.components.ScanButton
import com.example.reefscan.ui.theme.AquaBlue
import com.example.reefscan.ui.theme.AquaBlueDark
import com.example.reefscan.ui.theme.CategoryAlgae
import com.example.reefscan.ui.theme.CategoryFish
import com.example.reefscan.ui.theme.CategoryPest
import com.example.reefscan.ui.theme.CategorySPSCoral
import com.example.reefscan.ui.theme.DeepOcean
import com.example.reefscan.ui.theme.DeepOceanDark
import com.example.reefscan.ui.theme.GlassWhite
import com.example.reefscan.ui.theme.GlassWhiteBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    onNavigateToCamera: (String) -> Unit,
    onNavigateToSavedScans: () -> Unit,
    onNavigateToLoading: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    // Animation states
    val titleAlpha = remember { Animatable(0f) }
    val featuresAlpha = remember { Animatable(0f) }
    val actionsAlpha = remember { Animatable(0f) }
    val actionsOffsetY = remember { Animatable(40f) }
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val copiedUri = copyImageToInternalStorage(context, selectedUri)
            copiedUri?.let {
                onNavigateToLoading(it.toString(), "COMPREHENSIVE")
            }
        }
    }
    
    LaunchedEffect(Unit) {
        launch {
            delay(100)
            titleAlpha.animateTo(1f, tween(600))
        }
        launch {
            delay(300)
            featuresAlpha.animateTo(1f, tween(600))
        }
        launch {
            delay(500)
            actionsAlpha.animateTo(1f, tween(500))
        }
        launch {
            delay(500)
            actionsOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepOceanDark,
                        DeepOcean,
                        DeepOcean,
                        AquaBlueDark.copy(alpha = 0.6f)
                    )
                )
            )
    ) {
        // Background Animation
        val compositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.bubbles))
        val animationState = animateLottieCompositionAsState(
            composition = compositionResult.value,
            iterations = LottieConstants.IterateForever
        )

        LottieAnimation(
            composition = compositionResult.value,
            progress = { animationState.value },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.4f },
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = titleAlpha.value },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "ReefScan",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 32.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI-Powered Reef Diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AquaBlue
                    )
                }
                
                IconButton(
                    onClick = onNavigateToSavedScans,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassWhite)
                        .border(1.dp, GlassWhiteBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Scan History",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Hero Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = titleAlpha.value }
            ) {
                Text(
                    text = "Instantly identify what's in your reef tank",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        lineHeight = 36.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Point your camera at any fish, coral, invertebrate, or issue—ReefScan's AI will identify it and give you care recommendations.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Feature Pills (Now Clickable Buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = featuresAlpha.value },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(
                        icon = Icons.Outlined.Pets,
                        label = "Fish ID",
                        color = CategoryFish,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToCamera("FISH_ID") }
                    )
                    FeaturePill(
                        icon = Icons.Outlined.Spa,
                        label = "Coral ID",
                        color = CategorySPSCoral,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToCamera("CORAL_ID") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(
                        icon = Icons.Outlined.WaterDrop,
                        label = "Algae Detection",
                        color = CategoryAlgae,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToCamera("ALGAE_ID") }
                    )
                    FeaturePill(
                        icon = Icons.Outlined.BugReport,
                        label = "Pest Alerts",
                        color = CategoryPest,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToCamera("PEST_ID") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = actionsAlpha.value
                        translationY = actionsOffsetY.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary CTA - Complete Scan Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ScanButton(
                        onClick = { onNavigateToCamera("COMPREHENSIVE") },
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Complete Scan",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Secondary CTA - Gallery
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassWhite)
                        .border(1.dp, GlassWhiteBorder, RoundedCornerShape(12.dp))
                        .clickable { galleryLauncher.launch("image/*") }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Choose from Gallery",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tagline
                Text(
                    text = "Works offline • Results in seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.15f),
                        color.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.4f),
                        color.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper to copy gallery image to internal storage
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
        Log.e(TAG, "Failed to copy image: ${e.message}")
        null
    }
}
