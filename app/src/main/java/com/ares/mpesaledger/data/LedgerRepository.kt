package com.ares.mpesaledger.data

import androidx.room.withTransaction
import com.ares.mpesaledger.domain.Categorizer
import com.ares.mpesaledger.domain.CategoryRule
import com.ares.mpesaledger.domain.DefaultRules
import com.ares.mpesaledger.domain.MonthSummary
import com.ares.mpesaledger.domain.MonthTotals
import com.ares.mpesaledger.parser.Kind
import com.ares.mpesaledger.parser.MpesaParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.YearMonth

/** Epoch-millis bounds [start, end) of a Nairobi calendar month. */
data class MonthRange(val start: Long, val end: Long) {
    companion object {
        fun of(month: YearMonth) = MonthRange(
            start = month.atDay(1).atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli(),
            end = month.plusMonths(1).atDay(1).atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli(),
        )
    }
}

data class ListFilter(
    val month: YearMonth? = null,
    val kind: String? = null,
    val category: String? = null,
    val query: String = "",
)

/** Read/write facade for the UI. Aggregates run in SQL; nothing loads the whole ledger. */
class LedgerRepository(private val db: AppDatabase) {

    private val tx get() = db.transactions()

    fun observeCounts(): Flow<LedgerCounts> = db.stats().observeCounts()

    fun observeMonthSummaries(limit: Int = 120): Flow<List<MonthSummary>> =
        tx.observeMonthSummaries(nairobiOffsetSeconds(), limit)

    fun observeTotals(month: YearMonth): Flow<MonthTotals> =
        MonthRange.of(month).let { tx.observeMonthTotals(it.start, it.end) }

    fun observeCategorySpending(month: YearMonth): Flow<List<CategoryTotal>> =
        MonthRange.of(month).let { tx.observeCategorySpending(it.start, it.end) }

    fun observeTopRecipients(month: YearMonth, limit: Int = 5): Flow<List<RecipientTotal>> =
        MonthRange.of(month).let { tx.observeTopRecipients(it.start, it.end, limit) }

    fun observeList(filter: ListFilter): Flow<List<TxListItem>> {
        val range = filter.month?.let(MonthRange::of)
        val query = filter.query.trim().takeIf { it.isNotEmpty() }?.let(::escapeLike)
        return tx.observeList(range?.start, range?.end, filter.kind, filter.category, query)
    }

    fun observeTransaction(code: String, kind: String): Flow<TransactionEntity?> = tx.observe(code, kind)

    fun observeUnparsed(): Flow<List<UnparsedEntity>> = db.unparsed().observeAll()

    /** Every category a picker should offer: in use, from rules, and the kind fallbacks. */
    fun observeCategoryChoices(): Flow<List<String>> =
        combine(tx.observeUsedCategories(), db.rules().observeAll()) { used, rules ->
            (used + rules.map { it.category } + DefaultRules.all.map { it.category } +
                Kind.entries.map(Categorizer::fallback)).distinct().sorted()
        }

    suspend fun setOverride(code: String, kind: String, category: String?) = tx.setOverride(code, kind, category)

    /**
     * "Always categorize COUNTERPARTY as X": adds a user rule, clears this row's manual
     * override so the rule shows through, and recategorizes everything.
     */
    suspend fun addUserRuleAndRecategorize(keyword: String, category: String, code: String, kind: String): Int =
        db.withTransaction {
            db.rules().upsert(CategoryRuleEntity(keyword = keyword.trim().uppercase(), category = category, isUser = true))
            tx.setOverride(code, kind, null)
            recategorizeAllLocked()
        }

    /** Re-applies rules to the rule-derived `category` column. Manual overrides survive. */
    suspend fun recategorizeAll(): Int = db.withTransaction { recategorizeAllLocked() }

    private suspend fun recategorizeAllLocked(): Int {
        val categorizer = Categorizer(db.rules().getAll().map { CategoryRule(it.keyword, it.category, it.isUser) })
        return tx.categorizeKeys().sumOf { key ->
            val kind = Kind.fromKey(key.kind) ?: return@sumOf 0
            tx.setRuleCategory(key.code, key.kind, categorizer.categorize(kind, key.counterparty, key.account))
        }
    }

    companion object {
        /** EAT is a fixed UTC+3 with no DST, so one offset is valid for every stored timestamp. */
        fun nairobiOffsetSeconds(): Long =
            MpesaParser.NAIROBI.rules.getOffset(Instant.now()).totalSeconds.toLong()

        fun escapeLike(text: String): String =
            text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
