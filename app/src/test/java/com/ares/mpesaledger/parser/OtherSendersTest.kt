package com.ares.mpesaledger.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Shapes copied from the user's real (masked) messages, 2026-10-15; numbers and names are fictional. */
class OtherSendersTest {
    private val received = 1_790_000_000_000L

    private fun tx(source: Source, body: String, at: Long? = received): ParsedTransaction {
        val r = OtherSmsParser.parse(source, body, at)
        assertTrue("expected a transaction, got $r", r is ParseResult.Transaction)
        return (r as ParseResult.Transaction).tx
    }

    @Test fun `sources match real sender names`() {
        assertEquals(Source.OKOA, Source.of("Okoa Jahazi"))
        assertEquals(Source.SAFARICOM, Source.of("SAFARICOM"))
        assertEquals(Source.SAFARICOM, Source.of("Safaricom"))
        assertEquals(Source.GLOBALPAY, Source.of("Globalpay"))
        assertEquals(Source.MPESA, Source.of("M-PESA"))
        assertNull(Source.of("BETIKA"))
        assertNull(Source.of("AirtelAlert"))
    }

    @Test fun `GlobalPay card payment counts as money out with forex as the fee`() {
        val t = tx(Source.GLOBALPAY, "Dear JOHN, a transaction of Ksh. 2,600.00 (inclusive of Ksh. 78.00 forex charge) done at OPENAI *CHATGPT SUBSCR has been approved on your card ****1234. If not yours, contact us for assistance via; X (@Safaricom_Care, @SafaricomPLC), Facebook (@SafaricomPLC), or by calling 100 or 200.")
        assertEquals(Kind.GLOBALPAY, t.kind)
        assertEquals(Direction.OUT, t.direction)
        assertEquals(252_200L, t.amountCents)
        assertEquals(7_800L, t.feeCents)
        assertEquals("OPENAI *CHATGPT SUBSCR", t.counterparty)
        assertTrue(t.code.startsWith("S") && t.code.length == 10)
    }

    @Test fun `GlobalPay card notices are not transactions`() {
        assertEquals(ParseResult.NotTransaction, OtherSmsParser.parse(Source.GLOBALPAY, "Dear customer, your M-PESA GlobalPay Virtual VISA card has been created successfully.", received))
        assertEquals(ParseResult.NotTransaction, OtherSmsParser.parse(Source.GLOBALPAY, "Dear customer, your transaction OTP is 123456. Kindly ensure that you verify the transaction details.", received))
    }

    @Test fun `Okoa Jahazi borrow, chap chap and repayment`() {
        val b = tx(Source.OKOA, "You have received Ksh20.00. The fee is KSH 2.00.Your debt of Ksh 22.00 should be paid by 02-10-2026.Your limit OKOA JAHAZI is 50.00. You can now Okoa Minutes to Call.")
        assertEquals(Kind.OKOA_BORROW, b.kind)
        assertEquals(Direction.NONE, b.direction)
        assertEquals(2_000L, b.amountCents)
        assertEquals(200L, b.feeCents)
        assertEquals(2_200L, b.balanceCents)
        assertEquals("due 2026-10-02", b.account)
        val c = tx(Source.OKOA, "Your Okoa Chap Chap request was successful. Your call costs through OKOA was Ksh 5.00 to be paid by 2026-10-04. To check balance dial *131*9#")
        assertEquals(Kind.OKOA_BORROW, c.kind)
        assertEquals("due 2026-10-04", c.account)
        val r = tx(Source.OKOA, "22.00 KSH has been deducted to repay your Okoa Jahazi. Dial *131*1# to check your outstanding balances.")
        assertEquals(Kind.OKOA_REPAY, r.kind)
        assertEquals(2_200L, r.amountCents)
        assertEquals(ParseResult.NotTransaction, OtherSmsParser.parse(Source.OKOA, "You can now successfully borrow airtime. Please Dial *131# and Okoa Jahazi.", received))
    }

    @Test fun `Safaricom data bundle and airtime recharge`() {
        val d = tx(Source.SAFARICOM, "Thank you for purchasing S-Hook Hourly 1024.0 MB. Expiry date: 13/10/2026 11:45AM. Click here to Enjoy Free Games on us https://games.example.com.")
        assertEquals(Kind.DATA_BUNDLE, d.kind)
        assertEquals(0L, d.amountCents)
        assertEquals("S-Hook Hourly 1024.0 MB", d.counterparty)
        assertEquals("expires 13/10/2026 11:45 AM", d.account)
        val d2 = tx(Source.SAFARICOM, "Thank You For Purchasing 250.0 MB hourly data bundle. Expiry date 06-10-2026 11:39PM. Drama iko real!")
        assertEquals("250.0 MB hourly data bundle", d2.counterparty)
        val rc = tx(Source.SAFARICOM, "Recharge of 50.00 KSH was successful. Balance:63.50 KSH,expiry date:2027-01-06.Tariff: S-Hook. Lipa na Bonga!")
        assertEquals(Kind.AIRTIME_RECHARGE, rc.kind)
        assertEquals(5_000L, rc.amountCents)
        assertEquals(6_350L, rc.balanceCents)
        assertEquals(ParseResult.NotTransaction, OtherSmsParser.parse(Source.SAFARICOM, "Get 1GB for only Ksh 20, valid for 1HR! Dial *544#", received))
    }

    @Test fun `KCB money received and M-Pesa deposit`() {
        val k = tx(Source.KCB, "TJ12AB34CD Confirmed! You have received KES 5,000.00 from JANE DOE - 071****678 at 2026-09-30 03:27:07 PM via KCB.")
        assertEquals(Kind.BANK_IN, k.kind)
        assertEquals(Direction.NONE, k.direction)
        assertEquals("TJ12AB34CD", k.code)
        assertEquals("JANE DOE", k.counterparty)
        assertEquals("KCB · 071****678", k.account)
        assertEquals("2026-09-30 15:27", nairobi(k.timestamp))
        val dep = tx(Source.KCB, "Ksh 300.00 sent to KCB account JOHN 1234567 has been received on 17/08/2026 at 05:01 PM. M-PESA Ref UHHQ00AAA1. To reverse this transaction, SMS this message to 22522.")
        assertEquals(Kind.BANK_IN, dep.kind)
        assertEquals("UHHQ00AAA1", dep.code)
        assertEquals("2026-08-17 17:01", nairobi(dep.timestamp))
    }

    @Test fun `Ziidi withdrawal and e-top-up`() {
        val z = tx(Source.ZIIDI, "You have successfully withdrawn Ksh. 1,500.00 of transaction code UGFQ00ABC1. Your Ziidi balance is Ksh. 3.20.")
        assertEquals(Kind.SAVINGS_WITHDRAW, z.kind)
        assertEquals("UGFQ00ABC1", z.code)
        assertEquals(150_000L, z.amountCents)
        val e = tx(Source.ETOPUP, "254:R261013.1408.5500a2 confirmed. You have received Ksh. 20.")
        assertEquals(Kind.AIRTIME_RECEIVED, e.kind)
        assertEquals(2_000L, e.amountCents)
    }

    @Test fun `synthetic codes are stable per message and differ by received time`() {
        val body = "22.00 KSH has been deducted to repay your Okoa Jahazi."
        assertEquals(tx(Source.OKOA, body, 1L).code, tx(Source.OKOA, body, 1L).code)
        assertNotEquals(tx(Source.OKOA, body, 1L).code, tx(Source.OKOA, body, 2L).code)
    }

    @Test fun `flexible amounts`() {
        assertEquals(2_000L, OtherSmsParser.cents("20"))
        assertEquals(950L, OtherSmsParser.cents("9.5"))
        assertEquals(123_456L, OtherSmsParser.cents("1,234.56"))
    }

    private fun nairobi(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(MpesaParser.NAIROBI).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }
}
