package com.ares.mpesaledger.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.MpesaLedgerApp
import com.ares.mpesaledger.parser.MpesaParser
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Daily bill check at about 09:00 Nairobi time. Inexact on purpose: no exact-alarm
 * permission, and Android batches it with other wake-ups to save battery. Scheduling is
 * idempotent (same PendingIntent), so it's safe to call on every app start.
 */
object BillReminderScheduler {
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now(MpesaParser.NAIROBI)
        var first = ZonedDateTime.of(LocalDate.now(MpesaParser.NAIROBI), LocalTime.of(9, 0), MpesaParser.NAIROBI)
        if (!first.isAfter(now)) first = first.plusDays(1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, BillReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Fired by the daily alarm (not exported). */
class BillReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as MpesaLedgerApp).container
        val pending = goAsync()
        container.appScope.launch {
            try { container.billReminder.check() } catch (e: Exception) { AppLog.w({ "bill check failed" }, e) } finally { pending.finish() }
        }
    }
}

/** Alarms don't survive a reboot; re-arm the daily check. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BillReminderScheduler.schedule(context)
    }
}
