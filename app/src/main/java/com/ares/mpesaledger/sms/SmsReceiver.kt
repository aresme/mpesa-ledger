package com.ares.mpesaledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.MpesaLedgerApp
import kotlinx.coroutines.launch

/**
 * Manifest-declared SMS_RECEIVED receiver (exempt from implicit-broadcast limits). The
 * manifest requires android.permission.BROADCAST_SMS, so only the telephony stack can send it.
 *
 * Long M-Pesa messages arrive as multipart SMS: all parts come in one intent, in order, and
 * are concatenated before parsing. goAsync() keeps the receiver alive while the coroutine
 * parses and writes (well under the ~10 s broadcast limit).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val messages = parts.groupBy { it.originatingAddress }
            .filterKeys { InboxReader.isMpesaSender(it) }
            .map { (_, ps) -> ps.joinToString("") { it.messageBody.orEmpty() } to ps.first().timestampMillis }
        if (messages.isEmpty()) return

        val container = (context.applicationContext as MpesaLedgerApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                messages.forEach { (body, at) -> container.livePipeline.onSms(body, at, LiveSmsPipeline.Origin.LIVE) }
            } catch (e: Exception) {
                AppLog.w({ "live sms failed" }, e)
            } finally {
                pending.finish()
            }
        }
    }
}
