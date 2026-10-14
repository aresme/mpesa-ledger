package com.ares.mpesaledger.domain

import com.ares.mpesaledger.parser.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    private val defaults = Categorizer(DefaultRules.all)

    @Test fun `matches keyword case-insensitively as a substring`() {
        assertEquals("Groceries", defaults.categorize(Kind.TILL, "Naivas Supermarket Kilimani", null))
    }

    @Test fun `matches against the account as well`() {
        assertEquals("Electricity", defaults.categorize(Kind.PAYBILL, "PREPAID TOKENS", "KPLC-12345"))
    }

    @Test fun `user rules beat defaults even when shorter`() {
        val c = Categorizer(DefaultRules.all + CategoryRule("bolt", "Business", isUser = true))
        assertEquals("Business", c.categorize(Kind.TILL, "BOLT", null))
    }

    @Test fun `longest keyword wins within a group`() {
        val c = Categorizer(
            listOf(
                CategoryRule("SAFARICOM", "Phone", isUser = false),
                CategoryRule("SAFARICOM HOME", "Internet", isUser = false),
            )
        )
        assertEquals("Internet", c.categorize(Kind.PAYBILL, "SAFARICOM HOME FIBRE", null))
        assertEquals("Phone", c.categorize(Kind.PAYBILL, "SAFARICOM POSTPAID", null))
    }

    @Test fun `falls back by kind`() {
        val none = Categorizer(emptyList())
        val expected = mapOf(
            Kind.RECEIVED to "Income", Kind.DEPOSIT to "Cash in", Kind.WITHDRAW to "Cash out",
            Kind.AIRTIME to "Airtime", Kind.SENT to "People", Kind.FULIZA to "Fuliza",
            Kind.SAVINGS to "Savings", Kind.TILL to "Uncategorized", Kind.PAYBILL to "Uncategorized",
        )
        expected.forEach { (kind, category) -> assertEquals(kind.key, category, none.categorize(kind, "X", null)) }
    }
}
