package com.ares.mpesaledger.parser

import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDateTime
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parser for non-M-Pesa senders (parser v4). Every pattern here was written from the user's
 * real messages (masked), 2026-10-15. Unknown shapes from these senders are mostly adverts,
 * so they are skipped (NotTransaction) rather than filling the Unrecognised list.
 *
 * Messages without a transaction code get a stable synthetic one ("S" + hash of sender, text
 * and received time) so re-imports stay idempotent.
 */
object OtherSmsParser {
    private const val CI = Pattern.CASE_INSENSITIVE
    /** Amounts here may have 0-2 decimals ("Ksh. 20", "Ksh9.5", "Ksh 1,234.56"). */
    private const val AMT = """(\d[\d,]*(?:\.\d{1,2})?)"""

    private val GLOBALPAY = Pattern.compile(
        """a transaction of Ksh\.?\s?$AMT\s*(?:\(inclusive of Ksh\.?\s?$AMT forex charge\)\s*)?done at (.+?) has been approved on your card""", CI)
    private val OKOA_BORROW = Pattern.compile(
        """You have received Ksh\.?\s?$AMT\.\s*The fee is KSH\.?\s?$AMT\.?\s*Your debt of Ksh\.?\s?$AMT should be paid by (\d{1,2})-(\d{1,2})-(\d{4})""", CI)
    private val OKOA_CHAPCHAP = Pattern.compile(
        """call costs through OKOA was Ksh\.?\s?$AMT to be paid by (\d{4})-(\d{1,2})-(\d{1,2})""", CI)
    private val OKOA_REPAY = Pattern.compile("""^$AMT\s?KSH has been deducted to repay your Okoa""", CI)
    private val DATA_BUNDLE = Pattern.compile(
        """Thank you for purchasing (.+?)\.?\s*Expiry date:?\s*(\d{1,2})[/-](\d{1,2})[/-](\d{4}) (\d{1,2}):(\d{2})\s?([AP]M)""", CI)
    private val RECHARGE = Pattern.compile("""Recharge of $AMT\s?KSH was successful\.?\s*Balance:\s?$AMT\s?KSH""", CI)
    private val KCB_RECEIVED = Pattern.compile(
        """^(\S+) Confirmed! You have received KES\s?$AMT from (.+?) at (\d{4})-(\d{1,2})-(\d{1,2}) (\d{1,2}):(\d{2})(?::\d{2})?\s?([AP]M) via KCB""", CI)
    private val KCB_DEPOSIT = Pattern.compile(
        """Ksh\.?\s?$AMT sent to KCB account (.+?) has been received on (\d{1,2})/(\d{1,2})/(\d{4}) at (\d{1,2}):(\d{2})\s?([AP]M)\.?\s*M-PESA Ref ([A-Z0-9]{10})""", CI)
    private val ZIIDI_WITHDRAW = Pattern.compile("""You have successfully withdrawn Ksh\.?\s?$AMT of transaction code ([A-Z0-9]{10})""", CI)
    private val ETOPUP = Pattern.compile("""confirmed\. You have received Ksh\.?\s?(\d[\d,]*(?:\.\d{1,2})?)\.?$""", CI)

    fun parse(source: Source, body: String, receivedAt: Long?): ParseResult {
        val text = MpesaParser.collapseWhitespace(body)
        val raw = body.trim()
        fun synthetic() = "S" + sha1(source.key + "|" + text + "|" + (receivedAt ?: "")).take(9).uppercase()
        fun tx(kind: Kind, amount: Long, counterparty: String, fee: Long? = null, balance: Long? = null,
               account: String? = null, timestamp: Long? = null, code: String = synthetic()) =
            ParseResult.Transaction(ParsedTransaction(code, kind, amount, fee, balance, counterparty, null, account, timestamp, raw))

        return when (source) {
            Source.MPESA -> MpesaParser.parse(body)
            Source.GLOBALPAY -> GLOBALPAY.find(text)?.let { m ->
                val total = cents(m.group(1)!!)
                val forex = m.group(2)?.let(::cents) ?: 0
                tx(Kind.GLOBALPAY, total - forex, merchant(m.group(3)!!), fee = forex)
            }
            Source.OKOA -> OKOA_BORROW.find(text)?.let { m ->
                // balance = total Okoa debt after this loan (Safaricom's own figure).
                tx(Kind.OKOA_BORROW, cents(m.group(1)!!), "Okoa Jahazi", fee = cents(m.group(2)!!), balance = cents(m.group(3)!!),
                    account = "due ${m.group(6)}-${pad(m.group(5)!!)}-${pad(m.group(4)!!)}")
            } ?: OKOA_CHAPCHAP.find(text)?.let { m ->
                tx(Kind.OKOA_BORROW, cents(m.group(1)!!), "Okoa Chap Chap", balance = null, account = "due ${m.group(2)}-${pad(m.group(3)!!)}-${pad(m.group(4)!!)}")
            } ?: OKOA_REPAY.find(text)?.let { m -> tx(Kind.OKOA_REPAY, cents(m.group(1)!!), "Okoa Jahazi") }
            Source.SAFARICOM -> DATA_BUNDLE.find(text)?.let { m ->
                val expiry = "expires ${pad(m.group(2)!!)}/${pad(m.group(3)!!)}/${m.group(4)} ${m.group(5)}:${m.group(6)} ${m.group(7)!!.uppercase()}"
                tx(Kind.DATA_BUNDLE, 0, m.group(1)!!.trim().trimEnd('.'), account = expiry)
            } ?: RECHARGE.find(text)?.let { m -> tx(Kind.AIRTIME_RECHARGE, cents(m.group(1)!!), "Safaricom airtime", balance = cents(m.group(2)!!)) }
            Source.KCB -> KCB_RECEIVED.find(text)?.let { m ->
                val (name, acct) = splitAccount(m.group(3)!!)
                val code = m.group(1)!!.takeIf { it.matches(Regex("[A-Z0-9]{8,12}")) } ?: synthetic()
                tx(Kind.BANK_IN, cents(m.group(2)!!), name, account = listOfNotNull("KCB", acct).joinToString(" · "),
                    timestamp = at(m.group(4)!!.toInt(), m.group(5)!!.toInt(), m.group(6)!!.toInt(), m.group(7)!!, m.group(8)!!, m.group(9)!!), code = code)
            } ?: KCB_DEPOSIT.find(text)?.let { m ->
                tx(Kind.BANK_IN, cents(m.group(1)!!), "M-Pesa to KCB", account = "KCB · ${m.group(2)!!.trim()}",
                    timestamp = at(m.group(5)!!.toInt(), m.group(4)!!.toInt(), m.group(3)!!.toInt(), m.group(6)!!, m.group(7)!!, m.group(8)!!),
                    code = m.group(9)!!.uppercase())
            }
            Source.ZIIDI -> ZIIDI_WITHDRAW.find(text)?.let { m ->
                tx(Kind.SAVINGS_WITHDRAW, cents(m.group(1)!!), "Ziidi", code = m.group(2)!!.uppercase())
            }
            Source.ETOPUP -> ETOPUP.find(text)?.let { m -> tx(Kind.AIRTIME_RECEIVED, cents(m.group(1)!!), "Airtime top-up") }
        } ?: ParseResult.NotTransaction
    }

    private fun Pattern.find(text: String): Matcher? = matcher(text).takeIf { it.find() }

    /** "1,234" / "20" / "9.5" / "99.99" -> cents, exactly. */
    fun cents(amount: String): Long {
        val digits = amount.replace(",", "")
        val whole = digits.substringBefore('.')
        val frac = digits.substringAfter('.', "").padEnd(2, '0').take(2)
        return whole.toLong() * 100 + frac.toLong()
    }

    /** "OPENAI *CHATGPT SUBSCR g.co/helppay#" -> trimmed merchant name. */
    private fun merchant(raw: String): String = raw.trim().trimStart('*').trim().ifEmpty { "GlobalPay merchant" }

    /** "JANE DOE - 071****678" -> ("JANE DOE", "071****678"). */
    private fun splitAccount(raw: String): Pair<String, String?> {
        val i = raw.lastIndexOf(" - ")
        return if (i > 0) raw.substring(0, i).trim() to raw.substring(i + 3).trim() else raw.trim() to null
    }

    private fun pad(v: String) = v.padStart(2, '0')

    private fun at(y: Int, mo: Int, d: Int, h: String, min: String, ampm: String): Long? = try {
        val hour = h.toInt() % 12 + if (ampm.equals("PM", ignoreCase = true)) 12 else 0
        LocalDateTime.of(y, mo, d, hour, min.toInt()).atZone(MpesaParser.NAIROBI).toInstant().toEpochMilli()
    } catch (_: DateTimeException) { null }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
