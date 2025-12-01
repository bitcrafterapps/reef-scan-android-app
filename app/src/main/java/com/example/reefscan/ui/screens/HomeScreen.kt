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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.reefscan.R
import com.example.reefscan.data.local.ScanRepository
import com.example.reefscan.ui.components.AddEditTankDialog
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tankId: Long,
    onNavigateToCamera: (String) -> Unit,
    onNavigateToSavedScans: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToLoading: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HomeScreenViewModel? = null
) {
    val context = LocalContext.current
    val finalViewModel = viewModel ?: viewModel(
        factory = HomeScreenViewModelFactory(ScanRepository(context))
    )

    val tank by finalViewModel.tank.collectAsState()
    
    // Edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Help sheet state
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(tankId) {
        finalViewModel.loadTank(tankId)
    }
    
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                     Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Edit Button
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GlassWhite)
                            .border(1.dp, GlassWhiteBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Tank",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Gallery Button
                    IconButton(
                        onClick = onNavigateToGallery,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GlassWhite)
                            .border(1.dp, GlassWhiteBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // History Button
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

                    // Help Button
                    IconButton(
                        onClick = { showHelpSheet = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GlassWhite)
                            .border(1.dp, GlassWhiteBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = "Help",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                    text = tank?.name ?: "My Tank",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        lineHeight = 36.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Identify fish, coral, or issues instantly. Tap a category or do a complete scan.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Feature Pills
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
                        icon = Icons.Outlined.SetMeal,
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
                // Main Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Complete Scan Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ScanButton(
                            onClick = { onNavigateToCamera("COMPREHENSIVE") },
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scan",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Take Pics Button (New)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(GlassWhite)
                                .border(2.dp, GlassWhiteBorder, CircleShape)
                                .clickable { onNavigateToCamera("GALLERY") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Take Photos",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Add Pics",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Secondary CTA - Gallery Import
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

        // Edit Tank Dialog
        if (showEditDialog && tank != null) {
            AddEditTankDialog(
                tank = tank,
                onDismiss = { showEditDialog = false },
                onSave = { name, desc, size, manufacturer, uri ->
                    finalViewModel.updateTank(
                        tank!!.id, name, desc, size, manufacturer, uri, tank!!.imagePath
                    )
                    showEditDialog = false
                },
                onDelete = {
                    showDeleteConfirmation = true
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmation && tank != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Tank?") },
                text = { Text("Are you sure you want to delete \"${tank?.name}\"? This will also delete all associated scans and images. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            finalViewModel.deleteTank(tank!!)
                            showDeleteConfirmation = false
                            showEditDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = DeepOcean,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }

        // Help Bottom Sheet
        if (showHelpSheet) {
            ModalBottomSheet(
                onDismissRequest = { showHelpSheet = false },
                sheetState = helpSheetState,
                containerColor = DeepOcean,
                dragHandle = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            ) {
                HelpContent()
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

@Composable
private fun HelpContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AquaBlue, AquaBlueDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "How to Use ReefScan",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quick guide to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        // Help Items
        HelpItem(
            icon = Icons.Filled.CameraAlt,
            title = "Scan Your Tank",
            description = "Tap 'Scan' to take a photo of your aquarium. Our AI will analyze it and identify fish, coral, and potential issues.",
            color = AquaBlue
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HelpItem(
            icon = Icons.Filled.TouchApp,
            title = "Quick Categories",
            description = "Use Fish ID, Coral ID, Algae Detection, or Pest Alerts for targeted scans focused on specific identification.",
            color = CategoryFish
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HelpItem(
            icon = Icons.Filled.PhotoLibrary,
            title = "Add Photos",
            description = "Build a visual timeline of your tank. Tap 'Add Pics' to capture photos or import from your gallery.",
            color = CategorySPSCoral
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HelpItem(
            icon = Icons.Filled.History,
            title = "View History",
            description = "Access all your past scans and analysis results. Track changes in your tank over time.",
            color = CategoryAlgae
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HelpItem(
            icon = Icons.Filled.Star,
            title = "Rate & Organize",
            description = "Rate your best photos in the gallery. Use folders organized by date to track your tank's progress.",
            color = CategoryPest
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Pro Tips Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AquaBlue.copy(alpha = 0.15f),
                            AquaBlueDark.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            AquaBlue.copy(alpha = 0.3f),
                            AquaBlueDark.copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "💡 Pro Tips",
                    style = MaterialTheme.typography.titleSmall,
                    color = AquaBlue,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Use good lighting for best results\n• Hold your phone steady when scanning\n• Clean the glass before taking photos\n• Scan regularly to catch issues early",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun HelpItem(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
        }
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
