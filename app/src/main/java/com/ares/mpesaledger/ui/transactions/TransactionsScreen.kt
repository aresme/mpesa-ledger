package com.ares.mpesaledger.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.ares.mpesaledger.data.TxListItem
import com.ares.mpesaledger.parser.Kind
import com.ares.mpesaledger.ui.appViewModel
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.EmptyState
import com.ares.mpesaledger.ui.components.InitialAvatar
import com.ares.mpesaledger.ui.format.dayLabel
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.formatSigned
import com.ares.mpesaledger.ui.format.kindLabel
import com.ares.mpesaledger.ui.format.label
import com.ares.mpesaledger.ui.format.timeLabel
import com.ares.mpesaledger.ui.theme.AresBlack
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresOutline
import com.ares.mpesaledger.ui.theme.AresSurface
import com.ares.mpesaledger.ui.theme.Num

/** Out amounts use a slightly muted foreground so money in (green) stands out. */
private val OutAmount = Color(0xFFC9D1CB)

@Composable
fun TransactionsScreen(onOpen: (code: String, kind: String) -> Unit) {
    val vm = appViewModel { c, saved -> TransactionsViewModel(c, saved) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.filter

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Activity")
        OutlinedTextField(
            value = f.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search name, account or code", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (f.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = AresSurface, focusedContainerColor = AresSurface,
                unfocusedBorderColor = AresOutline,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipMenu(
                label = f.month?.label() ?: "All months",
                selected = f.month != null,
                options = listOf(null to "All months") + state.months.map { it to it.label() },
                onPick = vm::setMonth,
            )
            ChipMenu(
                label = f.category ?: "Category",
                selected = f.category != null,
                options = listOf(null to "All categories") + state.categories.map { it to it },
                onPick = vm::setCategory,
            )
            ChipMenu(
                label = f.kind?.let(::kindLabel) ?: "Type",
                selected = f.kind != null,
                options = listOf<Pair<String?, String>>(null to "All types") + Kind.entries.map { it.key to kindLabel(it.key) },
                onPick = vm::setKind,
            )
        }

        if (state.loaded && state.total == 0) {
            val filtered = f.month != null || f.kind != null || f.category != null || f.query.isNotBlank()
            EmptyState(
                icon = Icons.Filled.Search,
                title = if (filtered) "No matches" else "No transactions yet",
                message = if (filtered) "Nothing matches these filters." else "Transactions appear here once your M-Pesa messages are imported.",
                primaryAction = if (filtered) "Clear filters" to { vm.clearFilters(); vm.setQuery("") } else null,
            )
        } else {
            TransactionList(state.sections, onOpen)
        }
    }
}

@Composable
private fun TransactionList(sections: List<DaySection>, onOpen: (String, String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        sections.forEach { section ->
            stickyHeader(key = "h-${section.date}") {
                Row(
                    Modifier.fillMaxWidth().background(AresBlack).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(section.date?.let { dayLabel(it) } ?: "Date unknown", style = MaterialTheme.typography.labelLarge, color = AresMuted, modifier = Modifier.weight(1f))
                    if (section.netCents != 0L) {
                        Text(formatSigned(section.netCents, section.netCents > 0), style = Num.small, color = AresMuted)
                    }
                }
            }
            items(section.items, key = { "${it.code}/${it.kind}" }) { row -> TransactionRow(row) { onOpen(row.code, row.kind) } }
        }
    }
}

@Composable
fun TransactionRow(row: TxListItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(row.counterparty, highlight = row.direction == "in")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.counterparty, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(row.category, row.timestamp?.let(::timeLabel)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = AresMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            when (row.direction) {
                "in" -> Text(formatSigned(row.amountCents, true), style = Num.medium, color = AresGreen)
                "out" -> Text(formatSigned(row.amountCents, false), style = Num.medium, color = OutAmount)
                else -> Text(formatCents(row.amountCents), style = Num.medium, color = AresMuted)
            }
            Text(kindLabel(row.kind), style = MaterialTheme.typography.bodySmall, color = AresMuted)
        }
    }
}

@Composable
private fun <T> ChipMenu(label: String, selected: Boolean, options: List<Pair<T?, String>>, onPick: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = AresGreen,
                selectedTrailingIconColor = AresGreen,
            ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}
