package com.ares.mpesaledger.ui.theme

import androidx.compose.ui.graphics.Color

// ares_sys brand on a modern dark UI. Green is the accent (selection, money in, CTAs),
// not body text. Amber/red are reserved for warnings and over-budget states.
val AresBlack = Color(0xFF000000)
val AresGreen = Color(0xFF4DFF7A)
val AresGreenContainer = Color(0xFF12301C)
val AresSurface = Color(0xFF0E1210)
val AresSurfaceHigh = Color(0xFF161B18)
val AresSurfaceHighest = Color(0xFF1E2420)
val AresOutline = Color(0xFF262D29)
val AresForeground = Color(0xFFEDF2EE)
val AresMuted = Color(0xFF8E9A92)
val AresAmber = Color(0xFFFFB547)
val AresRed = Color(0xFFFF5A5F)

// Chart series, validated with the dataviz palette checker against AresSurface (dark):
// lightness band, chroma floor, CVD and normal-vision separation, contrast >= 3:1 all pass.
// The brand green itself is too light to be a chart mark next to a second series.
val ChartIncome = Color(0xFF24AD55)
val ChartSpending = Color(0xFF6688EA)
