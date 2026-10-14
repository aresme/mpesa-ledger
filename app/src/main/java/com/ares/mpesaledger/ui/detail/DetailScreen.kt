package com.ares.mpesaledger.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.data.TransactionEntity
import com.ares.mpesaledger.ui.appViewModel
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.HeroAmount
import com.ares.mpesaledger.ui.components.InitialAvatar
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.format.formatKsh
import com.ares.mpesaledger.ui.format.fullLabel
import com.ares.mpesaledger.ui.format.kindLabel
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresSurfaceHigh
import com.ares.mpesaledger.ui.theme.Mono
import com.ares.mpesaledger.ui.theme.Num

@Composable
fun DetailScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, saved -> DetailViewModel(c, saved) }
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val tx = state.tx

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Transaction", onBack = onBack)
        if (tx == null) {
            if (state.loaded) Text("This transaction no longer exists.", color = AresMuted, modifier = Modifier.padding(16.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Header(tx) }
            item {
                AppCard {
                    Field("Category", state.effectiveCategory.orEmpty(), trailing = {
                        IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Edit, contentDescription = "Change category", tint = AresGreen) }
                    }, onClick = { picking = true })
                    if (tx.categoryOverride != null) {
                        Text("Set manually (rules give \"${tx.category}\")", style = MaterialTheme.typography.bodySmall, color = AresMuted)
                        TextButton(onClick = vm::resetCategory, contentPadding = PaddingValues(0.dp)) { Text("Use rule category") }
                    }
                    Divider()
                    Field("Type", kindLabel(tx.kind))
                    tx.timestamp?.let { Divider(); Field("Date", fullLabel(it)) }
                    tx.feeCents?.let { Divider(); Field("Fee", formatKsh(it)) }
                    tx.balanceCents?.let {
                        Divider()
                        Field(when (tx.kind) { "okoa" -> "Total Okoa debt"; "airtime_recharge" -> "Airtime balance"; else -> "Balance after" }, formatKsh(it))
                    }
                    tx.account?.let {
                        Divider()
                        Field(
                            when (tx.kind) {
                                "withdraw" -> "Agent number"
                                "data_bundle" -> "Expires"
                                "okoa" -> "Pay by"
                                "bank_in" -> "Bank account"
                                else -> "Account"
                            },
                            it.removePrefix("expires ").removePrefix("due "),
                        )
                    }
                    tx.phone?.let { Divider(); Field("Phone", it) }
                    Divider()
                    Field("Code", tx.code, mono = true)
                }
            }
            item { RawSms(tx.raw) }
        }
    }

    if (picking && tx != null) {
        CategorySheet(
            current = state.effectiveCategory.orEmpty(),
            counterparty = tx.counterparty,
            choices = state.categories,
            onDismiss = { picking = false },
            onApply = { category, always ->
                picking = false
                if (always) vm.alwaysCategorize(category) else vm.setCategory(category)
            },
        )
    }
}

@Composable
private fun Header(tx: TransactionEntity) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        InitialAvatar(tx.counterparty, size = 56, highlight = tx.direction == "in")
        Spacer(Modifier.height(12.dp))
        Text(tx.counterparty, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        // Direction is spelled out below, so only money in carries a sign.
        HeroAmount(
            if (tx.kind == "data_bundle") "Data bundle" else (if (tx.direction == "in") "+" else "") + "Ksh ${formatCents(tx.amountCents)}",
            color = if (tx.direction == "in") AresGreen else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            when {
                tx.kind == "data_bundle" -> "Paid for through M-Pesa or airtime, so not counted again"
                tx.direction == "in" -> "Money in"
                tx.direction == "out" -> "Money out"
                else -> "Not counted in in/out totals"
            },
            style = MaterialTheme.typography.bodySmall, color = AresMuted,
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AresMuted, modifier = Modifier.width(110.dp))
        Text(value, style = if (mono) Mono.code else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        trailing()
    }
}

@Composable
private fun Divider() = HorizontalDivider()

@Composable
private fun RawSms(raw: String) {
    val context = LocalContext.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Original SMS", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { copyToClipboard(context, "M-Pesa SMS", raw) }) {
                Icon(AppIcons.ContentCopy, contentDescription = "Copy SMS", tint = AresGreen, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            raw,
            style = Mono.raw,
            modifier = Modifier.fillMaxWidth().background(AresSurfaceHigh, MaterialTheme.shapes.small).padding(12.dp),
        )
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own copy confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    current: String,
    counterparty: String,
    choices: List<String>,
    onDismiss: () -> Unit,
    onApply: (category: String, always: Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    var custom by remember { mutableStateOf("") }
    var always by remember { mutableStateOf(false) }
    val effective = custom.trim().ifEmpty { selected }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Category", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(280.dp)) {
                items(choices) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = c; custom = "" }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (c == selected && custom.isBlank()) Icon(Icons.Filled.Check, contentDescription = null, tint = AresGreen)
                    }
                }
            }
            OutlinedTextField(
                value = custom, onValueChange = { custom = it }, singleLine = true,
                label = { Text("Or a new category") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().clickable { always = !always }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Always categorize $counterparty as $effective", style = MaterialTheme.typography.bodyLarge)
                    Text("Creates a rule and updates all past and future transactions", style = MaterialTheme.typography.bodySmall, color = AresMuted)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = always, onCheckedChange = { always = it })
            }
            Button(
                onClick = { onApply(effective, always) },
                enabled = effective.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
