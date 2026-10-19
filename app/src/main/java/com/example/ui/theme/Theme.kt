package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val VitalPulseExpressiveLightScheme = lightColorScheme(
    primary = ExpressivePrimary,
    onPrimary = ExpressiveOnPrimary,
    primaryContainer = ExpressivePrimaryContainer,
    onPrimaryContainer = ExpressiveOnPrimaryContainer,
    secondary = ExpressiveSecondary,
    onSecondary = ExpressiveOnSecondary,
    secondaryContainer = ExpressiveSecondaryContainer,
    onSecondaryContainer = ExpressiveOnSecondaryContainer,
    tertiary = ExpressiveTertiary,
    onTertiary = ExpressiveOnTertiary,
    tertiaryContainer = ExpressiveTertiaryContainer,
    onTertiaryContainer = ExpressiveOnTertiaryContainer,
    background = ExpressiveBackground,
    onBackground = ExpressiveTextPrimary,
    surface = ExpressiveSurface,
    onSurface = ExpressiveTextPrimary,
    surfaceBright = ExpressiveSurfaceBright,
    surfaceDim = ExpressiveSurfaceDim,
    surfaceContainerLowest = ExpressiveSurfaceContainerLowest,
    surfaceContainerLow = ExpressiveSurfaceContainerLow,
    surfaceContainer = ExpressiveSurfaceContainer,
    surfaceContainerHigh = ExpressiveSurfaceContainerHigh,
    surfaceContainerHighest = ExpressiveSurfaceContainerHighest,
    surfaceVariant = ExpressiveSurfaceContainer,
    onSurfaceVariant = ExpressiveTextSecondary,
    outline = ExpressiveOutline,
    outlineVariant = ExpressiveOutlineVariant
)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(34.dp),
    extraLarge = RoundedCornerShape(44.dp)
)

@Composable
fun VitalPulseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = VitalPulseExpressiveLightScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    VitalPulseTheme(darkTheme = false, content = content)
}
