package com.ares.mpesaledger.domain

import com.ares.mpesaledger.parser.FulizaDetails
import com.ares.mpesaledger.parser.MpesaParser
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** One Fuliza loan or repayment, oldest first. */
data class FulizaEvent(
    val code: String,
    val isLoan: Boolean,
    val amountCents: Long,
    val feeCents: Long,
    val timestamp: Long?,
    val details: FulizaDetails,
)

data class FulizaSummary(
    /** Best estimate of what's owed now: the last loan's "outstanding" figure, minus repayments since. */
    val owedCents: Long,
    val dueDate: LocalDate?,
    val limitCents: Long?,
    val lastClearedAt: Long?,
    val feesThisMonthCents: Long,
    val feesThisYearCents: Long,
    val borrowedThisYearCents: Long,
    val repaidThisYearCents: Long,
    val events: List<FulizaEvent>,
) {
    val hasHistory: Boolean get() = events.isNotEmpty()
    fun daysUntilDue(today: LocalDate): Long? = dueDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
}

/**
 * Replays loans and repayments in time order. Safaricom's own "outstanding" figure on each loan
 * message is the anchor (it includes the access fee and earlier balance); a full repayment
 * resets to zero; a partial one subtracts. Daily maintenance charges aren't in the SMS, so the
 * figure can be slightly below what Safaricom shows — the screen says so.
 */
object FulizaTracker {
    fun summarize(events: List<FulizaEvent>, today: LocalDate = LocalDate.now(MpesaParser.NAIROBI)): FulizaSummary {
        val sorted = events.sortedWith(compareBy<FulizaEvent> { it.timestamp ?: Long.MAX_VALUE }.thenBy { !it.isLoan })
        var owed = 0L
        var due: LocalDate? = null
        var limit: Long? = null
        var cleared: Long? = null
        for (e in sorted) {
            if (e.isLoan) {
                owed = e.details.outstandingCents ?: (owed + e.amountCents + e.feeCents)
                due = e.details.dueDate ?: due
            } else {
                limit = e.details.limitCents ?: limit
                if (e.details.fullyRepaid == true) {
                    owed = 0; due = null; cleared = e.timestamp
                } else {
                    owed = (owed - e.amountCents).coerceAtLeast(0)
                    if (owed == 0L) { due = null; cleared = e.timestamp }
                }
            }
        }
        val month = YearMonth.from(today)
        fun inMonth(e: FulizaEvent) = e.timestamp?.let { YearMonth.from(local(it)) } == month
        fun inYear(e: FulizaEvent) = e.timestamp?.let { local(it).year } == today.year
        return FulizaSummary(
            owedCents = owed,
            dueDate = due,
            limitCents = limit,
            lastClearedAt = cleared,
            feesThisMonthCents = events.filter { it.isLoan && inMonth(it) }.sumOf { it.feeCents },
            feesThisYearCents = events.filter { it.isLoan && inYear(it) }.sumOf { it.feeCents },
            borrowedThisYearCents = events.filter { it.isLoan && inYear(it) }.sumOf { it.amountCents },
            repaidThisYearCents = events.filter { !it.isLoan && inYear(it) }.sumOf { it.amountCents },
            events = sorted.reversed(),
        )
    }

    private fun local(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(MpesaParser.NAIROBI).toLocalDate()
}
