package com.example.reefscan.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reefscan.ui.theme.AquaBlue
import com.example.reefscan.ui.theme.AquaBlueLight
import com.example.reefscan.ui.theme.DeepOcean
import com.example.reefscan.ui.theme.Seafoam

@Composable
fun ScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    // Subtle Press animation
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "press_scale"
    )
    
    // Elegant, slower pulse for the outer ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    
    // Haptic feedback on press
    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Radiating Pulse Ring
        Box(
            modifier = Modifier
                .size(80.dp) // Base size matching button
                .scale(pulseScale)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = AquaBlue.copy(alpha = pulseAlpha),
                    shape = CircleShape
                )
        )
        
        // Main Shutter Button - Cleaner, more solid look
        Box(
            modifier = Modifier
                .scale(pressScale)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = AquaBlue.copy(alpha = 0.5f)
                )
                .size(80.dp)
            .clip(CircleShape)
            .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AquaBlueLight,
                            AquaBlue
                        )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = Color.White),
                onClick = onClick
            )
                // Inner white ring for "Camera Shutter" feel
                .border(
                    width = 4.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
        contentAlignment = Alignment.Center
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Scan",
                tint = DeepOcean, // Dark icon on light button for contrast
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
