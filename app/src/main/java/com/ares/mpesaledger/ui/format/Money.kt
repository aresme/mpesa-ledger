package com.ares.mpesaledger.ui.format

import kotlin.math.absoluteValue

/** Cents -> "1,234.56" (or "−1,234.56"). Locale-independent; formatting happens only in UI. */
fun formatCents(cents: Long): String {
    val abs = cents.absoluteValue
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (cents < 0) "−" else ""
    return "$sign$whole.${(abs % 100).toString().padStart(2, '0')}"
}

fun formatKsh(cents: Long): String = "Ksh ${formatCents(cents)}"

/** "+1,234.00" / "−1,234.00" for list rows. */
fun formatSigned(cents: Long, positive: Boolean): String =
    (if (positive) "+" else "−") + formatCents(cents.absoluteValue)

/** Axis labels: 950, 1.2K, 35K, 1.5M. */
fun formatCompact(cents: Long): String {
    val ksh = cents / 100.0
    return when {
        ksh >= 1_000_000 -> trim(ksh / 1_000_000) + "M"
        ksh >= 1_000 -> trim(ksh / 1_000) + "K"
        else -> ksh.toLong().toString()
    }
}

private fun trim(v: Double): String =
    if (v >= 10) v.toLong().toString() else ((v * 10).toLong() / 10.0).toString().removeSuffix(".0")
