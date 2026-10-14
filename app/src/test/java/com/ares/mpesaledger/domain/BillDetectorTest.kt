package com.ares.mpesaledger.domain

import com.ares.mpesaledger.parser.MpesaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BillDetectorTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun pay(kind: String, name: String, account: String?, ksh: Long, y: Int, m: Int, d: Int) =
        Payment(kind, name, account, ksh * 100, LocalDateTime.of(y, m, d, 9, 0).atZone(MpesaParser.NAIROBI).toInstant().toEpochMilli())

    @Test fun `monthly pay bill not yet paid this month is overdue after the grace days`() {
        val kplc = (7..9).map { pay("paybill", "KPLC PREPAID", "37184529011", 500, 2026, it, 5) }
        val bill = BillDetector.detect(kplc, today).single()
        assertEquals("KPLC PREPAID", bill.name)
        assertEquals("37184529011", bill.account)
        assertEquals(5, bill.usualDay)
        assertEquals(50000L, bill.usualAmountCents)
        assertEquals(LocalDate.of(2026, 10, 5), bill.nextDue)
        assertEquals(-9L, bill.daysUntilDue)
        assertEquals(BillStatus.OVERDUE, bill.status)
    }

    @Test fun `paid this month moves the next due date to next month`() {
        val school = (8..10).map { pay("paybill", "KABARAK UNIVERSITY", "CS-0412-25", 1500, 2026, it, 2) }
        val bill = BillDetector.detect(school, today).single()
        assertEquals(BillStatus.PAID, bill.status)
        assertEquals(LocalDate.of(2026, 11, 2), bill.nextDue)
    }

    @Test fun `due soon within two days, upcoming further out`() {
        val rent = (7..9).map { pay("sent", "LANDLORD", null, 8000, 2026, it, 16) }
        assertEquals(BillStatus.DUE_SOON, BillDetector.detect(rent, today).single().status)
        val zuku = (7..9).map { pay("paybill", "ZUKU", "889123", 2999, 2026, it, 28) }
        assertEquals(BillStatus.UPCOMING, BillDetector.detect(zuku, today).single().status)
    }

    @Test fun `needs three of the last six months`() {
        val two = listOf(pay("paybill", "ZUKU", "1", 2999, 2026, 9, 28), pay("paybill", "ZUKU", "1", 2999, 2026, 8, 28))
        assertTrue(BillDetector.detect(two, today).isEmpty())
        val tooOld = (1..3).map { pay("paybill", "ZUKU", "1", 2999, 2026, it, 28) } // Jan-Mar, outside the window
        assertTrue(BillDetector.detect(tooOld, today).isEmpty())
    }

    @Test fun `weekly shopping is not a bill`() {
        val naivas = (7..10).flatMap { m -> listOf(3, 10, 17, 24).map { d -> pay("till", "NAIVAS", null, 900, 2026, m, d) } }
        assertTrue(BillDetector.detect(naivas, today).isEmpty())
    }

    @Test fun `same pay bill with different accounts are different bills`() {
        val a = (7..9).map { pay("paybill", "KPLC PREPAID", "111", 500, 2026, it, 5) }
        val b = (7..9).map { pay("paybill", "KPLC PREPAID", "222", 700, 2026, it, 20) }
        assertEquals(2, BillDetector.detect(a + b, today).size)
    }

    @Test fun `usual day clamps to short months and amounts use the median month`() {
        val gym = listOf(
            pay("till", "GYM", null, 2000, 2026, 7, 31), pay("till", "GYM", null, 2500, 2026, 8, 31),
            pay("till", "GYM", null, 2000, 2026, 9, 30),
        )
        val bill = BillDetector.detect(gym, LocalDate.of(2026, 11, 1)).single()
        assertEquals(31, bill.usualDay)
        assertEquals(LocalDate.of(2026, 11, 30), bill.nextDue) // November has 30 days
        assertEquals(200000L, bill.usualAmountCents)
    }

    @Test fun `overdue first, then due soon, upcoming, paid`() {
        val bills = BillDetector.detect(
            (7..9).map { pay("paybill", "KPLC", "1", 500, 2026, it, 5) } +            // overdue
                (7..9).map { pay("sent", "LANDLORD", null, 8000, 2026, it, 16) } +    // due soon
                (8..10).map { pay("paybill", "SCHOOL", "2", 1500, 2026, it, 2) } +    // paid
                (7..9).map { pay("paybill", "ZUKU", "3", 2999, 2026, it, 28) },       // upcoming
            today,
        )
        assertEquals(listOf("KPLC", "LANDLORD", "ZUKU", "SCHOOL"), bills.map { it.name })
    }
}
