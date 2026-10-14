package com.ares.mpesaledger.domain

/** Per-month aggregate row (month = "yyyy-MM" in Nairobi time), all amounts in cents. */
data class MonthSummary(
    val month: String,
    val inCents: Long,
    val outCents: Long,
    val feeCents: Long,
    val fulizaCents: Long,
    val savedCents: Long,
) {
    val netCents: Long get() = inCents - outCents - feeCents
}
