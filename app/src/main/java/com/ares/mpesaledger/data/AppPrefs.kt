package com.ares.mpesaledger.data

import android.content.Context

/** Small UI preferences. Ledger data lives in Room, never here. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
