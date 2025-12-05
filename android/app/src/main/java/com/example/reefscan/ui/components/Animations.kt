package com.bitcraftapps.reefscan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitcraftapps.reefscan.ui.theme.GlassWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modifier extension for press scale animation with haptic feedback
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    enableHaptics: Boolean = true
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "press_scale"
    )
    
    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptics) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = { }
        )
}

/**
 * Animated button with scale effect and haptic feedback
 */
@Composable
fun AnimatedPressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "button_scale"
    )
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled,
                onClick = onClick
            ),
        content = content
    )
}

/**
 * Shimmer effect modifier for loading states
 */
@Composable
fun Modifier.shimmerEffect(
    isLoading: Boolean = true,
    shimmerColor: Color = Color.White.copy(alpha = 0.3f),
    baseColor: Color = GlassWhite
): Modifier = composed {
    if (!isLoading) return@composed this
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    this.background(
        brush = Brush.linearGradient(
            colors = listOf(
                baseColor,
                shimmerColor,
                baseColor
            ),
            start = Offset(translateAnim - 500f, 0f),
            end = Offset(translateAnim, 0f)
        )
    )
}

/**
 * Shimmer placeholder box for loading states
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    cornerRadius: Dp = 16.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .shimmerEffect()
    )
}

/**
 * Pulse animation modifier
 */
@Composable
fun Modifier.pulseAnimation(
    enabled: Boolean = true,
    minScale: Float = 0.97f,
    maxScale: Float = 1.03f,
    duration: Int = 2000
): Modifier = composed {
    if (!enabled) return@composed this
    
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    this.scale(scale)
}

/**
 * Fade in animation with optional delay for staggered effects
 */
@Composable
fun FadeInAnimation(
    visible: Boolean,
    delayMillis: Int = 0,
    durationMillis: Int = 300,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis.toLong())
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis)
            )
        } else {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMillis / 2)
            )
        }
    }
    
    Box(
        modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
    ) {
        content()
    }
}

/**
 * Slide up and fade in animation
 */
@Composable
fun SlideUpFadeIn(
    visible: Boolean,
    delayMillis: Int = 0,
    durationMillis: Int = 400,
    slideOffset: Float = 50f,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(slideOffset) }
    
    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis.toLong())
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = durationMillis)
                )
            }
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
            )
        } else {
            alpha.snapTo(0f)
            offsetY.snapTo(slideOffset)
        }
    }
    
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            this.translationY = offsetY.value
        }
    ) {
        content()
    }
}

/**
 * Glow animation modifier
 */
@Composable
fun Modifier.glowAnimation(
    color: Color,
    enabled: Boolean = true,
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.8f,
    duration: Int = 1500
): Modifier = composed {
    if (!enabled) return@composed this
    
    val transition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    this.graphicsLayer {
        shadowElevation = 20f
        alpha = glowAlpha
    }
}

/**
 * Success check animation state holder
 */
class SuccessAnimationState {
    private val _scale = Animatable(0f)
    val scale: Float get() = _scale.value
    
    private val _alpha = Animatable(0f)
    val alpha: Float get() = _alpha.value
    
    var isAnimating = false
        private set
    
    suspend fun animate() {
        isAnimating = true
        _scale.snapTo(0f)
        _alpha.snapTo(0f)
        
        kotlinx.coroutines.coroutineScope {
            launch {
                _scale.animateTo(
                    targetValue = 1.2f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                )
                _scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(100)
                )
            }
            launch {
                _alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(200)
                )
                delay(1500)
                _alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(300)
                )
            }
        }
        isAnimating = false
    }
}

@Composable
fun rememberSuccessAnimationState(): SuccessAnimationState {
    return remember { SuccessAnimationState() }
}
