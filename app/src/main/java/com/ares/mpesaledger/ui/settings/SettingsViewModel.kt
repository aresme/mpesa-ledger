package com.ares.mpesaledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.data.ImportProgress
import com.ares.mpesaledger.data.ImportResult
import com.ares.mpesaledger.data.LedgerCounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsState(
    val counts: LedgerCounts? = null,
    val progress: ImportProgress? = null,
    val lastImport: ImportResult? = null,
    val hasSmsPermission: Boolean = false,
    val isDemo: Boolean = false,
    val databaseName: String = "",
    val notifyTransactions: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val permission = MutableStateFlow(container.inboxSync.hasPermission())
    private val notifyTransactions = MutableStateFlow(container.prefs.notifyTransactions)

    val state: StateFlow<SettingsState> = combine(
        container.repository.observeCounts(),
        container.importer.progress,
        container.importer.lastResult,
        combine(permission, notifyTransactions) { p, n -> p to n },
    ) { counts, progress, last, (perm, notify) ->
        SettingsState(counts, progress, last, perm, container.isDemo, container.databaseName, notify)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState(isDemo = container.isDemo))

    fun refreshPermission() { permission.value = container.inboxSync.hasPermission() }

    fun onPermissionResult(granted: Boolean) {
        refreshPermission()
        if (granted) container.syncInboxInBackground()
    }

    fun setNotifyTransactions(on: Boolean) {
        container.prefs.notifyTransactions = on
        notifyTransactions.value = on
    }

    fun reimportEverything() = container.syncInboxInBackground(full = true)
}
