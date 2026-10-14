package com.ares.mpesaledger.ui.importstatus

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.ui.format.formatCents
import com.ares.mpesaledger.ui.theme.AresBlack
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresOutline
import com.ares.mpesaledger.ui.theme.AresRed
import com.ares.mpesaledger.ui.theme.MonoStyles

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportStatusScreen(
    viewModel: ImportStatusViewModel,
    demoToggleAvailable: Boolean,
    onToggleDemo: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermission()
        if (it) viewModel.syncInbox(full = false)
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("ares_sys://ledger/import", style = MonoStyles.data, color = AresGreen, modifier = Modifier.weight(1f))
            if (state.isDemo) DemoBadge()
        }
        Text("Import status", style = MaterialTheme.typography.headlineSmall)
        Text("db: ${state.databaseName} · sms permission: ${if (state.hasSmsPermission) "granted" else "not granted"}",
            style = MonoStyles.data, color = AresMuted)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.hasSmsPermission && !state.isDemo) {
                OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }) { Text("Grant SMS") }
            }
            OutlinedButton(onClick = { viewModel.syncInbox(full = false) }, enabled = !state.isDemo) { Text("Scan inbox") }
            OutlinedButton(onClick = { viewModel.syncInbox(full = true) }, enabled = !state.isDemo) { Text("Re-import all") }
            OutlinedButton(onClick = { fileLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }) {
                Text("Import file")
            }
            if (demoToggleAvailable) {
                OutlinedButton(onClick = { onToggleDemo(!state.isDemo) }) { Text(if (state.isDemo) "Exit demo" else "Demo mode") }
            }
        }

        state.progress?.let { p ->
            Text("importing ${p.done} / ${p.total}", style = MonoStyles.data, color = AresGreen)
            LinearProgressIndicator(
                progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                modifier = Modifier.fillMaxWidth(),
                color = AresGreen,
                trackColor = AresOutline,
            )
        }
        state.error?.let { Text(it, color = AresRed, style = MaterialTheme.typography.bodyMedium) }

        Section("last import") {
            val r = state.lastResult
            if (r == null) {
                Text("none yet", style = MonoStyles.data, color = AresMuted)
            } else {
                KeyValue("source", r.source)
                KeyValue("scanned", r.scanned.toString())
                KeyValue("imported", r.inserted.toString())
                KeyValue("duplicates", r.duplicates.toString())
                KeyValue("unparsed", r.unparsed.toString())
                KeyValue("skipped", r.skipped.toString())
                KeyValue("duration", "${r.durationMs} ms")
            }
        }

        Section("database") {
            val c = state.counts
            KeyValue("transactions", c?.transactions?.toString() ?: "…")
            KeyValue("unparsed", c?.unparsed?.toString() ?: "…")
            KeyValue("undated", c?.undated?.toString() ?: "…")
            KeyValue("rules", c?.rules?.toString() ?: "…")
        }

        Section("months (in / out / fees / net)") {
            if (state.months.isEmpty()) Text("no dated transactions", style = MonoStyles.data, color = AresMuted)
            state.months.forEach { m ->
                Row(Modifier.fillMaxWidth()) {
                    Text(m.month, style = MonoStyles.data, color = AresMuted, modifier = Modifier.width(64.dp))
                    listOf(m.inCents, m.outCents, m.feeCents, m.netCents).forEach {
                        Text(formatCents(it), style = MonoStyles.data, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DemoBadge() {
    Text(
        "DEMO",
        style = MonoStyles.data,
        color = AresBlack,
        modifier = Modifier
            .background(AresGreen)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, AresOutline)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = AresMuted)
        content()
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = AresMuted)
        Text(value, style = MonoStyles.data)
    }
}
