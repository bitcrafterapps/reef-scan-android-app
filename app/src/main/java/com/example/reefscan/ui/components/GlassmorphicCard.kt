package com.example.reefscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.reefscan.ui.theme.GlassWhite
import com.example.reefscan.ui.theme.GlassWhiteBorder
import com.example.reefscan.ui.theme.GlassWhiteLight

/**
 * A glassmorphism-style card component with semi-transparent background
 * and subtle border effect
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = GlassWhite,
    borderColor: Color = GlassWhiteBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = backgroundColor.alpha * 0.7f)
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor.copy(alpha = borderColor.alpha * 1.5f),
                        borderColor.copy(alpha = borderColor.alpha * 0.5f)
                    )
                ),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

/**
 * A smaller glassmorphic card for compact UI elements
 */
@Composable
fun GlassmorphicChip(
    modifier: Modifier = Modifier,
    backgroundColor: Color = GlassWhite,
    content: @Composable BoxScope.() -> Unit
) {
    GlassmorphicCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        backgroundColor = backgroundColor,
        borderColor = GlassWhiteBorder,
        borderWidth = 1.dp,
        contentPadding = 12.dp,
        content = content
    )
}

/**
 * A highlight glassmorphic card with light glow effect
 */
@Composable
fun GlassmorphicHighlightCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassWhiteLight,
                        GlassWhite
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.3f),
                        accentColor.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
            .padding(24.dp),
        content = content
    )
}

