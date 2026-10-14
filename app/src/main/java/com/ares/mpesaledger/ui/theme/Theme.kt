package com.ares.mpesaledger.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Dark only, and no dynamic color: the brand palette wins over Material You.
private val AresColorScheme = darkColorScheme(
    primary = AresGreen,
    onPrimary = AresBlack,
    primaryContainer = AresGreenDim,
    onPrimaryContainer = AresBlack,
    secondary = AresGreen,
    onSecondary = AresBlack,
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
    surfaceContainerHighest = AresSurfaceHigh,
    outline = AresOutline,
    outlineVariant = AresOutline,
    error = AresRed,
    onError = AresBlack,
    tertiary = AresAmber,
    onTertiary = AresBlack,
)

@Composable
fun MpesaLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AresColorScheme, typography = AppTypography) {
        // LocalContentColor defaults to black outside a Surface, which is invisible on our
        // black background. Make the foreground the app-wide default text/icon color.
        CompositionLocalProvider(LocalContentColor provides AresForeground, content = content)
    }
}
