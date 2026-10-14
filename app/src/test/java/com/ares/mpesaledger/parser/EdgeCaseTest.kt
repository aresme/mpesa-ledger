package com.ares.mpesaledger.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Synthetic messages (no real data) covering formats and quirks beyond the fixture. */
class EdgeCaseTest {

    @Test fun `amounts over a million with multiple commas`() {
        val tx = parseTx(
            "QAB1234567 Confirmed. Ksh1,250,000.00 sent to ACME LTD for account 991 on 1/2/26 at 9:00 AM " +
                "New M-PESA balance is Ksh12,345,678.90. Transaction cost, Ksh108.00."
        )
        assertEquals(Kind.PAYBILL, tx.kind)
        assertEquals(125_000_000L, tx.amountCents)
        assertEquals(1_234_567_890L, tx.balanceCents)
        assertEquals(10_800L, tx.feeCents)
        assertEquals("991", tx.account)
    }

    @Test fun `Ksh with a space before the amount`() {
        val tx = parseTx(
            "QAB1234568 Confirmed.You bought Ksh 50.00 of airtime on 6/9/26 at 7:30 AM." +
                "New M-PESA balance is Ksh 2,170.50. Transaction cost, Ksh 0.00."
        )
        assertEquals(Kind.AIRTIME, tx.kind)
        assertEquals(5_000L, tx.amountCents)
        assertEquals(217_050L, tx.balanceCents)
        assertEquals(0L, tx.feeCents)
    }

    @Test fun `PMWithdraw with no space after the time`() {
        val tx = parseTx(
            "QAB1234569 Confirmed.on 3/3/26 at 4:30 PMWithdraw Ksh2,500.00 from 123456 - MAMA MBOGA SHOP " +
                "New M-PESA balance is Ksh10.00. Transaction cost, Ksh51.00."
        )
        assertEquals(Kind.WITHDRAW, tx.kind)
        assertEquals("2026-03-03 16:30", nairobi(tx.timestamp))
        assertEquals("MAMA MBOGA SHOP", tx.counterparty)
        assertEquals("123456", tx.account)
        assertEquals(5_100L, tx.feeCents)
    }

    @Test fun `lowercase confirmed and lowercase code`() {
        val tx = parseTx("qab123456a confirmed.You bought Ksh100.00 of airtime on 6/9/26 at 7:30 AM.")
        assertEquals("QAB123456A", tx.code)
        assertEquals(Kind.AIRTIME, tx.kind)
    }

    @Test fun `four digit year`() {
        val tx = parseTx(
            "QAB1234570 Confirmed.You have received Ksh3,000.00 from JOHN DOE 0712***678 on 2/9/2026 at 10:15 AM " +
                "New M-PESA balance is Ksh3,420.50."
        )
        assertEquals("2026-09-02 10:15", nairobi(tx.timestamp))
    }

    @Test fun `message split across newlines and double spaces`() {
        val body = "QAB1234571 Confirmed.\nYou have received Ksh3,000.00 from JOHN\n  DOE 0712***678 on\n2/9/26 at 10:15\nAM  " +
            "New M-PESA\nbalance is Ksh3,420.50."
        val tx = parseTx(body)
        assertEquals(Kind.RECEIVED, tx.kind)
        assertEquals("JOHN DOE", tx.counterparty)
        assertEquals("0712***678", tx.phone)
        assertEquals("2026-09-02 10:15", nairobi(tx.timestamp))
        assertEquals(342_050L, tx.balanceCents)
        assertEquals(body.trim(), tx.raw) // raw keeps the original line breaks
    }

    @Test fun `no-break spaces are treated as whitespace`() {
        val tx = parseTx("QAB1234572 Confirmed. Ksh250.00 paid to BOLT. on 8/10/26 at 8:20 PM.")
        assertEquals(Kind.TILL, tx.kind)
        assertEquals("BOLT", tx.counterparty)
    }

    @Test fun `code-bearing unknown format lands in unparsed`() {
        val body = "QAB1234573 Confirmed. Reversal of transaction XYZ0000000 has been successfully reversed " +
            "on 1/1/26 at 1:00 PM and Ksh100.00 is credited to your M-PESA account."
        val result = MpesaParser.parse(body)
        assertEquals(ParseResult.Unparsed("QAB1234573", body), result)
    }

    @Test fun `messages without a code are not transactions`() {
        assertEquals(ParseResult.NotTransaction, MpesaParser.parse("Get 1GB valid 24hrs for Ksh 50 only. Dial *544# now!"))
        assertEquals(ParseResult.NotTransaction, MpesaParser.parse("Dear customer, your Fuliza limit is Ksh500.00."))
        assertEquals(ParseResult.NotTransaction, MpesaParser.parse(""))
    }

    @Test fun `12 AM and 12 PM`() {
        assertEquals("2026-01-01 00:05", nairobi(MpesaParser.parseWhen("on 1/1/26 at 12:05 AM")))
        assertEquals("2026-01-01 12:30", nairobi(MpesaParser.parseWhen("on 1/1/26 at 12:30 PM")))
        assertEquals("2026-01-01 23:59", nairobi(MpesaParser.parseWhen("on 1/1/26 at 11:59PM")))
    }

    @Test fun `impossible dates and 3-digit years give no timestamp instead of failing`() {
        assertNull(MpesaParser.parseWhen("on 31/2/26 at 1:00 PM"))
        assertNull(MpesaParser.parseWhen("on 1/2/026 at 1:00 PM"))
        assertNull(MpesaParser.parseWhen("on 1/2/26 at 13:00 PM"))
        val tx = parseTx("QAB1234574 Confirmed. Ksh250.00 paid to BOLT. on 31/2/26 at 8:20 PM.")
        assertNull(tx.timestamp)
    }

    @Test fun `international phone suffix is split off`() {
        val tx = parseTx("QAB1234575 Confirmed. Ksh400.00 sent to JANE DOE +254712345678 on 9/9/26 at 1:20 PM.")
        assertEquals("JANE DOE", tx.counterparty)
        assertEquals("+254712345678", tx.phone)
    }

    @Test fun `paybill wins over plain sent`() {
        val tx = parseTx("QAB1234576 Confirmed. Ksh500.00 sent to KPLC PREPAID for account 123 on 5/9/26 at 8:02 AM")
        assertEquals(Kind.PAYBILL, tx.kind)
        assertEquals("KPLC PREPAID", tx.counterparty)
    }

    @Test fun `withdraw without an agent number keeps the whole name`() {
        val tx = parseTx("QAB1234577 Confirmed.on 3/3/26 at 4:30 PMWithdraw Ksh200.00 from CITY STAR AGENCIES New M-PESA balance is Ksh1.00.")
        assertEquals("CITY STAR AGENCIES", tx.counterparty)
        assertNull(tx.account)
    }

    @Test fun `toCents is exact`() {
        assertEquals(1L, MpesaParser.toCents("0.01"))
        assertEquals(19_250L, MpesaParser.toCents("192.50"))
        assertEquals(99_999_999_999L, MpesaParser.toCents("999,999,999.99"))
    }

    @Test fun `split blocks handles CRLF and trailing blank lines`() {
        val blocks = SmsText.splitBlocks("a\r\nb\r\n\r\nc\n \n\n\nd\n\n")
        assertEquals(listOf("a\r\nb", "c", "d"), blocks)
    }

    private fun parseTx(body: String): ParsedTransaction {
        val result = MpesaParser.parse(body)
        assertTrue("expected a transaction, got $result", result is ParseResult.Transaction)
        return (result as ParseResult.Transaction).tx
    }

    private fun nairobi(millis: Long?): String =
        millis?.let { Instant.ofEpochMilli(it).atZone(MpesaParser.NAIROBI).format(MINUTES) } ?: "-"

    private companion object {
        val MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
