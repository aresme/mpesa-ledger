package com.ares.mpesaledger.parser

import com.ares.mpesaledger.domain.MonthTotals
import java.time.Instant
import java.time.YearMonth

/**
 * In-memory stand-in for the Room ledger, with the same semantics the DAO must implement
 * (spec §5.4): PK (code, kind), insert-or-ignore, Fuliza timestamp inheritance by code, and
 * month aggregates by Nairobi month. Phase 2's Room tests assert the same numbers in SQL.
 */
class FakeLedger {
    private val byKey = LinkedHashMap<Pair<String, Kind>, ParsedTransaction>()

    val rows: List<ParsedTransaction> get() = byKey.values.toList()

    /** Returns the number of new rows (insert-or-ignore). */
    fun insertAll(txs: List<ParsedTransaction>): Int =
        txs.count { byKey.putIfAbsent(it.code to it.kind, it) == null }

    fun inheritFulizaTimestamps() {
        for ((key, tx) in byKey) {
            if (tx.timestamp != null) continue
            val donor = byKey.values.firstOrNull { it.code == tx.code && it.timestamp != null } ?: continue
            byKey[key] = tx.copy(timestamp = donor.timestamp)
        }
    }

    fun monthTotals(month: YearMonth): MonthTotals {
        val inMonth = rows.filter { tx ->
            tx.timestamp?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(MpesaParser.NAIROBI)) } == month
        }
        return MonthTotals(
            inCents = inMonth.filter { it.direction == Direction.IN }.sumOf { it.amountCents },
            outCents = inMonth.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
            feeCents = inMonth.sumOf { it.feeCents ?: 0 },
            fulizaCents = inMonth.filter { it.kind == Kind.FULIZA }.sumOf { it.amountCents },
            savedCents = inMonth.filter { it.kind == Kind.SAVINGS }.sumOf { it.amountCents },
        )
    }
}
