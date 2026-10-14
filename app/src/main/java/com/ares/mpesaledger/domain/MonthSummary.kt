package com.ares.mpesaledger.domain

/** Per-month aggregate row (month = "yyyy-MM" in Nairobi time), all amounts in cents. */
data class MonthSummary(
    val month: String,
    val inCents: Long,
    val outCents: Long,
    val feeCents: Long,
    val fulizaCents: Long,
    val fulizaRepaidCents: Long,
    val savedCents: Long,
    /** Moved back out of M-Shwari / KCB M-PESA this month. Last, with a default, so older call sites stay valid. */
    val savingsWithdrawnCents: Long = 0,
) {
    val netCents: Long get() = inCents - outCents - feeCents
}
