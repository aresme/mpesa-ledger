package com.ares.mpesaledger.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ares.mpesaledger.domain.MonthTotals
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.parser.RawSms
import com.ares.mpesaledger.parser.SmsText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/**
 * Runs the real Room SQL on the device against the §5.6 fixture (in-memory DB, so the
 * app's real and demo databases are untouched). Must agree with the JVM FakeLedger.
 */
@RunWith(AndroidJUnit4::class)
class LedgerDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var importer: LedgerImporter
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture: List<RawSms> by lazy {
        val text = context.assets.open("sample_sms.txt").use { it.readBytes().decodeToString() }
        SmsText.splitBlocks(text).map { RawSms(it) }
    }

    @Before fun setUp() {
        db = AppDatabase.inMemory(context)
        importer = LedgerImporter(db)
    }

    @After fun tearDown() = db.close()

    @Test fun importIsIdempotent() = runBlocking {
        val first = importer.import(fixture, "test")
        assertEquals(17, first.inserted)
        assertEquals(0, first.duplicates)
        assertEquals(0, first.unparsed)
        assertEquals(1, first.skipped)
        val again = importer.import(fixture, "test")
        assertEquals(0, again.inserted)
        assertEquals(17, again.duplicates)
        assertEquals(17, db.transactions().count())
    }

    @Test fun defaultRulesAreSeededAndApplied() = runBlocking {
        assertEquals(16, db.rules().getAll().size)
        importer.import(fixture, "test")
        assertEquals("Electricity", db.transactions().effectiveCategory("TJA3D4E5F6", "paybill"))
        assertEquals("Food out", db.transactions().effectiveCategory("TJA7H8I9J0", "till"))
        assertEquals("Cash out", db.transactions().effectiveCategory("TJA6G7H8I9", "withdraw"))
    }

    @Test fun fulizaInheritsTimestampInSql() = runBlocking {
        importer.import(fixture, "test")
        val fuliza = db.transactions().get("TJA8I9J0K1", "fuliza")!!
        val sent = db.transactions().get("TJA8I9J0K1", "sent")!!
        assertNotNull(sent.timestamp)
        assertEquals(sent.timestamp, fuliza.timestamp)
        assertEquals(0, db.stats().observeCounts().first().undated)
    }

    @Test fun fulizaArrivingInALaterBatchStillInherits() = runBlocking {
        val fuliza = fixture.filter { "Fuliza M-Pesa amount" in it.body }
        importer.import(fuliza, "test")
        assertEquals(1, db.stats().observeCounts().first().undated)
        importer.import(fixture - fuliza.toSet(), "test")
        assertEquals(0, db.stats().observeCounts().first().undated)
    }

    @Test fun monthTotalsMatchSpec() = runBlocking {
        importer.import(fixture, "test")
        assertEquals(MonthTotals(300000, 357000, 4493, 19250, 0, 0), totals(YearMonth.of(2026, 9)))
        assertEquals(MonthTotals(550000, 299000, 700, 0, 0, 20000), totals(YearMonth.of(2026, 10)))
        assertEquals(-61493L, totals(YearMonth.of(2026, 9)).netCents)
        assertEquals(250300L, totals(YearMonth.of(2026, 10)).netCents)
    }

    @Test fun monthSummariesGroupByNairobiMonth() = runBlocking {
        importer.import(fixture, "test")
        val months = db.transactions().observeMonthSummaries(LedgerRepository.nairobiOffsetSeconds(), 12).first()
        assertEquals(listOf("2026-10", "2026-09"), months.map { it.month })
        assertEquals(250300L, months[0].netCents)
        assertEquals(-61493L, months[1].netCents)
    }

    @Test fun balanceIsStoredForSavings() = runBlocking {
        importer.import(fixture, "test")
        assertEquals(210857L, db.transactions().get("TJB9X8Y7Z6", "savings")!!.balanceCents)
    }

    @Test fun unknownFormatGoesToUnparsedOnce() = runBlocking {
        val odd = RawSms("QAB1234573 Confirmed. Reversal of transaction XYZ0000000 on 1/1/26 at 1:00 PM.")
        assertEquals(1, importer.import(listOf(odd), "test").unparsed)
        importer.import(listOf(odd), "test")
        assertEquals(1, db.unparsed().count())
        assertEquals(0, db.transactions().count())
    }

    @Test fun fulizaRepaymentUsesSmsDateAndLeavesUnparsed() = runBlocking {
        val body = "QAB1234580 Confirmed. Ksh 50.00 from your M-PESA has been used to fully pay your " +
            "outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 500.00. Your M-PESA balance is 12.34."
        // Simulate an older parser having stored it as unparsed.
        db.unparsed().insert(UnparsedEntity(sha256Hex(body), body, 0))
        val received = 1_790_000_000_000L
        val result = importer.import(listOf(RawSms(body, smsId = 7, date = received)), "test")
        assertEquals(1, result.inserted)
        val row = db.transactions().get("QAB1234580", "fuliza_repay")!!
        assertEquals(received, row.timestamp)
        assertEquals(1234L, row.balanceCents)
        assertEquals("none", row.direction)
        assertEquals("Fuliza", db.transactions().effectiveCategory("QAB1234580", "fuliza_repay"))
        assertEquals(0, db.unparsed().count())
    }

    @Test fun fulizaLoanDoesNotTakeSmsDate() = runBlocking {
        val loan = fixture.single { "Fuliza M-Pesa amount" in it.body }.copy(date = 1L)
        importer.import(listOf(loan), "test")
        assertEquals(null, db.transactions().get("TJA8I9J0K1", "fuliza")!!.timestamp)
    }

    private fun sha256Hex(text: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private suspend fun totals(month: YearMonth): MonthTotals {
        val start = month.atDay(1).atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli()
        return db.transactions().monthTotals(start, end)
    }
}
