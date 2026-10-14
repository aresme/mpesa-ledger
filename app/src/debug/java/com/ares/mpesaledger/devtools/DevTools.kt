package com.ares.mpesaledger.devtools

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.data.AppDatabase
import com.ares.mpesaledger.data.LedgerImporter
import com.ares.mpesaledger.parser.RawSms
import com.ares.mpesaledger.parser.SmsText
import kotlin.system.exitProcess

/**
 * Debug-build-only tools. `src/release` has a no-op twin with the same API, so main code
 * can call these without any debug code shipping in release.
 *
 * Demo mode swaps the whole process onto mpesa_demo.db, seeded from the bundled
 * sample_sms.txt, so screenshots never contain real transactions. The flag lives in its
 * own prefs file so scripts/shot.sh can check it via `run-as`.
 */
object DevTools {
    const val AVAILABLE = true

    private const val PREFS = "devtools"
    private const val KEY_DEMO = "demo_mode"
    private const val SAMPLE_ASSET = "sample_sms.txt"

    fun isDemoMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DEMO, false)

    /**
     * Switching databases under live ViewModels and Flows is error-prone, so persist the flag
     * and restart the process instead: every component then starts on the right database.
     */
    fun setDemoModeAndRestart(activity: Activity, enabled: Boolean) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DEMO, enabled).commit() // commit, not apply: the process dies next
        val relaunch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(relaunch)
        activity.finishAffinity()
        exitProcess(0)
    }

    /** Fills the demo database from the bundled fixture the first time it is opened. */
    suspend fun seedDemoIfEmpty(context: Context, db: AppDatabase, importer: LedgerImporter) {
        if (db.transactions().count() > 0) return
        val text = context.assets.open(SAMPLE_ASSET).use { it.readBytes().decodeToString() }
        val result = importer.import(SmsText.splitBlocks(text).map { RawSms(it) }, source = "demo seed")
        AppLog.d { "demo seeded: $result" }
    }
}
