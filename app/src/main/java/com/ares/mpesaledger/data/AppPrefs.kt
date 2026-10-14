package com.ares.mpesaledger.data

import android.content.Context

/** Small UI preferences. Ledger data lives in Room, never here. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** "Transactions" notifications are opt-in (spec: off by default). */
    var notifyTransactions: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_TRANSACTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_TRANSACTIONS, value).apply()

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_NOTIFY_TRANSACTIONS = "notify_transactions"
    }
}
