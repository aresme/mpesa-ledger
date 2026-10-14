package com.ares.mpesaledger.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.domain.BudgetRules
import com.ares.mpesaledger.ui.format.nowMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class BudgetRow(val category: String, val limitCents: Long, val spentCents: Long) {
    val level: Int get() = BudgetRules.level(spentCents, limitCents)
    val fraction: Float get() = if (limitCents <= 0) 0f else (spentCents.toFloat() / limitCents)
}

data class BudgetsState(
    val month: YearMonth = nowMonth(),
    val rows: List<BudgetRow> = emptyList(),
    val choices: List<String> = emptyList(),
    val loaded: Boolean = false,
)

/** This month's spending per category against its monthly budget. */
class BudgetsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val month = nowMonth()

    val state: StateFlow<BudgetsState> = combine(
        repo.observeBudgets(),
        repo.observeCategorySpending(month),
        repo.observeCategoryChoices(),
    ) { budgets, spending, choices ->
        val spent = spending.associate { it.category to it.totalCents }
        BudgetsState(
            month = month,
            rows = budgets.map { BudgetRow(it.category, it.monthlyLimitCents, spent[it.category] ?: 0) },
            choices = choices,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState())

    /** Saving re-runs the checker, so a limit that's already exceeded alerts right away. */
    fun save(category: String, limitCents: Long) = container.appScope.launch {
        repo.setBudget(category, limitCents)
        container.budgetChecker.check()
    }

    fun delete(category: String) = container.appScope.launch { repo.deleteBudget(category) }
}
