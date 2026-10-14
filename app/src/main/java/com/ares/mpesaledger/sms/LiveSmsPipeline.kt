package com.ares.mpesaledger.sms

import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.notify.Notifications
import com.ares.mpesaledger.parser.Direction
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.parser.ParseResult
import com.ares.mpesaledger.parser.RawSms
import com.ares.mpesaledger.ui.format.formatKsh
import com.ares.mpesaledger.ui.format.kindLabel

/**
 * One path for "a new M-Pesa SMS arrived": used by the real SMS_RECEIVED receiver and by the
 * debug DEBUG_SMS receiver, so simulated messages test exactly what real ones do.
 * The live receiver is a convenience; the inbox scan on open remains the source of truth.
 */
class LiveSmsPipeline(private val container: AppContainer) {

    enum class Origin { LIVE, DEBUG }

    suspend fun onSms(body: String, receivedAt: Long, origin: Origin) {
        // Real SMS must never land in the demo DB; the next real-mode inbox scan picks it up.
        if (origin == Origin.LIVE && container.isDemo) {
            AppLog.d { "live sms ignored: demo mode" }
            return
        }
        val parsed = MpesaParser.parse(body)
        val result = container.importer.import(listOf(RawSms(body, date = receivedAt)), source = "live sms ($origin)")
        when (parsed) {
            ParseResult.NotTransaction -> AppLog.d { "live sms ($origin): not a transaction" }
            is ParseResult.Unparsed -> AppLog.d { "live sms ($origin): unparsed ${parsed.code}" }
            is ParseResult.Transaction -> {
                val tx = parsed.tx
                AppLog.d { "live sms ($origin): ${tx.code} ${tx.kind.key} inserted=${result.inserted}" }
                if (result.inserted > 0) {
                    notifyTransaction(tx.code, tx.kind.key)
                    container.budgetChecker.check()
                }
            }
        }
    }

    private suspend fun notifyTransaction(code: String, kind: String) {
        if (!container.prefs.notifyTransactions) return
        val row = container.database.transactions().get(code, kind) ?: return
        val sign = when (row.direction) { Direction.IN.key -> "+"; Direction.OUT.key -> "−"; else -> "" }
        Notifications.post(
            context = container.appContext,
            channel = Notifications.CHANNEL_TRANSACTIONS,
            id = (code + kind).hashCode(),
            title = "$sign${formatKsh(row.amountCents)} · ${row.counterparty}",
            text = "${kindLabel(row.kind)} · ${row.categoryOverride ?: row.category}",
            publicTitle = "New M-Pesa transaction",
        )
    }
}
