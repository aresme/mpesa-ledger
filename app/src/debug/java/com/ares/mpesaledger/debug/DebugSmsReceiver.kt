package com.ares.mpesaledger.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.MpesaLedgerApp
import com.ares.mpesaledger.sms.LiveSmsPipeline
import kotlinx.coroutines.launch

/**
 * Debug-only stand-in for SMS_RECEIVED: `--es body "<sms>"` (optional `--el at <epochMillis>`)
 * goes through exactly the same LiveSmsPipeline as a real message.
 */
class DebugSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val body = intent.getStringExtra("body")
        if (body.isNullOrBlank()) {
            AppLog.w({ "DEBUG_SMS without --es body" })
            return
        }
        val at = intent.getLongExtra("at", System.currentTimeMillis())
        val container = (context.applicationContext as MpesaLedgerApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.livePipeline.onSms(body, at, LiveSmsPipeline.Origin.DEBUG)
            } catch (e: Exception) {
                AppLog.w({ "debug sms failed" }, e)
            } finally {
                pending.finish()
            }
        }
    }
}
