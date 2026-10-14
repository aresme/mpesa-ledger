package com.ares.mpesaledger.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.data.ListFilter
import com.ares.mpesaledger.data.TxListItem
import com.ares.mpesaledger.ui.format.localDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** One day of transactions; [date] null groups rows whose time is unknown. */
data class DaySection(val date: LocalDate?, val netCents: Long, val items: List<TxListItem>)

data class TransactionsState(
    val filter: ListFilter = ListFilter(),
    val sections: List<DaySection> = emptyList(),
    val total: Int = 0,
    val loaded: Boolean = false,
    val months: List<YearMonth> = emptyList(),
    val categories: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionsViewModel(container: AppContainer, saved: SavedStateHandle) : ViewModel() {

    private val repo = container.repository

    private val filter = MutableStateFlow(
        ListFilter(
            month = saved.get<String>(ARG_MONTH)?.takeIf { it.isNotBlank() }?.let(YearMonth::parse),
            category = saved.get<String>(ARG_CATEGORY)?.takeIf { it.isNotBlank() },
        )
    )

    // Debounce typing only; chip changes go through immediately because they change other fields.
    private val rows = filter.debounce { if (it.query.isNotEmpty()) 200L else 0L }
        .flatMapLatest { f -> repo.observeList(f).map { f to it } }

    val state: StateFlow<TransactionsState> = combine(
        filter,
        rows,
        repo.observeMonthSummaries(),
        repo.observeCategoryChoices(),
    ) { f, (_, items), summaries, categories ->
        TransactionsState(
            filter = f,
            sections = group(items),
            total = items.size,
            loaded = true,
            months = summaries.map { YearMonth.parse(it.month) },
            categories = categories,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState(filter = filter.value))

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setMonth(m: YearMonth?) { filter.value = filter.value.copy(month = m) }
    fun setKind(k: String?) { filter.value = filter.value.copy(kind = k) }
    fun setCategory(c: String?) { filter.value = filter.value.copy(category = c) }
    fun clearFilters() { filter.value = ListFilter(query = filter.value.query) }

    companion object {
        const val ARG_MONTH = "month"
        const val ARG_CATEGORY = "category"

        fun group(items: List<TxListItem>): List<DaySection> =
            items.groupBy { it.timestamp?.let(::localDate) }.map { (date, rows) ->
                DaySection(date, rows.sumOf { signedCents(it) }, rows)
            }

        /** In is positive, out (plus its fee) negative, NONE-direction rows don't count. */
        fun signedCents(row: TxListItem): Long = when (row.direction) {
            "in" -> row.amountCents
            "out" -> -(row.amountCents + (row.feeCents ?: 0))
            else -> 0
        }
    }
}
