package com.ares.mpesaledger.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.domain.Bill
import com.ares.mpesaledger.domain.BillReminder
import com.ares.mpesaledger.domain.BillStatus
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.EmptyState
import com.ares.mpesaledger.ui.components.InitialAvatar
import com.ares.mpesaledger.ui.components.SectionTitle
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.fullLabel
import com.ares.mpesaledger.ui.format.kindLabel
import com.ares.mpesaledger.ui.theme.AresAmber
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresRed
import com.ares.mpesaledger.ui.theme.Num
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Status line + colour. Never colour-only: the words carry the meaning. */
fun billStatusText(b: Bill): Pair<String, Color> = when (b.status) {
    BillStatus.OVERDUE -> "Not paid yet · was due ${b.nextDue.format(DAY)}" to AresRed
    BillStatus.DUE_SOON -> when {
        b.daysUntilDue < 0 -> "Due ${-b.daysUntilDue} day${if (b.daysUntilDue == -1L) "" else "s"} ago · ${b.nextDue.format(DAY)}"
        b.daysUntilDue == 0L -> "Due today"
        b.daysUntilDue == 1L -> "Due tomorrow"
        else -> "Due in ${b.daysUntilDue} days · ${b.nextDue.format(DAY)}"
    } to AresAmber
    BillStatus.UPCOMING -> "Due ${b.nextDue.format(DAY)} · in ${b.daysUntilDue} days" to AresMuted
    BillStatus.PAID -> "Paid this month · next ${b.nextDue.format(DAY)}" to AresGreen
}

@Composable
fun BillsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val bills by container.repository.observeBills().collectAsStateWithLifecycle(initialValue = null)
    val hidden by container.prefs.hiddenBills.collectAsStateWithLifecycle()
    var reminders by remember { mutableStateOf(container.prefs.billReminders) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Bills", onBack = onBack)
        val all = bills ?: return@Column
        val visible = all.filter { it.key !in hidden }
        val hiddenBills = all.filter { it.key in hidden }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Payments you make to the same place in at least 3 of the last 6 months show up here, " +
                        "with the day and amount you usually pay.",
                    style = MaterialTheme.typography.bodyMedium, color = AresMuted,
                )
            }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reminders", style = MaterialTheme.typography.bodyLarge)
                            Text("A notification 2 days before a bill is usually due", style = MaterialTheme.typography.bodySmall, color = AresMuted)
                        }
                        Switch(checked = reminders, onCheckedChange = { reminders = it; container.prefs.billReminders = it })
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = "No repeat payments yet",
                        message = "Once you've paid the same bill (KPLC, rent, school fees…) in 3 different months, it appears here.",
                    )
                }
            }
            items(visible, key = { it.key }) { bill -> BillCard(bill, actionLabel = "Not a bill") { container.prefs.setBillHidden(bill.key, true) } }
            if (hiddenBills.isNotEmpty()) {
                item { SectionTitle("Hidden (${hiddenBills.size})") }
                items(hiddenBills, key = { "h-" + it.key }) { bill -> BillCard(bill, actionLabel = "Show again") { container.prefs.setBillHidden(bill.key, false) } }
            }
        }
    }
}

@Composable
private fun BillCard(bill: Bill, actionLabel: String, onAction: () -> Unit) {
    val (status, color) = billStatusText(bill)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(bill.name, size = 40)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(bill.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(kindLabel(bill.kind), bill.account?.let { "account $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = AresMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatCents(bill.usualAmountCents), style = Num.medium)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when (bill.status) {
                BillStatus.PAID -> Icons.Filled.Check
                BillStatus.OVERDUE, BillStatus.DUE_SOON -> Icons.Filled.Warning
                BillStatus.UPCOMING -> Icons.Filled.DateRange
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 6.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Text(
            "Usually around the ${BillReminder.ordinal(bill.usualDay)} · seen ${bill.monthsSeen} months · last ${formatCents(bill.lastAmountCents)} on ${fullLabel(bill.lastPaidAt).substringBefore(" ·")}",
            style = MaterialTheme.typography.bodySmall, color = AresMuted,
        )
        TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(actionLabel) }
    }
}
