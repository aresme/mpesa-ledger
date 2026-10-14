package com.ares.mpesaledger.parser

import com.ares.mpesaledger.domain.Categorizer
import com.ares.mpesaledger.domain.DefaultRules
import com.ares.mpesaledger.domain.MonthTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Spec §5.6: sample_sms.txt with default rules only must produce exactly this ledger. */
class FixtureTest {

    private val blocks = SmsText.splitBlocks(
        checkNotNull(javaClass.classLoader!!.getResource("sample_sms.txt")).readText()
    )
    private val results = blocks.map(MpesaParser::parse)
    private val categorizer = Categorizer(DefaultRules.all)

    private val expected = listOf(
        Row("TJA1B2C3D4", "received", "in", "3000.00", null, "JOHN KAMAU", "0712***678", null, "2026-09-02 10:15", "Income"),
        Row("TJA2C3D4E5", "till", "out", "650.00", "0.00", "NAIVAS SUPERMARKET", null, null, "2026-09-03 18:40", "Groceries"),
        Row("TJA3D4E5F6", "paybill", "out", "500.00", "0.00", "KPLC PREPAID", null, "37184529011", "2026-09-05 08:02", "Electricity"),
        Row("TJA4E5F6G7", "airtime", "out", "100.00", "0.00", "Airtime", null, null, "2026-09-06 07:30", "Airtime"),
        Row("TJA5F6G7H8", "sent", "out", "400.00", "7.00", "MERCY WANJIKU", "0722***145", null, "2026-09-09 13:20", "People"),
        Row("TJA6G7H8I9", "withdraw", "out", "1000.00", "29.00", "CITY STAR AGENCIES", null, "286412", "2026-09-14 11:05", "Cash out"),
        Row("TJA7H8I9J0", "till", "out", "320.00", "0.00", "JAVA HOUSE", null, null, "2026-09-20 12:45", "Food out"),
        Row("TJA8I9J0K1", "fuliza", "none", "192.50", "1.93", "Fuliza", null, null, "2026-09-25 21:12", "Fuliza"),
        Row("TJA8I9J0K1", "sent", "out", "600.00", "7.00", "BRIAN OTIENO", "0711***902", null, "2026-09-25 21:12", "People"),
        Row("TJB1J0K1L2", "received", "in", "4500.00", null, "JOHN KAMAU", "0712***678", null, "2026-10-01 09:00", "Income"),
        Row("TJB2K1L2M3", "paybill", "out", "1500.00", "0.00", "KABARAK UNIVERSITY", null, "CS-0412-25", "2026-10-02 10:30", "School"),
        Row("TJB3L2M3N4", "till", "out", "890.00", "0.00", "QUICKMART", null, null, "2026-10-04 17:55", "Groceries"),
        Row("TJB4M3N4O5", "deposit", "in", "1000.00", null, "CITY STAR AGENCIES", null, null, "2026-10-06 15:10", "Cash in"),
        Row("TJB5N4O5P6", "till", "out", "250.00", "0.00", "BOLT", null, null, "2026-10-08 20:20", "Transport"),
        Row("TJB6O5P6Q7", "airtime", "out", "50.00", "0.00", "Airtime", null, null, "2026-10-10 07:05", "Airtime"),
        Row("TJB7P6Q7R8", "sent", "out", "300.00", "7.00", "MERCY WANJIKU", "0722***145", null, "2026-10-12 16:40", "People"),
        Row("TJB9X8Y7Z6", "savings", "none", "200.00", null, "M-Shwari", null, null, "2026-10-13 16:00", "Savings"),
    )

    @Test fun `every row matches the spec table`() {
        val ledger = FakeLedger().apply { insertAll(transactions()); inheritFulizaTimestamps() }
        val actual = ledger.rows.map { tx ->
            Row(
                tx.code, tx.kind.key, tx.direction.key, cents(tx.amountCents), tx.feeCents?.let(::cents),
                tx.counterparty, tx.phone, tx.account, nairobi(tx.timestamp),
                categorizer.categorize(tx.kind, tx.counterparty, tx.account),
            )
        }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals("row ${e.code}/${e.kind}", e, a) }
    }

    @Test fun `promo is skipped and nothing is unparsed`() {
        assertEquals(18, blocks.size)
        assertEquals(1, results.count { it is ParseResult.NotTransaction })
        assertEquals(0, results.count { it is ParseResult.Unparsed })
        val promo = blocks.single { it.startsWith("Get 1GB") }
        assertEquals(ParseResult.NotTransaction, MpesaParser.parse(promo))
    }

    @Test fun `import inserts 17 then re-import inserts 0`() {
        val ledger = FakeLedger()
        assertEquals(17, ledger.insertAll(transactions()))
        assertEquals(0, ledger.insertAll(transactions()))
        assertEquals(17, ledger.rows.size)
    }

    @Test fun `fuliza has no date of its own and inherits from its payment`() {
        val fuliza = transactions().single { it.kind == Kind.FULIZA }
        assertNull(fuliza.timestamp)
        val ledger = FakeLedger().apply { insertAll(transactions()); inheritFulizaTimestamps() }
        val row = ledger.rows.single { it.kind == Kind.FULIZA }
        assertEquals("2026-09-25 21:12", nairobi(row.timestamp))
    }

    @Test fun `savings balance uses the improved regex`() {
        val savings = transactions().single { it.code == "TJB9X8Y7Z6" }
        assertEquals(210857L, savings.balanceCents)
    }

    @Test fun `september totals`() {
        assertEquals(
            MonthTotals(inCents = 300000, outCents = 357000, feeCents = 4493, fulizaCents = 19250, fulizaRepaidCents = 0, savedCents = 0),
            totalsFor(YearMonth.of(2026, 9)),
        )
        assertEquals(-61493L, totalsFor(YearMonth.of(2026, 9)).netCents)
    }

    @Test fun `october totals`() {
        assertEquals(
            MonthTotals(inCents = 550000, outCents = 299000, feeCents = 700, fulizaCents = 0, fulizaRepaidCents = 0, savedCents = 20000),
            totalsFor(YearMonth.of(2026, 10)),
        )
        assertEquals(250300L, totalsFor(YearMonth.of(2026, 10)).netCents)
    }

    private fun transactions() = results.filterIsInstance<ParseResult.Transaction>().map { it.tx }

    private fun totalsFor(month: YearMonth): MonthTotals {
        val ledger = FakeLedger().apply { insertAll(transactions()); inheritFulizaTimestamps() }
        return ledger.monthTotals(month)
    }

    private data class Row(
        val code: String, val kind: String, val dir: String, val amount: String, val fee: String?,
        val counterparty: String, val phone: String?, val account: String?, val time: String, val category: String,
    )

    private companion object {
        val MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun cents(c: Long) = "%d.%02d".format(c / 100, c % 100)

        fun nairobi(millis: Long?): String =
            millis?.let { Instant.ofEpochMilli(it).atZone(MpesaParser.NAIROBI).format(MINUTES) } ?: "-"
    }
}
