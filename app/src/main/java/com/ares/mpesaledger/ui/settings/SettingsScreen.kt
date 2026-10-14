package com.ares.mpesaledger.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.BuildConfig
import com.ares.mpesaledger.MainActivity
import com.ares.mpesaledger.ui.lock.AppLock
import com.ares.mpesaledger.data.ImportResult
import com.ares.mpesaledger.ui.appViewModel
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.CountBadge
import com.ares.mpesaledger.ui.components.ListRow
import com.ares.mpesaledger.ui.onboarding.rememberFileImport
import com.ares.mpesaledger.ui.onboarding.rememberSmsPermissionRequest
import com.ares.mpesaledger.ui.theme.AresAmber
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresRed
import com.ares.mpesaledger.ui.theme.AresSurface
import com.ares.mpesaledger.ui.theme.Num

@Composable
fun SettingsScreen(
    onOpenUnparsed: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSources: () -> Unit,
    demoToggleAvailable: Boolean,
    onToggleDemo: (Boolean) -> Unit,
) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestSms = rememberSmsPermissionRequest(vm::onPermissionResult)
    val importFile = rememberFileImport()
    LifecycleResumeEffect(Unit) { vm.refreshPermission(); onPauseOrDispose { } }
    val data = rememberDataActions()
    var wiping by remember { mutableStateOf(false) }
    val activity = context as? MainActivity
    val lockAvailable = remember { AppLock.isAvailable(context) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Settings")
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Group("Import") {
                    if (!state.isDemo) {
                        ListRow(
                            title = "SMS access",
                            subtitle = if (state.hasSmsPermission) "Allowed · reads only the senders under Sources" else "Not allowed · tap to allow",
                            icon = AppIcons.Sms,
                            iconTint = if (state.hasSmsPermission) MaterialTheme.colorScheme.primary else AresAmber,
                            onClick = if (state.hasSmsPermission) null else requestSms,
                        )
                        HorizontalDivider()
                        ListRow(
                            title = "Re-import everything",
                            subtitle = "Re-reads the whole inbox. Nothing is duplicated.",
                            icon = Icons.Filled.Refresh,
                            onClick = if (state.hasSmsPermission) vm::reimportEverything else null,
                        )
                        HorizontalDivider()
                    }
                    ListRow(
                        title = "Sources",
                        subtitle = "M-Pesa plus GlobalPay, Safaricom, Okoa Jahazi, KCB, Ziidi, e-top-up",
                        icon = AppIcons.Sms,
                        onClick = onOpenSources,
                    )
                    HorizontalDivider()
                    ListRow(title = "Import from file", subtitle = ".txt (blank-line separated) or Termux .json", icon = AppIcons.FileOpen, onClick = importFile)
                    HorizontalDivider()
                    ListRow(
                        title = "Unrecognised messages",
                        subtitle = "M-Pesa messages in a format the app doesn't know yet",
                        icon = Icons.Filled.Warning,
                        iconTint = if ((state.counts?.unparsed ?: 0) > 0) AresAmber else AresMuted,
                        onClick = onOpenUnparsed,
                        trailing = { CountBadge(state.counts?.unparsed ?: 0) },
                    )
                }
            }
            item { ImportStatus(state) }
            item {
                Group("Categories") {
                    ListRow(title = "Category rules", subtitle = "Keyword → category. Add, edit, recategorize", icon = Icons.Filled.Edit, onClick = onOpenRules)
                }
            }
            item {
                Group("Your data") {
                    ListRow(title = "Export CSV", subtitle = "All transactions, for a spreadsheet", icon = Icons.Filled.Share, onClick = data.exportCsv)
                    HorizontalDivider()
                    ListRow(title = "Export rules", subtitle = "Your category rules as JSON", icon = Icons.Filled.Share, onClick = data.exportRules)
                    HorizontalDivider()
                    ListRow(title = "Import rules", subtitle = "From a rules JSON file", icon = AppIcons.FileOpen, onClick = data.importRules)
                    HorizontalDivider()
                    ListRow(title = "Wipe all data", subtitle = "Delete the ledger from this app", icon = Icons.Filled.Delete, iconTint = AresRed, onClick = { wiping = true })
                }
            }
            item {
                Group("Security") {
                    ListRow(
                        title = "App lock",
                        subtitle = if (lockAvailable) "Ask for fingerprint, face or screen lock when opening the app"
                        else "Set a screen lock (PIN, pattern or fingerprint) on this phone first",
                        icon = Icons.Filled.Lock,
                        iconTint = if (lockAvailable) MaterialTheme.colorScheme.primary else AresMuted,
                        trailing = {
                            Switch(
                                checked = state.appLock,
                                enabled = lockAvailable && activity != null,
                                onCheckedChange = { on ->
                                    // Turning it on requires one successful unlock, so nobody locks themselves out.
                                    if (on) activity?.appLock?.prompt { vm.setAppLock(true); activity.applySecureFlag() }
                                    else { vm.setAppLock(false); activity?.applySecureFlag() }
                                },
                            )
                        },
                    )
                }
            }
            item {
                Group("Notifications") {
                    ListRow(
                        title = "New transactions",
                        subtitle = "A short notification for each M-Pesa SMS as it arrives. Budget alerts are always on.",
                        icon = Icons.Filled.Notifications,
                        onClick = { vm.setNotifyTransactions(!state.notifyTransactions) },
                        trailing = { Switch(checked = state.notifyTransactions, onCheckedChange = vm::setNotifyTransactions) },
                    )
                }
            }
            if (!state.isDemo) {
                item {
                    Group("Background updates") {
                        ListRow(
                            title = "Battery optimisation",
                            subtitle = "Infinix/Tecno (XOS) may stop the app in the background, so new SMS can arrive while it's closed. " +
                                "That's fine: the app catches up from your inbox every time you open it. To get live updates, " +
                                "set M-Pesa Ledger to \"No restrictions\" here and allow it in XOS's Phone Master → Auto-start.",
                            icon = AppIcons.Battery,
                            onClick = { openBatterySettings(context) },
                        )
                    }
                }
            }
            if (demoToggleAvailable) {
                item {
                    Group("Developer") {
                        ListRow(
                            title = "Demo mode",
                            subtitle = "Uses sample data in a separate database (${state.databaseName})",
                            onClick = { onToggleDemo(!state.isDemo) },
                            trailing = { Switch(checked = state.isDemo, onCheckedChange = onToggleDemo) },
                        )
                    }
                }
            }
            item {
                Group("About") {
                    ListRow(
                        title = "M-Pesa Ledger ${BuildConfig.VERSION_NAME}",
                        subtitle = "100% offline. The app has no internet permission; your messages and ledger never leave this phone.",
                        icon = Icons.Filled.Info,
                    )
                    HorizontalDivider()
                    ListRow(title = "Fonts", subtitle = "Inter and JetBrains Mono, SIL Open Font License 1.1")
                }
            }
        }
    }
    if (wiping) WipeDialog(isDemo = state.isDemo, onDismiss = { wiping = false }, onConfirm = { wiping = false; vm.wipe() })
}

@Composable
private fun ImportStatus(state: SettingsState) {
    Group("Status") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.progress?.let { p ->
                Text("Importing ${p.done} / ${p.total}", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
            }
            Stat("Transactions", state.counts?.transactions)
            Stat("Unrecognised", state.counts?.unparsed)
            state.lastImport?.let { r -> LastImport(r) }
        }
    }
}

@Composable
private fun LastImport(r: ImportResult) {
    Text(
        "Last import (${r.source}): ${r.inserted} new · ${r.duplicates} already there · ${r.unparsed} unrecognised · " +
            "${r.skipped} not transactions · ${r.durationMs} ms",
        style = MaterialTheme.typography.bodySmall, color = AresMuted,
    )
}

@Composable
private fun Stat(label: String, value: Int?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AresMuted, modifier = Modifier.weight(1f))
        Text(value?.toString() ?: "…", style = Num.medium)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = AresMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = AresSurface)) { content() }
    }
}

/**
 * The list screen needs no special permission (unlike ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
 * Some OEM builds remove it, so fall back to the app's own info page.
 */
private fun openBatterySettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: ActivityNotFoundException) { }
    }
}
