package com.ares.mpesaledger.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.data.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailState(
    val tx: TransactionEntity? = null,
    val loaded: Boolean = false,
    val categories: List<String> = emptyList(),
) {
    val effectiveCategory: String? get() = tx?.let { it.categoryOverride ?: it.category }
}

class DetailViewModel(private val container: AppContainer, saved: SavedStateHandle) : ViewModel() {

    private val code: String = checkNotNull(saved[ARG_CODE])
    private val kind: String = checkNotNull(saved[ARG_KIND])
    private val repo = container.repository

    val state: StateFlow<DetailState> = combine(
        repo.observeTransaction(code, kind),
        repo.observeCategoryChoices(),
    ) { tx, categories -> DetailState(tx, loaded = true, categories = categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    /** Just this transaction: stored as an override that "Recategorize all" never touches. */
    fun setCategory(category: String) = launchInApp {
        val tx = state.value.tx ?: return@launchInApp
        repo.setOverride(code, kind, category.takeIf { it != tx.category })
    }

    /** Creates a user rule for this counterparty and re-applies all rules. */
    fun alwaysCategorize(category: String) = launchInApp {
        val tx = state.value.tx ?: return@launchInApp
        repo.addUserRuleAndRecategorize(tx.counterparty, category, code, kind)
    }

    fun resetCategory() = launchInApp { repo.setOverride(code, kind, null) }

    // Writes run in the app scope so leaving the screen mid-write doesn't cancel them.
    private fun launchInApp(block: suspend () -> Unit) { container.appScope.launch { block() } }

    companion object {
        const val ARG_CODE = "code"
        const val ARG_KIND = "kind"
    }
}
