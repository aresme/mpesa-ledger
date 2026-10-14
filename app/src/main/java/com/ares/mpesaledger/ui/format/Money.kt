package com.ares.mpesaledger.ui.format

import kotlin.math.absoluteValue

/** Cents -> "1,234.56" (or "−1,234.56"). Locale-independent; formatting happens only in UI. */
fun formatCents(cents: Long): String {
    val abs = cents.absoluteValue
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (cents < 0) "−" else ""
    return "$sign$whole.${(abs % 100).toString().padStart(2, '0')}"
}
