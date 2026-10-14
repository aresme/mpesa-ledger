package com.ares.mpesaledger.ui.importstatus

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.data.ImportProgress
import com.ares.mpesaledger.data.ImportResult
import com.ares.mpesaledger.data.LedgerCounts
import com.ares.mpesaledger.domain.MonthSummary
import com.ares.mpesaledger.sms.SmsFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportStatusState(
    val isDemo: Boolean = false,
    val databaseName: String = "",
    val hasSmsPermission: Boolean = false,
    val counts: LedgerCounts? = null,
    val progress: ImportProgress? = null,
    val lastResult: ImportResult? = null,
    val months: List<MonthSummary> = emptyList(),
    val error: String? = null,
)

/** Phase 2 temporary debug screen: import controls and counts. Replaced by real screens in Phase 3. */
class ImportStatusViewModel(private val container: AppContainer) : ViewModel() {

    private val permission = MutableStateFlow(container.inboxSync.hasPermission())
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ImportStatusState> = combine(
        container.repository.observeCounts(),
        container.repository.observeMonthSummaries(),
        container.importer.progress,
        container.importer.lastResult,
        combine(permission, error) { p, e -> p to e },
    ) { counts, months, progress, last, (perm, err) ->
        ImportStatusState(container.isDemo, container.databaseName, perm, counts, progress, last, months, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportStatusState())

    fun refreshPermission() {
        permission.value = container.inboxSync.hasPermission()
    }

    fun syncInbox(full: Boolean) = container.syncInboxInBackground(full)

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val messages = withContext(Dispatchers.IO) { SmsFileReader.read(container.appContext.contentResolver, uri) }
                container.importer.import(messages, source = "file")
            }.onFailure {
                AppLog.w({ "file import failed" }, it)
                error.value = "File import failed: ${it.message}"
            }.onSuccess { error.value = null }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportStatusViewModel(container) }
        }
    }
}
