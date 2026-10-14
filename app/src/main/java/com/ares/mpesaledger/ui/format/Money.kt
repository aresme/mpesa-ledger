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

/**
 * User-typed shillings ("5000", "5,000", "1,250.5", "99.99") -> cents, exactly (no floats).
 * Returns null for anything else, including more than two decimals.
 */
fun parseKshToCents(input: String): Long? {
    val text = input.trim().replace(",", "").replace(" ", "")
    val m = Regex("""^(\d{1,12})(?:\.(\d{1,2}))?$""").matchEntire(text) ?: return null
    val whole = m.groupValues[1].toLong()
    val frac = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
    return whole * 100 + frac
}
