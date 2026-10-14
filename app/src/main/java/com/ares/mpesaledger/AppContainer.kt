package com.ares.mpesaledger

import android.content.Context
import com.ares.mpesaledger.data.AppDatabase
import com.ares.mpesaledger.data.AppPrefs
import com.ares.mpesaledger.data.LedgerImporter
import com.ares.mpesaledger.data.LedgerRepository
import com.ares.mpesaledger.devtools.DevTools
import com.ares.mpesaledger.domain.BillReminder
import com.ares.mpesaledger.domain.BudgetChecker
import com.ares.mpesaledger.sms.InboxReader
import com.ares.mpesaledger.sms.InboxSync
import com.ares.mpesaledger.sms.LiveSmsPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency injection: one instance per process, owned by [MpesaLedgerApp].
 * Demo mode is decided once at process start (toggling it restarts the process).
 */
class AppContainer(val appContext: Context) {
    /** Outlives screens: imports started here are not cancelled by navigation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isDemo: Boolean = DevTools.isDemoMode(appContext)
    val databaseName: String = if (isDemo) AppDatabase.DEMO_NAME else AppDatabase.REAL_NAME
    val database: AppDatabase = AppDatabase.build(appContext, databaseName)

    val prefs = AppPrefs(appContext)
    val repository = LedgerRepository(database)
    // A parser upgrade can bring new default rules (DB migration) -> re-apply rules once.
    val importer = LedgerImporter(database) { repository.recategorizeAll() }
    val inboxSync = InboxSync(appContext, importer, InboxReader(appContext.contentResolver) { prefs.enabledSources }, isDemo)
    val budgetChecker = BudgetChecker(appContext, database)
    val billReminder = BillReminder(appContext, repository, prefs)
    val livePipeline = LiveSmsPipeline(this)

    init {
        if (isDemo) appScope.launch { DevTools.seedDemoIfEmpty(appContext, database, importer) }
    }

    fun syncInboxInBackground(full: Boolean = false) {
        appScope.launch {
            val result = inboxSync.sync(full)
            // Messages the receiver missed (app killed) still trigger budget alerts on catch-up.
            if (result != null && result.inserted > 0) budgetChecker.check()
            // Also the fallback for the daily bill alarm, which XOS may drop.
            billReminder.check()
        }
    }
}
