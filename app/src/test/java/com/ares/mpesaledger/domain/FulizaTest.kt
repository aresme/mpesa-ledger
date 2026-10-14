package com.ares.mpesaledger.domain

import com.ares.mpesaledger.parser.FulizaDetails
import com.ares.mpesaledger.parser.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FulizaTest {
    private val loanSms = "TJA8I9J0K1 Confirmed. Fuliza M-Pesa amount is Ksh 192.50. Access Fee charged Ksh 1.93. " +
        "Total Fuliza M-Pesa outstanding amount is Ksh194.43 due on 25/10/26. To check daily charges, Dial *234*0#OK Select Query Charges"
    private val partialSms = "QAB1234579 Confirmed. Ksh 100.00 from your M-PESA has been used to partially pay your outstanding " +
        "Fuliza M-PESA. Your available Fuliza M-PESA limit is Ksh 405.57. Your M-PESA balance is 0.00."
    private val fullSms = "QAB1234580 Confirmed. Ksh 94.43 from your M-PESA has been used to fully pay your outstanding " +
        "Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 500.00. Your M-PESA balance is 12.00."

    @Test fun `details from the loan and repayment messages`() {
        assertEquals(FulizaDetails(19443, LocalDate.of(2026, 10, 25), null, null), FulizaDetails.parse(loanSms))
        assertEquals(FulizaDetails(null, null, 40557, false), FulizaDetails.parse(partialSms))
        assertEquals(FulizaDetails(null, null, 50000, true), FulizaDetails.parse(fullSms))
    }

    @Test fun `owed follows loans and repayments`() {
        val today = LocalDate.of(2026, 10, 14)
        val loan = event("TJA8I9J0K1", true, 19250, 193, at(2026, 9, 25, 21, 12), loanSms)
        assertEquals(19443L, FulizaTracker.summarize(listOf(loan), today).owedCents)
        assertEquals(LocalDate.of(2026, 10, 25), FulizaTracker.summarize(listOf(loan), today).dueDate)
        assertEquals(11L, FulizaTracker.summarize(listOf(loan), today).daysUntilDue(today))

        val partial = event("QAB1234579", false, 10000, 0, at(2026, 10, 2, 9, 0), partialSms)
        val afterPartial = FulizaTracker.summarize(listOf(loan, partial), today)
        assertEquals(9443L, afterPartial.owedCents)
        assertEquals(40557L, afterPartial.limitCents)

        val full = event("QAB1234580", false, 9443, 0, at(2026, 10, 5, 9, 0), fullSms)
        val cleared = FulizaTracker.summarize(listOf(full, loan, partial), today) // any input order
        assertEquals(0L, cleared.owedCents)
        assertNull(cleared.dueDate)
        assertEquals(50000L, cleared.limitCents)
        assertEquals(at(2026, 10, 5, 9, 0), cleared.lastClearedAt)
        assertEquals("QAB1234580", cleared.events.first().code) // newest first
    }

    @Test fun `fees and totals per month and year`() {
        val today = LocalDate.of(2026, 10, 14)
        val s = FulizaTracker.summarize(
            listOf(
                event("A", true, 19250, 193, at(2026, 9, 25, 21, 12), loanSms),
                event("B", true, 5000, 50, at(2026, 10, 3, 8, 0), loanSms),
                event("C", false, 10000, 0, at(2026, 10, 4, 8, 0), partialSms),
            ),
            today,
        )
        assertEquals(50L, s.feesThisMonthCents)
        assertEquals(243L, s.feesThisYearCents)
        assertEquals(24250L, s.borrowedThisYearCents)
        assertEquals(10000L, s.repaidThisYearCents)
    }

    @Test fun `no history`() {
        val s = FulizaTracker.summarize(emptyList(), LocalDate.of(2026, 10, 14))
        assertEquals(0L, s.owedCents)
        assertEquals(false, s.hasHistory)
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(MpesaParser.NAIROBI).toInstant().toEpochMilli()

    private fun event(code: String, loan: Boolean, amount: Long, fee: Long, ts: Long, raw: String) =
        FulizaEvent(code, loan, amount, fee, ts, FulizaDetails.parse(raw))
}
