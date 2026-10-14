package com.ares.mpesaledger.domain

import android.content.Context
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.data.AppPrefs
import com.ares.mpesaledger.data.LedgerRepository
import com.ares.mpesaledger.notify.Notifications
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.ui.format.formatKsh
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Posts one reminder per bill per due date, [BillDetector.DUE_SOON_DAYS] days ahead.
 * Runs from the daily alarm and after every inbox sync / live SMS (XOS can drop alarms for
 * apps it has stopped, so opening the app also catches up).
 */
class BillReminder(
    private val context: Context,
    private val repository: LedgerRepository,
    private val prefs: AppPrefs,
) {
    private val mutex = Mutex()

    suspend fun check(today: LocalDate = LocalDate.now(MpesaParser.NAIROBI)): List<Bill> = mutex.withLock {
        val bills = repository.bills()
        AppLog.d { "bills detected: ${bills.size} " + bills.groupingBy { it.status }.eachCount() }
        if (!prefs.billReminders) return emptyList()
        val hidden = prefs.hiddenBills.value
        val due = bills.filter {
            it.key !in hidden && it.status == BillStatus.DUE_SOON && it.daysUntilDue in 0..BillDetector.DUE_SOON_DAYS.toLong()
        }
        // Forget reminders for due dates more than ~2 months old so the set stays small.
        val notified = prefs.notifiedBills.filter { entry ->
            runCatching { LocalDate.parse(entry.substringAfterLast('|')) }.getOrNull()?.isAfter(today.minusDays(60)) == true
        }.toMutableSet()
        val fresh = due.filter { "${it.key}|${it.nextDue}" !in notified }
        fresh.forEach { bill ->
            post(bill)
            notified += "${bill.key}|${bill.nextDue}"
        }
        prefs.notifiedBills = notified
        if (fresh.isNotEmpty()) AppLog.d { "bill reminders posted: ${fresh.size}" }
        fresh
    }

    private fun post(bill: Bill) {
        val whenText = when (bill.daysUntilDue) {
            0L -> "due today"
            1L -> "due tomorrow"
            else -> "due ${bill.nextDue.format(DAY)}"
        }
        Notifications.post(
            context = context,
            channel = Notifications.CHANNEL_BILLS,
            id = ("bill:" + bill.key).hashCode(),
            title = "${bill.name} $whenText",
            text = "Usually ${formatKsh(bill.usualAmountCents)} around the ${ordinal(bill.usualDay)}" +
                (bill.account?.let { " · account $it" } ?: ""),
            publicTitle = "M-Pesa bill reminder",
        )
    }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        fun ordinal(day: Int): String = day.toString() + when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}
