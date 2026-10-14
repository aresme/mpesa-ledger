package com.ares.mpesaledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ares.mpesaledger.R

// Fonts are bundled in res/font (OFL). No downloadable fonts: that path needs Play Services.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Inter),
    displayMedium = base.displayMedium.copy(fontFamily = Inter),
    displaySmall = base.displaySmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)

/**
 * Amounts use Inter with tabular figures ("tnum") so digits line up in columns without a
 * monospace look. JetBrains Mono is kept for codes and raw SMS text.
 */
object Num {
    private const val TABULAR = "tnum"
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, fontFeatureSettings = TABULAR)
    val large = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = TABULAR)
    val medium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR)
}

object Mono {
    val code = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val raw = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
}
