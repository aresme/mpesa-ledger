package com.ares.mpesaledger.parser

import com.ares.mpesaledger.ui.components.niceTicks
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.formatCompact
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `cents formatting`() {
        assertEquals("0.00", formatCents(0))
        assertEquals("1,234,567.89", formatCents(123456789))
        assertEquals("−614.93", formatCents(-61493))
        assertEquals("0.05", formatCents(5))
    }

    @Test fun `compact axis labels`() {
        assertEquals("950", formatCompact(95000))
        assertEquals("1.2K", formatCompact(123000))
        assertEquals("35K", formatCompact(3500000))
        assertEquals("1.5M", formatCompact(150000000))
    }

    @Test fun `nice ticks cover the max with whole-shilling steps`() {
        assertEquals(listOf(0L, 200000L, 400000L, 600000L), niceTicks(550000))
        assertEquals(listOf(0L, 100L, 200L, 300L), niceTicks(0))
    }
}
