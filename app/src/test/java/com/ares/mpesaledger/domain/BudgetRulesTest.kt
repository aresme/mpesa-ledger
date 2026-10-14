package com.ares.mpesaledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class BudgetRulesTest {
    private val oct = YearMonth.of(2026, 10)
    private val octKey = BudgetRules.key(oct)

    @Test fun `levels at the boundaries`() {
        assertEquals(0, BudgetRules.level(399_999, 500_000))
        assertEquals(80, BudgetRules.level(400_000, 500_000))   // exactly 80%
        assertEquals(80, BudgetRules.level(499_999, 500_000))
        assertEquals(100, BudgetRules.level(500_000, 500_000))  // exactly 100%
        assertEquals(0, BudgetRules.level(100, 0))               // no limit, no alerts
    }

    @Test fun `each level notifies once per month`() {
        assertEquals(80, BudgetRules.levelToNotify(80, lastLevel = 0, lastMonth = null, month = oct))
        assertNull(BudgetRules.levelToNotify(80, lastLevel = 80, lastMonth = octKey, month = oct))
        assertEquals(100, BudgetRules.levelToNotify(100, lastLevel = 80, lastMonth = octKey, month = oct))
        assertNull(BudgetRules.levelToNotify(100, lastLevel = 100, lastMonth = octKey, month = oct))
    }

    @Test fun `jumping straight past 100 sends only the 100 alert`() {
        assertEquals(100, BudgetRules.levelToNotify(100, lastLevel = 0, lastMonth = null, month = oct))
    }

    @Test fun `a new month resets`() {
        assertEquals(80, BudgetRules.levelToNotify(80, lastLevel = 100, lastMonth = BudgetRules.key(YearMonth.of(2026, 9)), month = oct))
        assertNull(BudgetRules.levelToNotify(0, lastLevel = 0, lastMonth = null, month = oct))
    }
}
