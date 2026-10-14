package com.ares.mpesaledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ares.mpesaledger.domain.MonthSummary
import com.ares.mpesaledger.domain.MonthTotals
import kotlinx.coroutines.flow.Flow

/** Sums shared by the month queries. Fees count every row, including Fuliza access fees. */
private const val SUMS = """
    COALESCE(SUM(CASE WHEN direction = 'in' THEN amountCents END), 0) AS inCents,
    COALESCE(SUM(CASE WHEN direction = 'out' THEN amountCents END), 0) AS outCents,
    COALESCE(SUM(feeCents), 0) AS feeCents,
    COALESCE(SUM(CASE WHEN kind = 'fuliza' THEN amountCents END), 0) AS fulizaCents,
    COALESCE(SUM(CASE WHEN kind = 'fuliza_repay' THEN amountCents END), 0) AS fulizaRepaidCents,
    COALESCE(SUM(CASE WHEN kind = 'savings' THEN amountCents END), 0) AS savedCents
"""

@Dao
interface TransactionDao {
    /** Insert-or-ignore on (code, kind). Returned row id is -1 for each ignored duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<TransactionEntity>): List<Long>

    /** Fuliza messages carry no date: borrow it from the row sharing the same code. */
    @Query(
        """UPDATE transactions SET timestamp = (
               SELECT t2.timestamp FROM transactions t2
               WHERE t2.code = transactions.code AND t2.timestamp IS NOT NULL LIMIT 1)
           WHERE timestamp IS NULL"""
    )
    suspend fun inheritMissingTimestamps(): Int

    /** [start, end) are epoch millis of Nairobi month boundaries. */
    @Query("SELECT $SUMS FROM transactions WHERE timestamp >= :start AND timestamp < :end")
    suspend fun monthTotals(start: Long, end: Long): MonthTotals

    @Query("SELECT $SUMS FROM transactions WHERE timestamp >= :start AND timestamp < :end")
    fun observeMonthTotals(start: Long, end: Long): Flow<MonthTotals>

    /** [offsetSeconds] shifts UTC to Nairobi wall time (EAT, fixed +3h, no DST). */
    @Query(
        """SELECT strftime('%Y-%m', timestamp / 1000 + :offsetSeconds, 'unixepoch') AS month, $SUMS
           FROM transactions WHERE timestamp IS NOT NULL
           GROUP BY month ORDER BY month DESC LIMIT :limit"""
    )
    fun observeMonthSummaries(offsetSeconds: Long, limit: Int): Flow<List<MonthSummary>>

    @Query("SELECT COALESCE(categoryOverride, category) FROM transactions WHERE code = :code AND kind = :kind")
    suspend fun effectiveCategory(code: String, kind: String): String?

    @Query("SELECT * FROM transactions WHERE code = :code AND kind = :kind")
    suspend fun get(code: String, kind: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Export order: oldest first, undated (no timestamp) last. */
    @Query("SELECT * FROM transactions ORDER BY timestamp IS NULL, timestamp, code, kind")
    suspend fun allForExport(): List<TransactionEntity>

    /** Spending (direction out, amounts only) per effective category, largest first. */
    @Query(
        """SELECT COALESCE(categoryOverride, category) AS category, SUM(amountCents) AS totalCents, COUNT(*) AS count
           FROM transactions WHERE direction = 'out' AND timestamp >= :start AND timestamp < :end
           GROUP BY 1 ORDER BY totalCents DESC"""
    )
    fun observeCategorySpending(start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query(
        """SELECT COALESCE(categoryOverride, category) AS category, SUM(amountCents) AS totalCents, COUNT(*) AS count
           FROM transactions WHERE direction = 'out' AND timestamp >= :start AND timestamp < :end
           GROUP BY 1 ORDER BY totalCents DESC"""
    )
    suspend fun categorySpending(start: Long, end: Long): List<CategoryTotal>

    @Query(
        """SELECT counterparty, SUM(amountCents) AS totalCents, COUNT(*) AS count
           FROM transactions WHERE direction = 'out' AND timestamp >= :start AND timestamp < :end
           GROUP BY counterparty ORDER BY totalCents DESC LIMIT :limit"""
    )
    fun observeTopRecipients(start: Long, end: Long, limit: Int): Flow<List<RecipientTotal>>

    /**
     * The Activity list. Every filter is optional (null = no filter). [query] must already be
     * LIKE-escaped with backslash. Undated rows sort last.
     */
    @Query(
        """SELECT code, kind, direction, amountCents, feeCents, counterparty, account, timestamp,
                  COALESCE(categoryOverride, category) AS category
           FROM transactions
           WHERE (:start IS NULL OR timestamp >= :start) AND (:end IS NULL OR timestamp < :end)
             AND (:kind IS NULL OR kind = :kind)
             AND (:category IS NULL OR COALESCE(categoryOverride, category) = :category)
             AND (:query IS NULL OR counterparty LIKE '%' || :query || '%' ESCAPE '\'
                  OR account LIKE '%' || :query || '%' ESCAPE '\' OR code LIKE '%' || :query || '%' ESCAPE '\')
           ORDER BY timestamp DESC, code DESC"""
    )
    fun observeList(start: Long?, end: Long?, kind: String?, category: String?, query: String?): Flow<List<TxListItem>>

    @Query("SELECT * FROM transactions WHERE code = :code AND kind = :kind")
    fun observe(code: String, kind: String): Flow<TransactionEntity?>

    @Query("SELECT DISTINCT COALESCE(categoryOverride, category) FROM transactions ORDER BY 1")
    fun observeUsedCategories(): Flow<List<String>>

    /** Manual override; null clears it. Never touched by recategorize. */
    @Query("UPDATE transactions SET categoryOverride = :category WHERE code = :code AND kind = :kind")
    suspend fun setOverride(code: String, kind: String, category: String?)

    @Query("SELECT code, kind, counterparty, account FROM transactions")
    suspend fun categorizeKeys(): List<CategorizeKey>

    /** Rule-derived category only; categoryOverride is left alone. */
    @Query("UPDATE transactions SET category = :category WHERE code = :code AND kind = :kind AND category != :category")
    suspend fun setRuleCategory(code: String, kind: String, category: String): Int
}

data class CategoryTotal(val category: String, val totalCents: Long, val count: Int)

data class RecipientTotal(val counterparty: String, val totalCents: Long, val count: Int)

data class CategorizeKey(val code: String, val kind: String, val counterparty: String, val account: String?)

/** Lightweight list row (no raw SMS). [category] is the effective category. */
data class TxListItem(
    val code: String,
    val kind: String,
    val direction: String,
    val amountCents: Long,
    val feeCents: Long?,
    val counterparty: String,
    val account: String?,
    val timestamp: Long?,
    val category: String,
)

@Dao
interface UnparsedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: UnparsedEntity): Long

    @Query("SELECT * FROM unparsed ORDER BY firstSeen DESC")
    fun observeAll(): Flow<List<UnparsedEntity>>

    @Query("SELECT COUNT(*) FROM unparsed")
    suspend fun count(): Int

    /** A message that now parses (newer parser) leaves the unparsed list. */
    @Query("DELETE FROM unparsed WHERE rawHash = :rawHash")
    suspend fun delete(rawHash: String): Int

    /** Batch form; callers keep lists under SQLite's 999-variable limit (import batches are 250). */
    @Query("DELETE FROM unparsed WHERE rawHash IN (:rawHashes)")
    suspend fun deleteAll(rawHashes: List<String>): Int
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules ORDER BY isUser DESC, keyword")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    /** Same keyword + isUser replaces the old rule (unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity): Long

    @Query("SELECT * FROM category_rules WHERE isUser = 1 ORDER BY keyword")
    suspend fun userRules(): List<CategoryRuleEntity>

    /** Only user rules can be deleted; the built-in defaults are read-only. */
    @Query("DELETE FROM category_rules WHERE id = :id AND isUser = 1")
    suspend fun deleteUserRule(id: Long): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    /**
     * Setting/changing a limit resets the notified state so the new limit can alert this month.
     * REPLACE rather than SQL UPSERT: ON CONFLICT DO UPDATE needs SQLite 3.24, and API 24 ships 3.9.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("UPDATE budgets SET lastNotifiedLevel = :level, lastNotifiedMonth = :month WHERE category = :category")
    suspend fun markNotified(category: String, level: Int, month: Int)
}

@Dao
interface ImportStateDao {
    @Query("SELECT * FROM import_state WHERE id = 0")
    suspend fun get(): ImportStateEntity?

    @Upsert
    suspend fun put(state: ImportStateEntity)
}

/** Wipe: explicit deletes (Room's clearAllTables can't run inside a transaction). */
@Dao
interface WipeDao {
    @Query("DELETE FROM transactions") suspend fun transactions()
    @Query("DELETE FROM unparsed") suspend fun unparsed()
    @Query("DELETE FROM category_rules") suspend fun rules()
    @Query("DELETE FROM budgets") suspend fun budgets()
    @Query("DELETE FROM import_state") suspend fun importState()
}

/** Row counts for status displays, observed as one Flow. */
data class LedgerCounts(val transactions: Int, val unparsed: Int, val rules: Int, val undated: Int)

@Dao
interface StatsDao {
    @Query(
        """SELECT (SELECT COUNT(*) FROM transactions) AS transactions,
                  (SELECT COUNT(*) FROM unparsed) AS unparsed,
                  (SELECT COUNT(*) FROM category_rules) AS rules,
                  (SELECT COUNT(*) FROM transactions WHERE timestamp IS NULL) AS undated"""
    )
    fun observeCounts(): Flow<LedgerCounts>
}
