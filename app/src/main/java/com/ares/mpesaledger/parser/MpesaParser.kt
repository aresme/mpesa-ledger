package com.ares.mpesaledger.parser

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parses one M-Pesa confirmation SMS. Pure Kotlin/JVM: no Android imports, so it runs
 * in plain JVM unit tests. Ported from mpesa_ledger.py (spec §5), plus improvements:
 * - balance also matches "M-PESA balance is" / "Your M-PESA balance is", with or without "Ksh"
 * - withdraw agents are split into "286412" (account) and "CITY STAR AGENCIES" (counterparty)
 * - Fuliza repayments (seen on the real phone, 2026-10) are their own kind
 * - money moved back out of M-Shwari, and KCB M-PESA savings in/out (parser v3). These were
 *   added from Safaricom's documented wording, not yet from a real SMS on this phone; anything
 *   that doesn't match exactly still goes to Unparsed. Reversals and M-Shwari loans are left
 *   unparsed on purpose until a real example shows their exact wording.
 *
 * To support a new SMS format: add a [Kind] (if needed), a pattern in [patterns] at the
 * right priority, and a fixture test with a masked real message.
 */
object MpesaParser {

    /**
     * Bump whenever parsing output changes for existing messages. A higher version than the
     * one stored in import_state triggers a full inbox rescan, so old messages get re-parsed.
     */
    const val VERSION = 4

    val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")

    private const val AMT = """([\d,]+\.\d{2})"""
    private const val KSH = """Ksh\s?$AMT"""
    private const val CI = Pattern.CASE_INSENSITIVE

    private val CODE = Pattern.compile("""^\s*([A-Z0-9]{10})\s+confirmed""", CI)
    private val WHEN = Pattern.compile("""on (\d{1,2})/(\d{1,2})/(\d{2,4}) at (\d{1,2}):(\d{2})\s?([AP]M)""", CI)
    // (?<!KCB ) keeps a "KCB M-PESA balance" from being read as the M-PESA wallet balance.
    private val BALANCE = Pattern.compile("""(?<!KCB )(?:New |Your )?M-PESA balance is (?:Ksh\s?)?$AMT""", CI)
    // Savings messages write the fee as "Transaction cost Ksh.0.00".
    private val FEE = Pattern.compile("""(?:Transaction cost,?|Access Fee charged)\s*Ksh\.?\s?$AMT""", CI)
    private val PHONE_TAIL = Pattern.compile("""\s+((?:\+?254|0)[\d*]{6,12})$""")
    private val AGENT = Pattern.compile("""^(\d+)\s*-\s*(.+)$""")

    /**
     * Tried in this order; first match wins (paybill must precede plain "sent to"). Each entry
     * carries a literal phrase from its own regex: if the phrase isn't in the text the regex
     * cannot match, so the cheap contains() check skips it (same results, ~5x faster imports).
     */
    private class KindPattern(val kind: Kind, val anchor: String, regex: String, val counterparty: String? = null) {
        val pattern: Pattern = Pattern.compile(regex, CI)
    }

    private val patterns: List<KindPattern> = listOf(
        KindPattern(Kind.FULIZA, "Fuliza M-Pesa amount is", """Fuliza M-Pesa amount is $KSH"""),
        KindPattern(Kind.RECEIVED, "You have received", """You have received $KSH from (.+?) on \d"""),
        KindPattern(Kind.PAYBILL, " for account ", """$KSH sent to (.+?) for account (.+?) on \d"""),
        KindPattern(Kind.SENT, " sent to ", """$KSH sent to (.+?) on \d"""),
        KindPattern(Kind.TILL, " paid to ", """$KSH paid to (.+?)\.?\s*on \d"""),
        KindPattern(Kind.WITHDRAW, "Withdraw", """Withdraw $KSH from (.+?)\s*New M-PESA"""),
        KindPattern(Kind.DEPOSIT, " cash to ", """Give $KSH cash to (.+?)\s*New M-PESA"""),
        KindPattern(Kind.AIRTIME, " of airtime", """You bought $KSH of airtime"""),
        KindPattern(Kind.SAVINGS, " to your M-Shwari", """transferred $KSH to your M-Shwari""", "M-Shwari"),
        KindPattern(Kind.SAVINGS, " to KCB M-PESA account", """$KSH transferred to KCB M-PESA account""", "KCB M-PESA"),
        KindPattern(Kind.SAVINGS_WITHDRAW, "transferred from M-Shwari", """$KSH transferred from M-Shwari account""", "M-Shwari"),
        KindPattern(Kind.SAVINGS_WITHDRAW, "transferred from KCB M-PESA", """$KSH transferred from KCB M-PESA account""", "KCB M-PESA"),
        KindPattern(Kind.FULIZA_REPAY, "pay your outstanding Fuliza", """$KSH from your M-PESA has been used to (?:fully |partially )?pay your outstanding Fuliza"""),
    )

    fun parse(body: String): ParseResult {
        val text = collapseWhitespace(body)
        val codeMatch = CODE.matcher(text)
        if (!codeMatch.find()) return ParseResult.NotTransaction
        val code = codeMatch.group(1)!!.uppercase()
        val raw = body.trim()

        for (p in patterns) {
            if (!text.contains(p.anchor, ignoreCase = true)) continue
            val m = p.pattern.matcher(text)
            if (!m.find()) continue
            return ParseResult.Transaction(build(p.kind, m, code, text, raw, p.counterparty))
        }
        return ParseResult.Unparsed(code, raw)
    }

    private fun build(kind: Kind, m: Matcher, code: String, text: String, raw: String, fixedCounterparty: String?): ParsedTransaction {
        var counterparty: String
        var phone: String? = null
        var account: String? = null

        when {
            fixedCounterparty != null -> counterparty = fixedCounterparty
            kind == Kind.FULIZA || kind == Kind.FULIZA_REPAY -> counterparty = "Fuliza"
            kind == Kind.AIRTIME -> counterparty = "Airtime"
            else -> {
                counterparty = m.group(2)!!.trim().trimEnd('.')
                PHONE_TAIL.matcher(counterparty).takeIf { it.find() }?.let {
                    phone = it.group(1)
                    counterparty = counterparty.substring(0, it.start()).trim()
                }
                if (kind == Kind.PAYBILL) account = m.group(3)!!.trim()
                if (kind == Kind.WITHDRAW) {
                    AGENT.matcher(counterparty).takeIf { it.matches() }?.let {
                        account = it.group(1)
                        counterparty = it.group(2)!!.trim()
                    }
                }
            }
        }

        return ParsedTransaction(
            code = code,
            kind = kind,
            amountCents = toCents(m.group(1)!!),
            feeCents = FEE.matcher(text).firstGroupCents(),
            balanceCents = BALANCE.matcher(text).firstGroupCents(),
            counterparty = counterparty,
            phone = phone,
            account = account,
            timestamp = parseWhen(text),
            raw = raw,
        )
    }

    /**
     * Collapses every whitespace run (newlines, tabs, no-break and other Unicode spaces, which
     * java.util.regex's \s misses) to one space and trims, like Python's " ".join(s.split()).
     * A plain loop: on Android's ICU-backed regex this was a measurable share of import time.
     */
    internal fun collapseWhitespace(body: String): String {
        val sb = StringBuilder(body.length)
        var pendingSpace = false
        for (c in body) {
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = sb.isNotEmpty()
            } else {
                if (pendingSpace) sb.append(' ')
                pendingSpace = false
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun Matcher.firstGroupCents(): Long? = if (find()) toCents(group(1)!!) else null

    /** "1,234,567.89" -> 123456789. The regex guarantees exactly two decimals. */
    fun toCents(amount: String): Long {
        val digits = amount.replace(",", "")
        val (whole, frac) = digits.split('.')
        return whole.toLong() * 100 + frac.toLong()
    }

    /**
     * "on 2/9/26 at 10:15 AM" -> epoch millis in Nairobi time. Equivalent to the prototype's
     * `d/M/yy h:mma` then `d/M/yyyy` formats, but parsed by hand so it doesn't depend on the
     * platform's locale data for AM/PM (which differs between JDK versions and Android).
     */
    fun parseWhen(text: String): Long? {
        val m = WHEN.matcher(text)
        if (!m.find()) return null
        val day = m.group(1)!!.toInt()
        val month = m.group(2)!!.toInt()
        val yearText = m.group(3)!!
        val year = when (yearText.length) {
            2 -> 2000 + yearText.toInt()
            4 -> yearText.toInt()
            else -> return null
        }
        val hour12 = m.group(4)!!.toInt()
        if (hour12 !in 1..12) return null
        val pm = m.group(6)!!.equals("PM", ignoreCase = true)
        val hour = hour12 % 12 + if (pm) 12 else 0
        return try {
            LocalDateTime.of(year, month, day, hour, m.group(5)!!.toInt())
                .atZone(NAIROBI).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }
}
