package com.ares.mpesaledger.parser

import com.ares.mpesaledger.data.LedgerRepository
import com.ares.mpesaledger.sms.SmsFileReader
import org.junit.Assert.assertEquals
import org.junit.Test

class FileImportTest {

    @Test fun `termux json keeps M-Pesa and known financial senders only`() {
        val json = """[
            {"threadid":1,"type":"inbox","read":true,"number":"MPESA","received":"2026-10-01 09:00","body":"A1"},
            {"threadid":2,"type":"inbox","read":true,"number":"M-PESA","body":"A2"},
            {"threadid":3,"type":"inbox","read":true,"number":"Safaricom","body":"bundle"},
            {"threadid":6,"type":"inbox","read":true,"number":"BETIKA","body":"bet"},
            {"threadid":4,"type":"inbox","read":true,"sender":"mpesa","body":"A3"},
            {"threadid":5,"type":"inbox","read":true,"body":"no sender"}
        ]"""
        val parsed = SmsFileReader.parse(json)
        assertEquals(listOf("A1", "A2", "bundle", "A3", "no sender"), parsed.map { it.body })
        assertEquals("Safaricom", parsed[2].sender) // routed to the Safaricom parser on import
    }

    @Test fun `plain text is split on blank lines`() {
        assertEquals(listOf("one", "two\nlines"), SmsFileReader.parse("one\n\ntwo\nlines\n").map { it.body })
    }

    @Test fun `sample fixture imports through the file path`() {
        val text = checkNotNull(javaClass.classLoader!!.getResource("sample_sms.txt")).readText()
        val results = SmsFileReader.parse(text).map { MpesaParser.parse(it.body) }
        assertEquals(17, results.count { it is ParseResult.Transaction })
    }

    @Test fun `LIKE escaping`() {
        assertEquals("50\\%\\_a\\\\b", LedgerRepository.escapeLike("50%_a\\b"))
    }
}
