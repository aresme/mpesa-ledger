package com.ares.mpesaledger.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.MpesaLedgerApp
import com.ares.mpesaledger.data.ListFilter
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.parser.RawSms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.random.Random

/**
 * Debug-only performance test: `am broadcast -a com.ares.mpesaledger.DEBUG_PERF --ei count 5000`.
 * Generates realistic synthetic M-Pesa SMS (all kinds, Fuliza pairs, promos, an unknown
 * format) spread over the last 12 months, imports them in one go, then times the queries the
 * dashboard and activity screens run. Demo mode only: never touches the real ledger.
 */
class PerfReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as MpesaLedgerApp).container
        if (!container.isDemo) {
            AppLog.w({ "perf: refused, demo mode is off" })
            return
        }
        val count = intent.getIntExtra("count", 5000)
        val pending = goAsync()
        container.appScope.launch {
            try {
                val messages = SyntheticSms.generate(count, seed = 42)
                val result = container.importer.import(messages, source = "perf")
                AppLog.d { "perf: import of ${messages.size} msgs: inserted=${result.inserted} unparsed=${result.unparsed} skipped=${result.skipped} in ${result.durationMs} ms" }

                val repo = container.repository
                val month = YearMonth.now(MpesaParser.NAIROBI)
                suspend fun <T> timed(label: String, block: suspend () -> T): T {
                    val t0 = System.nanoTime()
                    val r = block()
                    AppLog.d { "perf: $label ${(System.nanoTime() - t0) / 1_000_000} ms" }
                    return r
                }
                val t0 = System.nanoTime()
                timed("totals") { repo.observeTotals(month).first() }
                timed("categories") { repo.observeCategorySpending(month).first() }
                timed("top recipients") { repo.observeTopRecipients(month).first() }
                timed("12-month summaries") { repo.observeMonthSummaries().first() }
                val all = timed("activity list (all)") { repo.observeList(ListFilter()).first() }
                timed("activity search") { repo.observeList(ListFilter(query = "naivas")).first() }
                AppLog.d { "perf: dashboard+activity queries total ${(System.nanoTime() - t0) / 1_000_000} ms, ${all.size} rows" }
            } catch (e: Exception) {
                AppLog.w({ "perf failed" }, e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Synthetic messages in the same shapes as sample_sms.txt (fictional names and codes). */
object SyntheticSms {
    private val people = listOf("JOHN KAMAU", "MERCY WANJIKU", "BRIAN OTIENO", "AMINA HASSAN", "PETER MWANGI", "GRACE ACHIENG", "KEVIN KIPROP", "FAITH NJERI")
    private val tills = listOf("NAIVAS SUPERMARKET", "QUICKMART", "JAVA HOUSE", "BOLT", "KFC", "CARREFOUR", "MAMA MBOGA SHOP", "RUBIS KAREN")
    private val paybills = listOf("KPLC PREPAID" to "3718452901", "KABARAK UNIVERSITY" to "CS-0412-25", "ZUKU FIBER" to "889123", "SAFARICOM HOME" to "0712000111")

    fun generate(count: Int, seed: Int): List<RawSms> {
        val rnd = Random(seed)
        val start = LocalDateTime.now(MpesaParser.NAIROBI).minusMonths(12)
        val out = ArrayList<RawSms>(count)
        var i = 0
        while (out.size < count) {
            i++
            val t = start.plusMinutes(rnd.nextLong(0, 365L * 24 * 60))
            val code = "PF" + i.toString(36).uppercase().padStart(8, '0')
            val on = "on ${t.dayOfMonth}/${t.monthValue}/${t.year % 100} at ${(t.hour + 11) % 12 + 1}:${"%02d".format(t.minute)} ${if (t.hour < 12) "AM" else "PM"}"
            val amt = ksh(rnd.nextLong(50, 25_000) * 100 + listOf(0, 50).random(rnd))
            val bal = ksh(rnd.nextLong(0, 80_000) * 100 + rnd.nextLong(0, 100))
            val fee = ksh(listOf(0L, 700, 1300, 2900, 5100).random(rnd))
            val body = when (rnd.nextInt(100)) {
                in 0..29 -> "$code Confirmed. Ksh$amt paid to ${tills.random(rnd)}. $on.New M-PESA balance is Ksh$bal. Transaction cost, Ksh0.00."
                in 30..44 -> paybills.random(rnd).let { (n, a) -> "$code Confirmed. Ksh$amt sent to $n for account $a $on New M-PESA balance is Ksh$bal. Transaction cost, Ksh0.00." }
                in 45..59 -> "$code Confirmed. Ksh$amt sent to ${people.random(rnd)} 0712***${rnd.nextInt(100, 999)} $on. New M-PESA balance is Ksh$bal. Transaction cost, Ksh$fee."
                in 60..71 -> "$code Confirmed.You have received Ksh$amt from ${people.random(rnd)} 0722***${rnd.nextInt(100, 999)} $on  New M-PESA balance is Ksh$bal."
                in 72..77 -> "$code Confirmed.$on${"Withdraw"} Ksh$amt from ${rnd.nextInt(100000, 999999)} - CITY STAR AGENCIES New M-PESA balance is Ksh$bal. Transaction cost, Ksh$fee."
                in 78..81 -> "$code Confirmed. On ${on.removePrefix("on ")} Give Ksh$amt cash to CITY STAR AGENCIES New M-PESA balance is Ksh$bal."
                in 82..87 -> "$code confirmed.You bought Ksh${ksh(rnd.nextLong(10, 500) * 100)} of airtime $on.New M-PESA balance is Ksh$bal."
                in 88..90 -> "$code Confirmed. You have transferred Ksh$amt to your M-Shwari account $on. M-Shwari balance is Ksh$bal . M-PESA balance is Ksh$bal ."
                in 91..94 -> {
                    // Fuliza loan + the payment it funded share one code (two rows, one code).
                    out += RawSms("$code Confirmed. Fuliza M-Pesa amount is Ksh ${ksh(rnd.nextLong(50, 2000) * 100)}. Access Fee charged Ksh 1.93. Total Fuliza M-Pesa outstanding amount is Ksh200.00 due on 25/10/26.")
                    "$code Confirmed. Ksh$amt sent to ${people.random(rnd)} 0711***${rnd.nextInt(100, 999)} $on. New M-PESA balance is Ksh0.00. Transaction cost, Ksh$fee."
                }
                in 95..96 -> "$code Confirmed. Ksh ${ksh(rnd.nextLong(20, 500) * 100)} from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 500.00. Your M-PESA balance is $bal."
                97 -> "$code Confirmed. Reversal of a transaction $on is being processed."
                else -> "Get 1GB valid 24hrs for Ksh 50 only. Dial *544# now!"
            }
            out += RawSms(body, date = t.atZone(MpesaParser.NAIROBI).toInstant().toEpochMilli())
        }
        return out.take(count)
    }

    private fun ksh(cents: Long): String {
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$whole.${(cents % 100).toString().padStart(2, '0')}"
    }
}
