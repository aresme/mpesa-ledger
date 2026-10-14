package com.ares.mpesaledger.data

import com.ares.mpesaledger.parser.MpesaParser
import java.io.Writer
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * CSV with the prototype's `export` columns (everything except the raw SMS). Amounts are
 * written from cents without ever going through floating point; `ts` is Nairobi local time
 * in ISO format like the prototype; `category` is the effective (override-aware) category.
 */
object CsvExport {
    val COLUMNS = listOf("code", "kind", "direction", "amount", "cost", "balance", "counterparty", "phone", "account", "ts", "category")
    private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun write(rows: List<TransactionEntity>, out: Writer) {
        out.write(COLUMNS.joinToString(",") + "\r\n")
        for (r in rows) {
            val fields = listOf(
                r.code, r.kind, r.direction,
                decimal(r.amountCents), r.feeCents?.let(::decimal), r.balanceCents?.let(::decimal),
                r.counterparty, r.phone, r.account,
                r.timestamp?.let { Instant.ofEpochMilli(it).atZone(MpesaParser.NAIROBI).format(TS) },
                r.categoryOverride ?: r.category,
            )
            out.write(fields.joinToString(",") { escape(it.orEmpty()) } + "\r\n")
        }
        out.flush()
    }

    /** 65000 -> "650.00"; -150 -> "-1.50". Plain digits, no grouping, for spreadsheets. */
    fun decimal(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = Math.abs(cents)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** RFC 4180 quoting, plus a leading quote for values a spreadsheet would run as a formula. */
    fun escape(value: String): String {
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@') && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
