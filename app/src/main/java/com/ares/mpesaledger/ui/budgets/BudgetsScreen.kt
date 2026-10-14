package com.ares.mpesaledger.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.domain.BudgetRules
import com.ares.mpesaledger.ui.appViewModel
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.EmptyState
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.label
import com.ares.mpesaledger.ui.format.parseKshToCents
import com.ares.mpesaledger.ui.theme.AresAmber
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresRed
import com.ares.mpesaledger.ui.theme.AresSurfaceHighest
import com.ares.mpesaledger.ui.theme.Num

@Composable
fun BudgetsScreen() {
    val vm = appViewModel { c, _ -> BudgetsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Budgets")
        if (state.loaded && state.rows.isEmpty()) {
            EmptyState(
                icon = AppIcons.PieChart,
                title = "Set a monthly budget",
                message = "Pick a category and a monthly limit. You'll get a notification at 80% and again at 100%, once each per month.",
                primaryAction = "Add a budget" to { adding = true },
            )
        } else if (state.loaded) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(state.month.label(), style = MaterialTheme.typography.titleMedium) }
                items(state.rows, key = { it.category }) { row -> BudgetCard(row) { editing = row } }
                item {
                    OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null); Text("  Add budget")
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        BudgetDialog(
            existing = editing,
            choices = state.choices.filter { c -> state.rows.none { it.category == c } || c == editing?.category },
            onDismiss = { adding = false; editing = null },
            onSave = { category, cents -> vm.save(category, cents); adding = false; editing = null },
            onDelete = { editing?.let { vm.delete(it.category) }; editing = null },
        )
    }
}

private fun levelColor(level: Int): Color = when {
    level >= BudgetRules.OVER -> AresRed
    level >= BudgetRules.WARN -> AresAmber
    else -> AresGreen
}

@Composable
private fun BudgetCard(row: BudgetRow, onClick: () -> Unit) {
    val color = levelColor(row.level)
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (row.level >= BudgetRules.WARN) {
                // Status is never colour-only: icon + words alongside the amber/red bar.
                Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.padding(end = 6.dp))
                Text(if (row.level >= BudgetRules.OVER) "Over budget" else "Almost there", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatCents(row.spentCents), style = Num.large)
            Text("  of ${formatCents(row.limitCents)}", style = Num.small, color = AresMuted, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).background(AresSurfaceHighest, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(row.fraction.coerceIn(0.02f, 1f)).height(10.dp).background(color, RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(8.dp))
        val left = row.limitCents - row.spentCents
        Text(
            "${(row.fraction * 100).toInt()}% used · " + if (left >= 0) "${formatCents(left)} left" else "${formatCents(-left)} over",
            style = MaterialTheme.typography.bodySmall, color = AresMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(
    existing: BudgetRow?,
    choices: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    onDelete: () -> Unit,
) {
    var category by remember { mutableStateOf(existing?.category.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.let { formatCents(it.limitCents).replace(",", "") }.orEmpty()) }
    val cents = parseKshToCents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New budget" else existing.category) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing == null) {
                    OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    // Wrapping chips: every choice is visible without sideways scrolling.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { c -> FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) }) }
                    }
                }
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Monthly limit (Ksh)") }, singleLine = true,
                    isError = amount.isNotBlank() && (cents == null || cents == 0L),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(category.trim(), cents!!) }, enabled = category.isNotBlank() && cents != null && cents > 0) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onDelete) { Text("Delete", color = AresRed) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
