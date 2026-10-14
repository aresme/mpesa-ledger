package com.ares.mpesaledger.ui.fuliza

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.domain.FulizaEvent
import com.ares.mpesaledger.domain.FulizaSummary
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.EmptyState
import com.ares.mpesaledger.ui.components.HeroAmount
import com.ares.mpesaledger.ui.components.SectionTitle
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.fullLabel
import com.ares.mpesaledger.ui.theme.AresAmber
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresRed
import com.ares.mpesaledger.ui.theme.Num
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DUE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/** "Due in 3 days" with amber at <= 3 days and red when overdue; null when nothing is owed. */
fun dueText(s: FulizaSummary, today: LocalDate = LocalDate.now(MpesaParser.NAIROBI)): Pair<String, Color>? {
    if (s.owedCents <= 0) return null
    val days = s.daysUntilDue(today) ?: return "Due date unknown" to AresMuted
    val date = s.dueDate!!.format(DUE)
    return when {
        days < 0 -> "Overdue since $date" to AresRed
        days == 0L -> "Due today" to AresAmber
        days <= 3 -> "Due in $days day${if (days == 1L) "" else "s"} · $date" to AresAmber
        else -> "Due $date · in $days days" to AresMuted
    }
}

@Composable
fun FulizaScreen(onBack: () -> Unit) {
    val container = appContainer()
    val summary by container.repository.observeFuliza().collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Fuliza", onBack = onBack)
        val s = summary ?: return@Column
        if (!s.hasHistory) {
            EmptyState(
                icon = AppIcons.Sms,
                title = "No Fuliza yet",
                message = "When M-Pesa covers a payment with Fuliza, the loan, its fees and your repayments show up here.",
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OwedCard(s) }
            item {
                AppCard {
                    Stat("Fuliza limit", s.limitCents?.let(::formatCents) ?: "Shown after your next repayment")
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Stat("Access fees this month", formatCents(s.feesThisMonthCents))
                    Stat("Access fees this year", formatCents(s.feesThisYearCents))
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Stat("Borrowed this year", formatCents(s.borrowedThisYearCents))
                    Stat("Repaid this year", formatCents(s.repaidThisYearCents))
                }
            }
            item { SectionTitle("History") }
            items(s.events, key = { it.code + it.isLoan }) { e -> EventRow(e) }
            item {
                Text(
                    "Amount owed is worked out from your M-Pesa messages. Daily Fuliza charges aren't sent by SMS, " +
                        "so Safaricom's figure (*234#) can be slightly higher.",
                    style = MaterialTheme.typography.bodySmall, color = AresMuted,
                )
            }
        }
    }
}

@Composable
private fun OwedCard(s: FulizaSummary) {
    AppCard {
        Text("You owe Fuliza", style = MaterialTheme.typography.labelLarge, color = AresMuted)
        Spacer(Modifier.height(4.dp))
        HeroAmount("Ksh " + formatCents(s.owedCents), color = if (s.owedCents > 0) MaterialTheme.colorScheme.onSurface else AresGreen)
        Spacer(Modifier.height(6.dp))
        val due = dueText(s)
        if (due != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (due.second != AresMuted) Icon(Icons.Filled.Warning, contentDescription = null, tint = due.second, modifier = Modifier.padding(end = 6.dp))
                Text(due.first, style = MaterialTheme.typography.bodyMedium, color = due.second)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = AresGreen, modifier = Modifier.padding(end = 6.dp))
                Text(
                    "All repaid" + (s.lastClearedAt?.let { " · cleared ${fullLabel(it).substringBefore(" ·")}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = AresGreen,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AresMuted, modifier = Modifier.weight(1f))
        Text(value, style = Num.medium)
    }
}

@Composable
private fun EventRow(e: FulizaEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    e.isLoan -> "Borrowed"
                    e.details.fullyRepaid == true -> "Repaid in full"
                    else -> "Partly repaid"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                listOfNotNull(
                    e.timestamp?.let(::fullLabel),
                    e.feeCents.takeIf { e.isLoan && it > 0 }?.let { "fee ${formatCents(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = AresMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            (if (e.isLoan) "+" else "−") + formatCents(e.amountCents),
            style = Num.medium,
            color = if (e.isLoan) MaterialTheme.colorScheme.onSurface else AresGreen,
        )
    }
}
