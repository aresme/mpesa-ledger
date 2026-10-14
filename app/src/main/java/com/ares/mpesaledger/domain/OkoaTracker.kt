package com.ares.mpesaledger.domain

import java.time.LocalDate

/** Okoa Jahazi airtime credit: what's owed now and by when. */
data class OkoaSummary(val owedCents: Long, val dueDate: LocalDate?, val loans: Int, val lastActivity: Long?) {
    val hasHistory: Boolean get() = loans > 0 || lastActivity != null
}

/**
 * Replays Okoa borrows and repayments in time order. A borrow message states the total debt
 * ("Your debt of Ksh X"), which becomes the anchor; repayments ("X KSH has been deducted")
 * subtract. Pure logic; the due date comes from "should be paid by dd-MM-yyyy".
 */
object OkoaTracker {
    data class Event(val isBorrow: Boolean, val amountCents: Long, val feeCents: Long, val debtCents: Long?, val due: LocalDate?, val timestamp: Long?)

    fun summarize(events: List<Event>): OkoaSummary {
        var owed = 0L
        var due: LocalDate? = null
        for (e in events.sortedBy { it.timestamp ?: Long.MAX_VALUE }) {
            if (e.isBorrow) {
                owed = e.debtCents ?: (owed + e.amountCents + e.feeCents)
                due = e.due ?: due
            } else {
                owed = (owed - e.amountCents).coerceAtLeast(0)
                if (owed == 0L) due = null
            }
        }
        return OkoaSummary(owed, due, events.count { it.isBorrow }, events.mapNotNull { it.timestamp }.maxOrNull())
    }

    /** "due 2026-10-02" (stored in the account column) -> date. */
    fun dueOf(account: String?): LocalDate? = account?.removePrefix("due ")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
