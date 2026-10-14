package com.ares.mpesaledger.devtools

import android.app.Activity
import android.content.Context
import com.ares.mpesaledger.data.AppDatabase
import com.ares.mpesaledger.data.LedgerImporter

/** Release twin of the debug DevTools: no demo mode, no test hooks. */
object DevTools {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun isDemoMode(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun setDemoModeAndRestart(activity: Activity, enabled: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    suspend fun seedDemoIfEmpty(context: Context, db: AppDatabase, importer: LedgerImporter) = Unit
}
