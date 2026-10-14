package com.ares.mpesaledger.domain

import java.time.YearMonth

/** Budget thresholds. Pure logic so the "notify at most once per level per month" rule is unit-tested. */
object BudgetRules {
    const val WARN = 80
    const val OVER = 100

    /** 0, 80 or 100 for [spentCents] against [limitCents]. Integer maths, no rounding surprises. */
    fun level(spentCents: Long, limitCents: Long): Int = when {
        limitCents <= 0 -> 0
        spentCents >= limitCents -> OVER
        spentCents * 100 >= limitCents * WARN -> WARN
        else -> 0
    }

    /**
     * The level to notify now, or null. A new month resets what was already notified; jumping
     * straight from 0 to 100 sends only the 100% alert.
     */
    fun levelToNotify(current: Int, lastLevel: Int, lastMonth: Int?, month: YearMonth): Int? {
        val already = if (lastMonth == key(month)) lastLevel else 0
        return current.takeIf { it > already && it > 0 }
    }

    fun key(month: YearMonth): Int = month.year * 100 + month.monthValue
}
