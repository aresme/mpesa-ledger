package com.ares.mpesaledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

// Dark only, and no dynamic color: the brand palette wins over Material You.
private val AresColorScheme = darkColorScheme(
    primary = AresGreen,
    onPrimary = AresBlack,
    primaryContainer = AresGreenContainer,
    onPrimaryContainer = AresGreen,
    secondary = AresGreen,
    onSecondary = AresBlack,
    secondaryContainer = AresGreenContainer,
    onSecondaryContainer = AresGreen,
    tertiary = AresAmber,
    onTertiary = AresBlack,
    background = AresBlack,
    onBackground = AresForeground,
    surface = AresBlack,
    onSurface = AresForeground,
    surfaceVariant = AresSurfaceHigh,
    onSurfaceVariant = AresMuted,
    surfaceContainerLowest = AresBlack,
    surfaceContainerLow = AresSurface,
    surfaceContainer = AresSurface,
    surfaceContainerHigh = AresSurfaceHigh,
    surfaceContainerHighest = AresSurfaceHighest,
    inverseSurface = AresForeground,
    inverseOnSurface = AresBlack,
    outline = AresOutline,
    outlineVariant = AresOutline,
    error = AresRed,
    onError = AresBlack,
)

private val AresShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** True while the debug build runs on the demo database; drives the DEMO badge. */
val LocalDemoMode = staticCompositionLocalOf { false }

@Composable
fun MpesaLedgerTheme(isDemo: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AresColorScheme, typography = AppTypography, shapes = AresShapes) {
        // LocalContentColor defaults to black outside a Surface, which is invisible on our
        // black background. Make the foreground the app-wide default text/icon color.
        CompositionLocalProvider(
            LocalContentColor provides AresForeground,
            LocalDemoMode provides isDemo,
            content = content,
        )
    }
}
