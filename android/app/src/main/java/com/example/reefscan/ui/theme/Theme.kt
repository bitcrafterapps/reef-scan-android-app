package com.bitcraftapps.reefscan.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Ocean Dark Color Scheme - the only theme for ReefScan
private val OceanDarkColorScheme = darkColorScheme(
    // Primary colors
    primary = AquaBlue,
    onPrimary = TextPrimary,
    primaryContainer = AquaBlueDark,
    onPrimaryContainer = TextPrimary,

    // Secondary colors
    secondary = Seafoam,
    onSecondary = DeepOcean,
    secondaryContainer = SeafoamDark,
    onSecondaryContainer = TextPrimary,

    // Tertiary colors
    tertiary = CoralAccent,
    onTertiary = TextPrimary,
    tertiaryContainer = CoralAccentDark,
    onTertiaryContainer = TextPrimary,

    // Background colors
    background = DeepOcean,
    onBackground = TextPrimary,

    // Surface colors
    surface = DeepOcean,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    surfaceTint = AquaBlue,

    // Inverse colors
    inverseSurface = TextPrimary,
    inverseOnSurface = DeepOcean,
    inversePrimary = AquaBlueDark,

    // Error colors
    error = CoralAccent,
    onError = TextPrimary,
    errorContainer = CoralAccentDark,
    onErrorContainer = TextPrimary,

    // Outline colors
    outline = GlassWhiteBorder,
    outlineVariant = GlassWhite,

    // Scrim
    scrim = DeepOceanDark
)

// Custom shapes with refined rounded corners for a professional look (12-16dp)
val ReefScanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun ReefScanTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = OceanDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use WindowCompat for modern edge-to-edge handling
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ReefScanShapes,
        content = content
    )
}
