package com.ares.mpesaledger.parser

import com.ares.mpesaledger.data.CategoryRuleEntity
import com.ares.mpesaledger.data.CsvExport
import com.ares.mpesaledger.data.RulesJson
import com.ares.mpesaledger.data.toEntity
import com.ares.mpesaledger.ui.format.parseKshToCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringWriter

class ExportTest {

    @Test fun `csv has the prototype columns and exact amounts`() {
        val text = checkNotNull(javaClass.classLoader!!.getResource("sample_sms.txt")).readText()
        val rows = SmsText.splitBlocks(text).map(MpesaParser::parse)
            .filterIsInstance<ParseResult.Transaction>().map { it.tx.toEntity("Cat", null) }
        val out = StringWriter().also { CsvExport.write(rows, it) }.toString().split("\r\n")
        assertEquals("code,kind,direction,amount,cost,balance,counterparty,phone,account,ts,category", out[0])
        assertEquals("TJA1B2C3D4,received,in,3000.00,,3420.50,JOHN KAMAU,0712***678,,2026-09-02T10:15,Cat", out[1])
        assertEquals("TJA8I9J0K1,fuliza,none,192.50,1.93,,Fuliza,,,,Cat", out[8])
    }

    @Test fun `csv quoting and formula guard`() {
        assertEquals("\"A, B\"", CsvExport.escape("A, B"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        assertEquals("'=HYPERLINK(1)", CsvExport.escape("=HYPERLINK(1)"))
        assertEquals("-12.50", CsvExport.escape("-12.50")) // negative numbers stay numbers
        assertEquals("-12.50", CsvExport.decimal(-1250))
        assertEquals("1000000.05", CsvExport.decimal(100000005))
    }

    @Test fun `rules json round trip keeps only user rules`() {
        val json = RulesJson.encode(listOf(
            CategoryRuleEntity(1, "NAIVAS", "Groceries", isUser = false),
            CategoryRuleEntity(2, "MAMA MBOGA", "Groceries", isUser = true),
        ))
        assertEquals(listOf("MAMA MBOGA" to "Groceries"), RulesJson.decode(json))
        assertEquals(listOf("BOLT" to "Work"), RulesJson.decode("""{"rules":[{"keyword":" bolt ","category":"Work"},{"keyword":"","category":"x"}]}"""))
    }

    @Test fun `typed amounts parse exactly`() {
        assertEquals(500000L, parseKshToCents("5000"))
        assertEquals(500000L, parseKshToCents("5,000"))
        assertEquals(125050L, parseKshToCents("1,250.5"))
        assertEquals(9999L, parseKshToCents("99.99"))
        assertNull(parseKshToCents("1.234"))
        assertNull(parseKshToCents("abc"))
        assertNull(parseKshToCents(""))
    }
}
