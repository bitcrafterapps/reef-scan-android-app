package com.bitcraftapps.reefscan.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitcraftapps.reefscan.R
import com.bitcraftapps.reefscan.ui.theme.AquaBlue
import com.bitcraftapps.reefscan.ui.theme.AquaBlueLight
import com.bitcraftapps.reefscan.ui.theme.DeepOcean
import com.bitcraftapps.reefscan.ui.theme.DeepOceanDark
import com.bitcraftapps.reefscan.ui.theme.Seafoam
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import com.bitcraftapps.reefscan.BuildConfig

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit
) {
    // Animation states
    val backgroundAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(20f) }
    val taglineAlpha = remember { Animatable(0f) }
    val bottomAlpha = remember { Animatable(0f) }
    
    // Infinite transitions for shimmer and glow effects
    val infiniteTransition = rememberInfiniteTransition(label = "effects")
    
    // Water shimmer animation - moves horizontally
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    // Secondary wave animation - slower, different phase
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    
    // Logo glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        // Background fade in
        launch {
            backgroundAlpha.animateTo(1f, tween(1000))
        }
        
        // Logo entrance
        launch {
            delay(200)
            logoAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            delay(200)
            logoScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        
        // Title entrance
        launch {
            delay(500)
            textAlpha.animateTo(1f, tween(500))
        }
        launch {
            delay(500)
            textOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
        
        // Tagline entrance
        launch {
            delay(750)
            taglineAlpha.animateTo(1f, tween(500))
        }
        
        // Bottom text
        launch {
            delay(1000)
            bottomAlpha.animateTo(1f, tween(500))
        }
        
        // Navigate after delay
        delay(6000)
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepOceanDark)
    ) {
        // Background Photo
        Image(
            painter = painterResource(id = R.drawable.splash_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha.value * 0.6f },
            contentScale = ContentScale.Crop
        )
        
        // Water shimmer effect overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha.value * 0.4f }
        ) {
            val width = size.width
            val height = size.height
            
            // Create multiple shimmer bands that move across the screen
            for (i in 0..5) {
                val phase = (shimmerOffset + i * 0.15f) % 1f
                val wavePhase = (waveOffset + i * 0.2f) % 1f
                
                // Calculate position with sine wave for organic movement
                val xOffset = width * phase
                val yWave = sin(wavePhase * Math.PI * 2).toFloat() * 50f
                
                // Draw shimmer highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AquaBlueLight.copy(alpha = 0.3f),
                            AquaBlue.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(xOffset, height * 0.3f + yWave + i * 80f),
                        radius = 200f + i * 30f
                    ),
                    center = Offset(xOffset, height * 0.3f + yWave + i * 80f),
                    radius = 200f + i * 30f
                )
            }
            
            // Add some subtle caustic-like patterns at the bottom
            for (i in 0..3) {
                val phase = (waveOffset + i * 0.25f) % 1f
                val xPos = width * (0.2f + i * 0.2f) + sin(phase * Math.PI * 2).toFloat() * 30f
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Seafoam.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(xPos, height * 0.7f),
                        radius = 150f
                    ),
                    center = Offset(xPos, height * 0.7f),
                    radius = 150f
                )
            }
        }
        
        // Dark gradient overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DeepOceanDark.copy(alpha = 0.6f),
                            Color.Transparent,
                            DeepOceanDark.copy(alpha = 0.75f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        // Radial vignette for focus
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            DeepOceanDark.copy(alpha = 0.4f)
                        ),
                        radius = 1200f
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // Logo without container - transparent background
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Animated glow behind logo
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer {
                            alpha = logoAlpha.value * glowAlpha
                        }
                        .blur(50.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    AquaBlue.copy(alpha = 0.8f),
                                    Seafoam.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Logo - using vector drawable directly without background
                Image(
                    painter = painterResource(id = R.drawable.ic_real_coral),
                    contentDescription = "ReefScan Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                        },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // App Name
            Text(
                text = "ReefScan",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 46.sp,
                    letterSpacing = (-1.5).sp
                ),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textOffsetY.value
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tagline
            Text(
                text = "Beyond-the-glass!",
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 1.sp
                ),
                color = AquaBlue,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    alpha = taglineAlpha.value
                }
            )
            
            Spacer(modifier = Modifier.weight(1.2f))
            
            // Bottom branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = bottomAlpha.value }
            ) {
                // Version Number
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp
                    ),
                    color = Color.White.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Powered by BitCraft",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
