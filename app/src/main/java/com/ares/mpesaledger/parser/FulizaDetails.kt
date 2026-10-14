package com.ares.mpesaledger.parser

import java.time.LocalDate
import java.util.regex.Pattern

/**
 * Extra facts carried by Fuliza messages, read from the raw SMS on demand (only a handful of
 * rows), so no schema change is needed:
 *  - loan:      "Total Fuliza M-Pesa outstanding amount is Ksh194.43 due on 25/10/26."
 *  - repayment: "...used to fully|partially pay..." and "Available Fuliza M-PESA limit is Ksh 500.00"
 */
data class FulizaDetails(
    val outstandingCents: Long? = null,
    val dueDate: LocalDate? = null,
    val limitCents: Long? = null,
    /** true = fully repaid, false = partial, null = not a repayment message. */
    val fullyRepaid: Boolean? = null,
) {
    companion object {
        private const val AMT = """([\d,]+\.\d{2})"""
        private val OUTSTANDING = Pattern.compile(
            """outstanding amount is Ksh\s?$AMT(?: due on (\d{1,2})/(\d{1,2})/(\d{2,4}))?""", Pattern.CASE_INSENSITIVE
        )
        private val LIMIT = Pattern.compile("""Fuliza M-PESA limit is Ksh\s?$AMT""", Pattern.CASE_INSENSITIVE)
        private val REPAY = Pattern.compile("""used to (fully|partially) pay""", Pattern.CASE_INSENSITIVE)

        fun parse(raw: String): FulizaDetails {
            val text = MpesaParser.collapseWhitespace(raw)
            var outstanding: Long? = null
            var due: LocalDate? = null
            OUTSTANDING.matcher(text).takeIf { it.find() }?.let { m ->
                outstanding = MpesaParser.toCents(m.group(1)!!)
                if (m.group(2) != null) due = date(m.group(2)!!, m.group(3)!!, m.group(4)!!)
            }
            val limit = LIMIT.matcher(text).takeIf { it.find() }?.let { MpesaParser.toCents(it.group(1)!!) }
            val full = REPAY.matcher(text).takeIf { it.find() }?.let { it.group(1)!!.equals("fully", ignoreCase = true) }
            return FulizaDetails(outstanding, due, limit, full)
        }

        private fun date(d: String, m: String, y: String): LocalDate? = runCatching {
            val year = when (y.length) { 2 -> 2000 + y.toInt(); 4 -> y.toInt(); else -> return null }
            LocalDate.of(year, m.toInt(), d.toInt())
        }.getOrNull()
    }
}
