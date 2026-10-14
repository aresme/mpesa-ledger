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
 *
 * To support a new SMS format: add a [Kind] (if needed), a pattern in [patterns] at the
 * right priority, and a fixture test with a masked real message.
 */
object MpesaParser {

    /**
     * Bump whenever parsing output changes for existing messages. A higher version than the
     * one stored in import_state triggers a full inbox rescan, so old messages get re-parsed.
     */
    const val VERSION = 2

    val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")

    private const val AMT = """([\d,]+\.\d{2})"""
    private const val KSH = """Ksh\s?$AMT"""
    private const val CI = Pattern.CASE_INSENSITIVE

    // Includes no-break and other Unicode spaces, which java.util.regex's \s does not.
    private val WHITESPACE = Pattern.compile("""[\s   -     　]+""")
    private val CODE = Pattern.compile("""^\s*([A-Z0-9]{10})\s+confirmed""", CI)
    private val WHEN = Pattern.compile("""on (\d{1,2})/(\d{1,2})/(\d{2,4}) at (\d{1,2}):(\d{2})\s?([AP]M)""", CI)
    private val BALANCE = Pattern.compile("""(?:New |Your )?M-PESA balance is (?:Ksh\s?)?$AMT""", CI)
    private val FEE = Pattern.compile("""(?:Transaction cost,?|Access Fee charged)\s*$KSH""", CI)
    private val PHONE_TAIL = Pattern.compile("""\s+((?:\+?254|0)[\d*]{6,12})$""")
    private val AGENT = Pattern.compile("""^(\d+)\s*-\s*(.+)$""")

    /** Tried in this order; first match wins (paybill must precede plain "sent to"). */
    private val patterns: List<Pair<Kind, Pattern>> = listOf(
        Kind.FULIZA to """Fuliza M-Pesa amount is $KSH""",
        Kind.RECEIVED to """You have received $KSH from (.+?) on \d""",
        Kind.PAYBILL to """$KSH sent to (.+?) for account (.+?) on \d""",
        Kind.SENT to """$KSH sent to (.+?) on \d""",
        Kind.TILL to """$KSH paid to (.+?)\.?\s*on \d""",
        Kind.WITHDRAW to """Withdraw $KSH from (.+?)\s*New M-PESA""",
        Kind.DEPOSIT to """Give $KSH cash to (.+?)\s*New M-PESA""",
        Kind.AIRTIME to """You bought $KSH of airtime""",
        Kind.SAVINGS to """transferred $KSH to your M-Shwari""",
        Kind.FULIZA_REPAY to """$KSH from your M-PESA has been used to (?:fully |partially )?pay your outstanding Fuliza""",
    ).map { (kind, regex) -> kind to Pattern.compile(regex, CI) }

    fun parse(body: String): ParseResult {
        val text = WHITESPACE.matcher(body).replaceAll(" ").trim()
        val codeMatch = CODE.matcher(text)
        if (!codeMatch.find()) return ParseResult.NotTransaction
        val code = codeMatch.group(1)!!.uppercase()
        val raw = body.trim()

        for ((kind, pattern) in patterns) {
            val m = pattern.matcher(text)
            if (!m.find()) continue
            return ParseResult.Transaction(build(kind, m, code, text, raw))
        }
        return ParseResult.Unparsed(code, raw)
    }

    private fun build(kind: Kind, m: Matcher, code: String, text: String, raw: String): ParsedTransaction {
        var counterparty: String
        var phone: String? = null
        var account: String? = null

        when (kind) {
            Kind.FULIZA, Kind.FULIZA_REPAY -> counterparty = "Fuliza"
            Kind.AIRTIME -> counterparty = "Airtime"
            Kind.SAVINGS -> counterparty = "M-Shwari"
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
