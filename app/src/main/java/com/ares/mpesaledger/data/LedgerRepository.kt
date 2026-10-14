package com.ares.mpesaledger.data

import com.ares.mpesaledger.domain.MonthSummary
import com.ares.mpesaledger.parser.MpesaParser
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Read side for the UI. Aggregates run in SQL; nothing loads the whole ledger. */
class LedgerRepository(private val db: AppDatabase) {

    fun observeCounts(): Flow<LedgerCounts> = db.stats().observeCounts()

    fun observeMonthSummaries(limit: Int = 12): Flow<List<MonthSummary>> =
        db.transactions().observeMonthSummaries(nairobiOffsetSeconds(), limit)

    companion object {
        /** EAT is a fixed UTC+3 with no DST, so one offset is valid for every stored timestamp. */
        fun nairobiOffsetSeconds(): Long =
            MpesaParser.NAIROBI.rules.getOffset(Instant.now()).totalSeconds.toLong()
    }
}
