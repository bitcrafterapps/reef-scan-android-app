package com.example.reefscan.ui.screens

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.reefscan.ui.theme.AquaBlue
import com.example.reefscan.ui.theme.CoralAccent
import com.example.reefscan.ui.theme.DeepOcean
import com.example.reefscan.ui.theme.DeepOceanDark
import com.example.reefscan.ui.theme.GlassWhite

@Composable
fun LoadingScreen(
    imageUri: String,
    mode: String,
    tankId: Long,
    onNavigateToResults: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: LoadingViewModel = viewModel(factory = LoadingViewModelFactory(LocalContext.current))
) {
    val state by viewModel.state.collectAsState()
    val uri = remember { Uri.parse(imageUri) }
    
    // Start analysis when screen loads
    LaunchedEffect(imageUri) {
        viewModel.analyzeImage(uri, mode, tankId)
    }
    
    // Handle navigation on success
    LaunchedEffect(state) {
        when (val currentState = state) {
            is LoadingState.Success -> {
                onNavigateToResults(currentState.scanId)
            }
            else -> {}
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOcean)
    ) {
        // Top Half: Cover Image with Scanning Effect
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Image
            AsyncImage(
                model = uri,
                contentDescription = "Analyzing Image",
        modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Scrim at bottom of image to blend into content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                DeepOceanDark.copy(alpha = 0.8f),
                                DeepOceanDark
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Scanning Overlay
            if (state is LoadingState.Analyzing) {
                ScanningOverlay()
            }
        }
        
        // Bottom Half: Controls & Status
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(DeepOceanDark, DeepOcean)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val currentState = state) {
                    is LoadingState.Analyzing -> {
                        // Modern Status Indicators
                        Box(
        contentAlignment = Alignment.Center
    ) {
                            CircularProgressIndicator(
                                color = AquaBlue.copy(alpha = 0.2f),
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp,
                                trackColor = Color.Transparent
                            )
                            CircularProgressIndicator(
                                color = AquaBlue,
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Analyzing text with shimmer
                        val analyzingText = when(mode) {
                            "FISH_ID" -> "Identifying fish species..."
                            "CORAL_ID" -> "Analyzing coral health..."
                            "ALGAE_ID" -> "Detecting algae types..."
                            "PEST_ID" -> "Scanning for pests..."
                            else -> "Analyzing reef life..."
                        }
                        ShimmerText(text = analyzingText)

                        Spacer(modifier = Modifier.height(12.dp))

                        val detailText = when(mode) {
                            "FISH_ID" -> "Checking for disease, stress signs, and species ID."
                            "CORAL_ID" -> "Identifying coral types and checking for recession or pests."
                            "ALGAE_ID" -> "Identifying nuisance algae and potential causes."
                            "PEST_ID" -> "Looking for aiptasia, flatworms, and other hitchhikers."
                            else -> "Identifying species and detecting potential issues in your tank."
                        }
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    is LoadingState.Error -> {
                        ErrorContent(
                            message = currentState.message,
                            onRetry = { viewModel.analyzeImage(uri, mode, tankId) },
                            onBack = onNavigateBack
                        )
                    }
                    is LoadingState.Success -> {
                        // Will navigate automatically
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Moving scan line gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        scanLineOffset - 0.1f to Color.Transparent,
                        scanLineOffset to AquaBlue.copy(alpha = 0.5f),
                        scanLineOffset + 0.01f to AquaBlue,
                        scanLineOffset + 0.1f to AquaBlue.copy(alpha = 0.2f),
                        scanLineOffset + 0.2f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
        )
    }
}

@Composable
private fun ShimmerText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White.copy(alpha = shimmerAlpha),
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Error icon
        Text(
            text = "😕",
            style = MaterialTheme.typography.displayMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Analysis Failed",
            style = MaterialTheme.typography.headlineSmall,
            color = CoralAccent,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AquaBlue
            ),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = GlassWhite
            ),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Take New Photo",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
