package com.ares.mpesaledger.data

import androidx.room.withTransaction
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.domain.Categorizer
import com.ares.mpesaledger.domain.CategoryRule
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.parser.ParseResult
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
     * re-reading it is harmless because inserts are idempotent.
     */
    suspend fun importInbox(read: suspend (sinceDate: Long) -> List<RawSms>, full: Boolean): ImportResult =
        mutex.withLock {
            val since = if (full) 0L else db.importState().get()?.lastInboxDate ?: 0L
            val started = System.nanoTime()
            val messages = withContext(Dispatchers.IO) { read(since) }
            val readMs = (System.nanoTime() - started) / 1_000_000
            val result = importLocked(messages, if (full) "inbox (full)" else "inbox")
            messages.mapNotNull { it.date }.maxOrNull()?.let { newest ->
                if (newest > since) db.importState().put(ImportStateEntity(lastInboxDate = newest))
            }
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

            db.withTransaction {
                messages.chunked(BATCH_SIZE).forEachIndexed { index, batch ->
                    val rows = ArrayList<TransactionEntity>(batch.size)
                    for (sms in batch) {
                        when (val result = MpesaParser.parse(sms.body)) {
                            ParseResult.NotTransaction -> skipped++
                            is ParseResult.Unparsed -> {
                                unparsed++
                                db.unparsed().insert(UnparsedEntity(sha256(result.raw), result.raw, System.currentTimeMillis()))
                                AppLog.d { "unparsed ${result.code}" }
                            }
                            is ParseResult.Transaction -> {
                                val tx = result.tx
                                rows += tx.toEntity(categorizer.categorize(tx.kind, tx.counterparty, tx.account), sms.smsId)
                            }
                        }
                    }
                    val new = db.transactions().insertAll(rows).count { it != -1L }
                    inserted += new
                    duplicates += rows.size - new
                    _progress.value = ImportProgress(minOf((index + 1) * BATCH_SIZE, messages.size), messages.size)
                }
                db.transactions().inheritMissingTimestamps()
            }

            _progress.value = null
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

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
