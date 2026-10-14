package com.ares.mpesaledger.data

import androidx.room.withTransaction
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.domain.Categorizer
import com.ares.mpesaledger.domain.CategoryRule
import com.ares.mpesaledger.parser.Kind
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.parser.ParseResult
import com.ares.mpesaledger.parser.ParsedTransaction
import com.ares.mpesaledger.parser.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ImportProgress(val done: Int, val total: Int)

data class ImportResult(
    val source: String,
    val scanned: Int,
    val inserted: Int,
    val duplicates: Int,
    val unparsed: Int,
    val skipped: Int,
    val durationMs: Long,
)

/**
 * Parses, categorizes and stores SMS. Idempotent: (code, kind) insert-or-ignore, unparsed
 * keyed by content hash. One import at a time (inbox scan on resume, file import, live SMS
 * can all race), serialized by [mutex].
 */
class LedgerImporter(private val db: AppDatabase) {

    private val mutex = Mutex()
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    private val _lastResult = MutableStateFlow<ImportResult?>(null)

    /** Non-null while an import is running. */
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()
    val lastResult: StateFlow<ImportResult?> = _lastResult.asStateFlow()

    suspend fun import(messages: List<RawSms>, source: String): ImportResult =
        mutex.withLock { importLocked(messages, source) }

    /**
     * Reads the inbox from the stored high-water mark (or from 0 when [full]) and imports.
     * `>=` rather than `>` so a message sharing the boundary millisecond is never skipped;
     * re-reading it is harmless because inserts are idempotent. A parser newer than the one
     * recorded in import_state also forces a full scan, so old messages get re-parsed.
     */
    suspend fun importInbox(read: suspend (sinceDate: Long) -> List<RawSms>, full: Boolean): ImportResult =
        mutex.withLock {
            val state = db.importState().get()
            val parserUpgraded = (state?.parserVersion ?: 0) < MpesaParser.VERSION
            val fullScan = full || parserUpgraded
            val since = if (fullScan) 0L else state?.lastInboxDate ?: 0L
            val started = System.nanoTime()
            val messages = withContext(Dispatchers.IO) { read(since) }
            val readMs = (System.nanoTime() - started) / 1_000_000
            val source = when {
                full -> "inbox (full)"
                parserUpgraded -> "inbox (parser v${MpesaParser.VERSION} rescan)"
                else -> "inbox"
            }
            val result = importLocked(messages, source)
            val newest = maxOf(messages.mapNotNull { it.date }.maxOrNull() ?: 0L, state?.lastInboxDate ?: 0L)
            db.importState().put(ImportStateEntity(lastInboxDate = newest, parserVersion = MpesaParser.VERSION))
            result.copy(durationMs = result.durationMs + readMs).also {
                _lastResult.value = it
                AppLog.d { "import ${it.source}: read=${readMs}ms $it" }
            }
        }

    private suspend fun importLocked(messages: List<RawSms>, source: String): ImportResult =
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            val categorizer = Categorizer(
                db.rules().getAll().map { CategoryRule(it.keyword, it.category, it.isUser) }
            )
            var inserted = 0
            var duplicates = 0
            var unparsed = 0
            var skipped = 0
            _progress.value = ImportProgress(0, messages.size)
            // Only hash and clean up when there is something to clean: per-row DELETEs made a
            // 5,000-message import ~4x slower when the unparsed table was empty anyway.
            val cleanUnparsed = db.unparsed().count() > 0
            var parseNs = 0L
            var dbNs = 0L

            db.withTransaction {
                messages.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                    val rows = ArrayList<TransactionEntity>(batch.size)
                    val nowParsed = if (cleanUnparsed) ArrayList<String>() else null
                    for (sms in batch) {
                        val p0 = System.nanoTime()
                        val parsed = MpesaParser.parse(sms.body)
                        parseNs += System.nanoTime() - p0
                        when (val result = parsed) {
                            ParseResult.NotTransaction -> skipped++
                            is ParseResult.Unparsed -> {
                                unparsed++
                                db.unparsed().insert(UnparsedEntity(sha256(result.raw), result.raw, System.currentTimeMillis()))
                                AppLog.d { "unparsed ${result.code}" }
                            }
                            is ParseResult.Transaction -> {
                                val tx = result.tx.withFallbackTime(sms.date)
                                rows += tx.toEntity(categorizer.categorize(tx.kind, tx.counterparty, tx.account), sms.smsId)
                                // Parsed now (e.g. after a parser upgrade): no longer unparsed.
                                nowParsed?.add(sha256(tx.raw))
                            }
                        }
                    }
                    val d0 = System.nanoTime()
                    if (!nowParsed.isNullOrEmpty()) db.unparsed().deleteAll(nowParsed)
                    val new = db.transactions().insertAll(rows).count { it != -1L }
                    dbNs += System.nanoTime() - d0
                    inserted += new
                    duplicates += rows.size - new
                    _progress.value = ImportProgress(minOf((index + 1) * BATCH_SIZE, messages.size), messages.size)
                }
                db.transactions().inheritMissingTimestamps()
            }

            _progress.value = null
            AppLog.d { "import timing: parse=${parseNs / 1_000_000}ms insert=${dbNs / 1_000_000}ms (${messages.size} msgs)" }
            ImportResult(
                source = source,
                scanned = messages.size,
                inserted = inserted,
                duplicates = duplicates,
                unparsed = unparsed,
                skipped = skipped,
                durationMs = (System.nanoTime() - started) / 1_000_000,
            ).also { _lastResult.value = it }
        }

    private companion object {
        const val BATCH_SIZE = 250

        /**
         * Some formats (e.g. Fuliza repayments) carry no date; use the SMS received time.
         * The Fuliza loan message is excluded: it inherits the exact time of the payment it
         * funded (same code) after the batch, per spec §5.4.
         */
        fun ParsedTransaction.withFallbackTime(smsDate: Long?) =
            if (timestamp != null || kind == Kind.FULIZA || smsDate == null) this else copy(timestamp = smsDate)

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
