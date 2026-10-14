package com.ares.mpesaledger.domain

import com.ares.mpesaledger.parser.MpesaParser
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** An outgoing payment that could be part of a bill (pay bill, buy goods, send money). */
data class Payment(val kind: String, val counterparty: String, val account: String?, val amountCents: Long, val timestamp: Long)

enum class BillStatus { OVERDUE, DUE_SOON, UPCOMING, PAID }

data class Bill(
    /** Stable identity: name (+ account for pay bills) + kind, uppercase. Used for hiding and reminders. */
    val key: String,
    val name: String,
    val account: String?,
    val kind: String,
    val usualDay: Int,
    val usualAmountCents: Long,
    val monthsSeen: Int,
    val lastPaidAt: Long,
    val lastAmountCents: Long,
    val nextDue: LocalDate,
    val status: BillStatus,
    /** Negative when the expected day has passed this month. */
    val daysUntilDue: Long,
)

/**
 * Finds repeat payments. A bill is the same payee (and account, for pay bills) seen in at least
 * [MIN_MONTHS] of the last [LOOKBACK_MONTHS] months, usually paid at most twice a month — so
 * weekly shopping at the same supermarket doesn't count. Pure logic: unit-tested on the JVM.
 */
object BillDetector {
    const val LOOKBACK_MONTHS = 6
    const val MIN_MONTHS = 3
    const val MAX_PAYMENTS_PER_MONTH = 2
    /** Reminders and "due soon" start this many days before the usual day. */
    const val DUE_SOON_DAYS = 2
    /** Not paid this many days after the usual day = overdue. */
    const val OVERDUE_AFTER_DAYS = 3

    /** GlobalPay is here for card subscriptions (streaming, ChatGPT, …). */
    private val BILL_KINDS = setOf("paybill", "till", "sent", "globalpay")

    fun key(kind: String, counterparty: String, account: String?): String =
        listOf(kind, counterparty.trim().uppercase(), if (kind == "paybill") account?.trim()?.uppercase().orEmpty() else "")
            .joinToString("|")

    fun detect(payments: List<Payment>, today: LocalDate = LocalDate.now(MpesaParser.NAIROBI)): List<Bill> {
        val thisMonth = YearMonth.from(today)
        val firstMonth = thisMonth.minusMonths((LOOKBACK_MONTHS - 1).toLong())
        return payments
            .filter { it.kind in BILL_KINDS }
            .map { it to local(it.timestamp) }
            .filter { (_, d) -> YearMonth.from(d) in firstMonth..thisMonth }
            .groupBy { (p, _) -> key(p.kind, p.counterparty, p.account) }
            .mapNotNull { (key, group) -> toBill(key, group, today, thisMonth) }
            .sortedWith(compareBy<Bill> { it.status.ordinal }.thenBy { it.nextDue })
    }

    private fun toBill(key: String, group: List<Pair<Payment, LocalDate>>, today: LocalDate, thisMonth: YearMonth): Bill? {
        val byMonth = group.groupBy { (_, d) -> YearMonth.from(d) }
        if (byMonth.size < MIN_MONTHS) return null
        if (median(byMonth.values.map { it.size.toLong() }) > MAX_PAYMENTS_PER_MONTH) return null

        val usualDay = median(byMonth.values.map { ps -> ps.minOf { it.second.dayOfMonth }.toLong() }).toInt()
        val usualAmount = median(byMonth.values.map { ps -> ps.sumOf { it.first.amountCents } })
        val last = group.maxBy { it.first.timestamp }
        val paidThisMonth = thisMonth in byMonth

        val dueThisMonth = thisMonth.atDay(minOf(usualDay, thisMonth.lengthOfMonth()))
        val nextMonth = thisMonth.plusMonths(1)
        val nextDue = if (paidThisMonth) nextMonth.atDay(minOf(usualDay, nextMonth.lengthOfMonth())) else dueThisMonth
        val days = ChronoUnit.DAYS.between(today, nextDue)
        val status = when {
            paidThisMonth -> BillStatus.PAID
            days < -OVERDUE_AFTER_DAYS -> BillStatus.OVERDUE
            days <= DUE_SOON_DAYS -> BillStatus.DUE_SOON
            else -> BillStatus.UPCOMING
        }
        return Bill(
            key = key,
            name = last.first.counterparty,
            account = last.first.account.takeIf { last.first.kind == "paybill" },
            kind = last.first.kind,
            usualDay = usualDay,
            usualAmountCents = usualAmount,
            monthsSeen = byMonth.size,
            lastPaidAt = last.first.timestamp,
            lastAmountCents = last.first.amountCents,
            nextDue = nextDue,
            status = status,
            daysUntilDue = days,
        )
    }

    /** Lower median: for an even count it picks the smaller middle value (a whole day/cent). */
    private fun median(values: List<Long>): Long = values.sorted()[(values.size - 1) / 2]

    private fun local(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(MpesaParser.NAIROBI).toLocalDate()
}
