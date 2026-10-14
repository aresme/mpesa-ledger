package com.ares.mpesaledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ares.mpesaledger.parser.ParsedTransaction

/**
 * PK is (code, kind): a Fuliza message deliberately shares its code with the payment it
 * funded, and both rows must survive. Effective category = categoryOverride ?: category;
 * "Recategorize all" only ever rewrites [category], so manual overrides survive it.
 */
@Entity(
    tableName = "transactions",
    primaryKeys = ["code", "kind"],
    indices = [Index("timestamp"), Index("category")],
)
data class TransactionEntity(
    val code: String,
    val kind: String,
    val direction: String,
    val amountCents: Long,
    val feeCents: Long?,
    val balanceCents: Long?,
    val counterparty: String,
    val phone: String?,
    val account: String?,
    val timestamp: Long?,
    val category: String,
    val categoryOverride: String? = null,
    val smsId: Long?,
    val raw: String,
)

fun ParsedTransaction.toEntity(category: String, smsId: Long?) = TransactionEntity(
    code = code,
    kind = kind.key,
    direction = direction.key,
    amountCents = amountCents,
    feeCents = feeCents,
    balanceCents = balanceCents,
    counterparty = counterparty,
    phone = phone,
    account = account,
    timestamp = timestamp,
    category = category,
    smsId = smsId,
    raw = raw,
)

/** Messages with a transaction code but an unknown format. Keyed by SHA-256 of the raw text. */
@Entity(tableName = "unparsed")
data class UnparsedEntity(
    @PrimaryKey val rawHash: String,
    val raw: String,
    val firstSeen: Long,
)

/** Keywords are stored uppercase. A user rule may reuse a default's keyword (it wins). */
@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["keyword", "isUser"], unique = true)],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val category: String,
    val isUser: Boolean,
)

/**
 * [lastNotifiedLevel] (0/80/100) applies to [lastNotifiedMonth] (yyyyMM), so each level
 * notifies at most once per month.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimitCents: Long,
    val lastNotifiedLevel: Int = 0,
    val lastNotifiedMonth: Int? = null,
)

/**
 * Single row (id = 0): the highest inbox `date` imported so far, for incremental scans, and
 * the parser version that import ran with (a newer parser forces one full rescan).
 */
@Entity(tableName = "import_state")
data class ImportStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastInboxDate: Long,
    @ColumnInfo(defaultValue = "0") val parserVersion: Int = 0,
)
