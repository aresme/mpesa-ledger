package com.ares.mpesaledger.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.data.ImportResult
import com.ares.mpesaledger.data.LedgerImporter

/**
 * The inbox is the source of truth. An incremental scan runs on every app start/resume,
 * which also catches up on anything the live receiver missed (XOS kills background apps).
 * Never runs in demo mode: real messages must not leak into the demo database.
 */
class InboxSync(
    private val context: Context,
    private val importer: LedgerImporter,
    private val reader: InboxReader,
    private val isDemo: Boolean,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Returns null when skipped (demo mode or no permission). */
    suspend fun sync(full: Boolean = false): ImportResult? {
        if (isDemo) {
            AppLog.d { "inbox sync skipped: demo mode" }
            return null
        }
        if (!hasPermission()) {
            AppLog.d { "inbox sync skipped: READ_SMS not granted" }
            return null
        }
        return importer.importInbox(reader::readMpesa, full)
    }
}
