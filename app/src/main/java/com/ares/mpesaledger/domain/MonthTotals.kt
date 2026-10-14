package com.ares.mpesaledger.domain

/**
 * Dashboard stat block for one month, all in cents. Fees include every fee in the month
 * (Fuliza access fees too); Fuliza and savings are reported separately, never as in/out.
 */
data class MonthTotals(
    val inCents: Long,
    val outCents: Long,
    val feeCents: Long,
    val fulizaCents: Long,
    val savedCents: Long,
) {
    val netCents: Long get() = inCents - outCents - feeCents
}
