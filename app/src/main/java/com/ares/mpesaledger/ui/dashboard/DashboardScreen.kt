package com.ares.mpesaledger.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.data.RecipientTotal
import com.ares.mpesaledger.domain.MonthTotals
import com.ares.mpesaledger.ui.appViewModel
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.BarItem
import com.ares.mpesaledger.ui.components.CategoryBars
import com.ares.mpesaledger.ui.components.EmptyState
import com.ares.mpesaledger.ui.components.HeroAmount
import com.ares.mpesaledger.ui.components.InitialAvatar
import com.ares.mpesaledger.ui.components.MonthSwitcher
import com.ares.mpesaledger.ui.components.SectionTitle
import com.ares.mpesaledger.ui.components.TrendChart
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.label
import com.ares.mpesaledger.ui.onboarding.rememberFileImport
import com.ares.mpesaledger.ui.onboarding.rememberSmsPermissionRequest
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.Num
import java.time.YearMonth

@Composable
fun DashboardScreen(
    onOpenCategory: (category: String, month: YearMonth) -> Unit,
    onOpenActivity: () -> Unit,
) {
    val vm = appViewModel { c, _ -> DashboardViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val requestSms = rememberSmsPermissionRequest(vm::onPermissionResult)
    val importFile = rememberFileImport()
    LifecycleResumeEffect(Unit) { vm.refreshPermission(); onPauseOrDispose { } }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "M-Pesa Ledger")
        when {
            !state.loaded -> Unit
            state.ledgerEmpty && state.progress != null -> ImportingCard(state.progress!!.done, state.progress!!.total)
            state.ledgerEmpty && !state.hasSmsPermission -> EmptyState(
                icon = AppIcons.Sms,
                title = "Allow SMS access",
                message = "M-Pesa Ledger reads only messages from MPESA to build your ledger. Nothing leaves this phone: the app has no internet access at all.",
                primaryAction = "Allow SMS access" to requestSms,
                secondaryAction = "Import from a file instead" to importFile,
            )
            state.ledgerEmpty -> EmptyState(
                icon = Icons.Filled.Search,
                title = "No M-Pesa messages found",
                message = "There are no SMS from MPESA in this phone's inbox. If your messages are on another phone, export them (e.g. with Termux's termux-sms-list) and import the file.",
                primaryAction = "Import from a file" to importFile,
            )
            else -> DashboardContent(state, vm::previousMonth, vm::nextMonth, onOpenCategory, onOpenActivity)
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenCategory: (String, YearMonth) -> Unit,
    onOpenActivity: () -> Unit,
) {
    var selectedBar by rememberSaveable(state.month.toString()) { mutableIntStateOf(11) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonthSwitcher(state.month.label(), canGoNext = !state.isCurrentMonth, onPrevious, onNext) }
        state.progress?.let { p -> item { ImportingCard(p.done, p.total) } }

        val totals = state.totals!!
        if (state.monthEmpty) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = "Nothing in ${state.month.label()}",
                        message = "No M-Pesa transactions this month yet. Use the arrows to look at earlier months.",
                        primaryAction = "Previous month" to onPrevious,
                    )
                }
            }
        } else {
            item { NetCard(totals) }
            item { StatGrid(totals) }
            if (state.categories.isNotEmpty()) {
                item {
                    AppCard {
                        SectionTitle("Spending by category")
                        CategoryBars(
                            items = state.categories.map { BarItem(it.category, it.totalCents, it.count) },
                            onClick = { onOpenCategory(it.label, state.month) },
                        )
                    }
                }
            }
            if (state.recipients.isNotEmpty()) {
                item {
                    AppCard {
                        SectionTitle("Top recipients") { TextButton(onClick = onOpenActivity) { Text("See all") } }
                        state.recipients.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp))
                            RecipientRow(r)
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                SectionTitle("Last 12 months")
                TrendChart(points = state.trend, selected = selectedBar, onSelect = { selectedBar = it })
            }
        }
    }
}

@Composable
private fun NetCard(t: MonthTotals) {
    AppCard {
        Text("Net this month", style = MaterialTheme.typography.labelLarge, color = AresMuted)
        Spacer(Modifier.height(4.dp))
        HeroAmount(
            "Ksh " + formatCents(t.netCents),
            color = if (t.netCents >= 0) AresGreen else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row {
            LabeledAmount("Money in", t.inCents, AresGreen, Modifier.weight(1f))
            LabeledAmount("Money out", t.outCents, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatGrid(t: MonthTotals) {
    val feeShare = if (t.outCents > 0) " · ${"%.1f".format(t.feeCents * 100.0 / t.outCents)}% of out" else ""
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Fees", t.feeCents, feeShare.removePrefix(" · ").ifEmpty { null }, Modifier.weight(1f))
            StatTile(
                "Saved", t.savedCents,
                if (t.savingsWithdrawnCents > 0) "${formatCents(t.savingsWithdrawnCents)} taken out" else "to savings",
                Modifier.weight(1f),
            )
        }
        if (t.fulizaCents > 0 || t.fulizaRepaidCents > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Fuliza borrowed", t.fulizaCents, null, Modifier.weight(1f))
                StatTile("Fuliza repaid", t.fulizaRepaidCents, null, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, cents: Long, caption: String?, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AresMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(formatCents(cents), style = Num.large, maxLines = 1)
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = AresMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LabeledAmount(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AresMuted)
        Text(formatCents(cents), style = Num.medium, color = color)
    }
}

@Composable
private fun RecipientRow(r: RecipientTotal) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        InitialAvatar(r.counterparty, size = 36)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(r.counterparty, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (r.count == 1) "1 payment" else "${r.count} payments", style = MaterialTheme.typography.bodySmall, color = AresMuted)
        }
        Text(formatCents(r.totalCents), style = Num.medium)
    }
}

@Composable
private fun ImportingCard(done: Int, total: Int) {
    AppCard(Modifier.padding(horizontal = 0.dp)) {
        Text("Importing your M-Pesa messages…", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text("$done / $total", style = Num.small, color = AresMuted)
    }
}
