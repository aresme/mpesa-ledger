package com.ares.mpesaledger.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ares.mpesaledger.AppContainer
import com.ares.mpesaledger.data.CategoryTotal
import com.ares.mpesaledger.data.ImportProgress
import com.ares.mpesaledger.data.ImportResult
import com.ares.mpesaledger.data.LedgerCounts
import com.ares.mpesaledger.data.RecipientTotal
import com.ares.mpesaledger.domain.Bill
import com.ares.mpesaledger.domain.FulizaSummary
import com.ares.mpesaledger.domain.MonthSummary
import com.ares.mpesaledger.domain.MonthTotals
import com.ares.mpesaledger.ui.components.TrendPoint
import com.ares.mpesaledger.ui.format.nowMonth
import com.ares.mpesaledger.ui.format.shortLabel
import com.ares.mpesaledger.ui.format.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class DashboardState(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    val totals: MonthTotals? = null,
    val categories: List<CategoryTotal> = emptyList(),
    val recipients: List<RecipientTotal> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val counts: LedgerCounts? = null,
    val progress: ImportProgress? = null,
    val lastImport: ImportResult? = null,
    val hasSmsPermission: Boolean = false,
    val isDemo: Boolean = false,
    val fuliza: FulizaSummary? = null,
    /** Bills needing attention (overdue / due soon / upcoming), hidden ones removed. */
    val bills: List<Bill> = emptyList(),
) {
    val loaded: Boolean get() = counts != null && totals != null
    val ledgerEmpty: Boolean get() = counts?.transactions == 0
    val monthEmpty: Boolean
        get() = totals?.let { it.inCents == 0L && it.outCents == 0L && it.feeCents == 0L && it.fulizaCents == 0L && it.fulizaRepaidCents == 0L && it.savedCents == 0L && it.savingsWithdrawnCents == 0L } ?: false
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val month = MutableStateFlow(nowMonth())
    private val permission = MutableStateFlow(container.inboxSync.hasPermission())

    val state: StateFlow<DashboardState> = month.flatMapLatest { m ->
        val monthData = combine(
            repo.observeTotals(m),
            repo.observeCategorySpending(m),
            repo.observeTopRecipients(m),
            repo.observeMonthSummaries(),
        ) { totals, categories, recipients, summaries -> MonthData(totals, categories, recipients, trendFor(m, summaries)) }
        combine(
            monthData,
            repo.observeCounts(),
            container.importer.progress,
            container.importer.lastResult,
            permission,
        ) { data, counts, progress, last, perm ->
            DashboardState(
                month = m,
                isCurrentMonth = m >= nowMonth(),
                totals = data.totals,
                categories = data.categories,
                recipients = data.recipients,
                trend = data.trend,
                counts = counts,
                progress = progress,
                lastImport = last,
                hasSmsPermission = perm,
                isDemo = container.isDemo,
            )
        }
    }.combine(repo.observeFuliza()) { state, fuliza -> state.copy(fuliza = fuliza) }
        .combine(combine(repo.observeBills(), container.prefs.hiddenBills) { b, h -> b.filter { it.key !in h } }) { state, bills ->
            state.copy(bills = bills)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState(nowMonth(), true))

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { if (month.value < nowMonth()) month.value = month.value.plusMonths(1) }

    fun onPermissionResult(granted: Boolean) {
        permission.value = container.inboxSync.hasPermission()
        if (granted) {
            container.prefs.onboardingDone = true
            container.syncInboxInBackground()
        }
    }

    fun refreshPermission() { permission.value = container.inboxSync.hasPermission() }

    private data class MonthData(
        val totals: MonthTotals,
        val categories: List<CategoryTotal>,
        val recipients: List<RecipientTotal>,
        val trend: List<TrendPoint>,
    )

    companion object {
        /** 12 months ending at [end], zero-filled; spending = out + fees. */
        fun trendFor(end: YearMonth, summaries: List<MonthSummary>): List<TrendPoint> {
            val byMonth = summaries.associateBy { it.month }
            return (11 downTo 0).map { back ->
                val m = end.minusMonths(back.toLong())
                val s = byMonth[m.toString()]
                TrendPoint(
                    label = m.shortLabel(),
                    title = m.label(),
                    incomeCents = s?.inCents ?: 0,
                    spendingCents = (s?.outCents ?: 0) + (s?.feeCents ?: 0),
                )
            }
        }
    }
}
