package com.ares.mpesaledger

import android.util.Log

/**
 * The only logging entry point. Every call is a no-op in release builds, so SMS bodies,
 * balances and amounts can never reach logcat from a release APK.
 *
 * Debug lines are emitted at INFO, not DEBUG: Transsion XOS ships with
 * `persist.log.tag=I`, which silently drops Log.d() system-wide.
 */
object AppLog {
    const val TAG = "MpesaLedger"

    fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message())
    }

    fun w(message: () -> String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message(), error)
    }
}
