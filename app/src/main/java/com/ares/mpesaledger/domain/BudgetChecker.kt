package com.ares.mpesaledger.domain

import android.content.Context
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.data.AppDatabase
import com.ares.mpesaledger.data.MonthRange
import com.ares.mpesaledger.notify.Notifications
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.ui.format.formatKsh
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

/**
 * Compares this month's spending per category with its budget and posts at most one alert
 * per level (80%, 100%) per category per month. Runs after new transactions arrive.
 */
class BudgetChecker(
    private val context: Context,
    private val db: AppDatabase,
    private val now: () -> YearMonth = { YearMonth.now(MpesaParser.NAIROBI) },
) {
    private val mutex = Mutex()

    /** Returns the alerts that were due (for logs/tests); posting is skipped if notifications are off. */
    suspend fun check(): List<Alert> = mutex.withLock {
        val month = now()
        val budgets = db.budgets().getAll()
        if (budgets.isEmpty()) return emptyList()
        val range = MonthRange.of(month)
        val spent = db.transactions().categorySpending(range.start, range.end).associate { it.category to it.totalCents }

        budgets.mapNotNull { b ->
            val spentCents = spent[b.category] ?: 0
            val level = BudgetRules.level(spentCents, b.monthlyLimitCents)
            val notify = BudgetRules.levelToNotify(level, b.lastNotifiedLevel, b.lastNotifiedMonth, month) ?: return@mapNotNull null
            db.budgets().markNotified(b.category, notify, BudgetRules.key(month))
            Alert(b.category, notify, spentCents, b.monthlyLimitCents).also(::post)
        }
    }

    private fun post(alert: Alert) {
        AppLog.d { "budget alert: ${alert.category} ${alert.level}%" }
        val title = if (alert.level >= BudgetRules.OVER) "${alert.category} is over budget" else "${alert.category} at ${alert.level}% of budget"
        Notifications.post(
            context = context,
            channel = Notifications.CHANNEL_BUDGETS,
            id = ("budget:" + alert.category).hashCode(),
            title = title,
            text = "${alert.category} at ${alert.level}% of ${formatKsh(alert.limitCents)} this month (${formatKsh(alert.spentCents)} spent)",
            publicTitle = "M-Pesa budget alert",
        )
    }

    data class Alert(val category: String, val level: Int, val spentCents: Long, val limitCents: Long)
}
