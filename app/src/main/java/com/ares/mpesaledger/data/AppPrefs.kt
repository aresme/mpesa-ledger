package com.ares.mpesaledger.data

import android.content.Context
import com.ares.mpesaledger.parser.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    var appLock: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    /** Bill reminders 2 days before a detected bill's usual day. On by default. */
    var billReminders: Boolean
        get() = prefs.getBoolean(KEY_BILL_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_BILL_REMINDERS, value).apply()

    private val _hiddenBills = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_BILLS, emptySet())!!.toSet())

    /** Bill keys the user marked "not a bill". */
    val hiddenBills: StateFlow<Set<String>> = _hiddenBills.asStateFlow()

    fun setBillHidden(key: String, hidden: Boolean) {
        val next = if (hidden) _hiddenBills.value + key else _hiddenBills.value - key
        prefs.edit().putStringSet(KEY_HIDDEN_BILLS, next).apply()
        _hiddenBills.value = next
    }

    /** "billKey|dueDate" entries already reminded, so each due date notifies once. */
    var notifiedBills: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED_BILLS, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_NOTIFIED_BILLS, value).apply()

    /** Other financial senders to read besides M-Pesa (Settings → Sources). All on by default. */
    var enabledSources: Set<Source>
        get() {
            val off = prefs.getStringSet(KEY_SOURCES_OFF, emptySet())!!
            return Source.entries.filter { it != Source.MPESA && it.key !in off }.toSet()
        }
        set(value) = prefs.edit().putStringSet(KEY_SOURCES_OFF, Source.entries.filter { it != Source.MPESA && it !in value }.map { it.key }.toSet()).apply()

    private companion object {
        const val KEY_SOURCES_OFF = "sources_off"
        const val KEY_BILL_REMINDERS = "bill_reminders"
        const val KEY_HIDDEN_BILLS = "hidden_bills"
        const val KEY_NOTIFIED_BILLS = "notified_bills"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_NOTIFY_TRANSACTIONS = "notify_transactions"
    }
}
