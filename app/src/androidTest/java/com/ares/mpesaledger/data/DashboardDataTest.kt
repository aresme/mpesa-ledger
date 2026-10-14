package com.ares.mpesaledger.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ares.mpesaledger.domain.MonthTotals
import com.ares.mpesaledger.parser.RawSms
import com.ares.mpesaledger.parser.SmsText
import com.ares.mpesaledger.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** The exact flows the Dashboard and Activity screens render, against the §5.6 fixture. */
@RunWith(AndroidJUnit4::class)
class DashboardDataTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)

    @Before fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        repo = LedgerRepository(db)
        val text = context.assets.open("sample_sms.txt").use { it.readBytes().decodeToString() }
        LedgerImporter(db).import(SmsText.splitBlocks(text).map { RawSms(it) }, "test")
        Unit
    }

    @After fun tearDown() = db.close()

    @Test fun statBlockFigures() = runBlocking {
        assertEquals(MonthTotals(300000, 357000, 4493, 19250, 0, 0), repo.observeTotals(sep).first())
        assertEquals(MonthTotals(550000, 299000, 700, 0, 0, 20000), repo.observeTotals(oct).first())
    }

    @Test fun categorySpendingIsOutOnlyAndSorted() = runBlocking {
        val octCats = repo.observeCategorySpending(oct).first().map { it.category to it.totalCents }
        assertEquals(listOf("School" to 150000L, "Groceries" to 89000L, "People" to 30000L, "Transport" to 25000L, "Airtime" to 5000L), octCats)
        val sepCats = repo.observeCategorySpending(sep).first().associate { it.category to it.totalCents }
        assertEquals(100000L, sepCats["People"]) // 400 + 600
        assertEquals(357000L, sepCats.values.sum()) // equals Out
    }

    @Test fun topRecipients() = runBlocking {
        val top = repo.observeTopRecipients(sep).first()
        assertEquals(5, top.size)
        assertEquals("CITY STAR AGENCIES" to 100000L, top[0].counterparty to top[0].totalCents)
        val mercy = repo.observeTopRecipients(YearMonth.of(2026, 9), limit = 10).first().single { it.counterparty == "MERCY WANJIKU" }
        assertEquals(1, mercy.count)
    }

    @Test fun trendHasTwelveZeroFilledMonths() = runBlocking {
        val trend = DashboardViewModel.trendFor(oct, repo.observeMonthSummaries().first())
        assertEquals(12, trend.size)
        assertEquals("Oct", trend.last().label)
        assertEquals(550000L to 299700L, trend.last().incomeCents to trend.last().spendingCents) // out + fees
        assertEquals(300000L to 361493L, trend[10].incomeCents to trend[10].spendingCents)
        assertEquals(0L, trend.take(10).sumOf { it.incomeCents + it.spendingCents })
    }

    @Test fun activityFiltersAndSearch() = runBlocking {
        assertEquals(17, repo.observeList(ListFilter()).first().size)
        assertEquals(listOf("TJA2C3D4E5"), repo.observeList(ListFilter(month = sep, category = "Groceries")).first().map { it.code })
        assertEquals(2, repo.observeList(ListFilter(query = "mercy")).first().size)
        assertEquals(2, repo.observeList(ListFilter(kind = "airtime")).first().size)
        assertEquals(1, repo.observeList(ListFilter(query = "CS-0412")).first().size) // account search
        assertEquals(0, repo.observeList(ListFilter(query = "%")).first().size)         // LIKE wildcard is escaped
    }

    @Test fun wipeEmptiesEverythingAndReseedsDefaults() = runBlocking {
        repo.setBudget("Groceries", 100000)
        repo.saveUserRule(null, "bolt", "Work")
        repo.wipe()
        val counts = repo.observeCounts().first()
        assertEquals(0, counts.transactions)
        assertEquals(21, counts.rules)               // defaults back, user rule gone
        assertEquals(0, repo.observeBudgets().first().size)
        assertEquals(null, db.importState().get())   // next inbox sync starts from scratch
    }

    @Test fun csvExportCoversEveryRowWithEffectiveCategory() = runBlocking {
        repo.setOverride("TJA7H8I9J0", "till", "Treats")
        val out = java.io.StringWriter()
        assertEquals(17, repo.exportCsv(out))
        val lines = out.toString().trim().split("\r\n")
        assertEquals(18, lines.size)
        assertEquals(true, lines.any { it.startsWith("TJA7H8I9J0,till,out,320.00,0.00,414.50,JAVA HOUSE,,,2026-09-20T12:45,Treats") })
    }

    @Test fun alwaysCategorizeCreatesRuleAndOverridesSurviveRecategorize() = runBlocking {
        repo.setOverride("TJA7H8I9J0", "till", "Treats")                 // manual override on JAVA HOUSE
        repo.addUserRuleAndRecategorize("MERCY WANJIKU", "Family", "TJA5F6G7H8", "sent")
        val tx = db.transactions()
        assertEquals("Family", tx.effectiveCategory("TJA5F6G7H8", "sent"))
        assertEquals("Family", tx.effectiveCategory("TJB7P6Q7R8", "sent"))  // the other MERCY row too
        assertEquals("Treats", tx.effectiveCategory("TJA7H8I9J0", "till"))  // override survived
        repo.recategorizeAll()
        assertEquals("Treats", tx.effectiveCategory("TJA7H8I9J0", "till"))
    }
}
