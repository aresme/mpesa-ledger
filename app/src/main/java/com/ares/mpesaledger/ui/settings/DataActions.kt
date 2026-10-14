package com.ares.mpesaledger.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.theme.AresRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** SAF-based export/import actions. Files go only where the user picks; nothing is shared or uploaded. */
class DataActions(val exportCsv: () -> Unit, val exportRules: () -> Unit, val importRules: () -> Unit)

@Composable
fun rememberDataActions(): DataActions {
    val container = appContainer()
    val context = LocalContext.current
    val repo = container.repository

    fun runIo(label: String, block: suspend () -> String) {
        container.appScope.launch {
            val msg = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse {
                AppLog.w({ "$label failed" }, it)
                "$label failed: ${it.message}"
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        }
    }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runIo("Export") {
            val n = context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { repo.exportCsv(it) }
            "Exported $n transactions"
        }
    }
    val rulesOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runIo("Rules export") {
            val json = repo.exportRulesJson()
            context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(json) }
            "Rules exported"
        }
    }
    val rulesIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runIo("Rules import") {
            val json = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            "Imported ${repo.importRulesJson(json)} rules"
        }
    }
    return remember {
        DataActions(
            exportCsv = { csv.launch("mpesa-ledger-${LocalDate.now()}.csv") },
            exportRules = { rulesOut.launch("mpesa-ledger-rules.json") },
            importRules = { rulesIn.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )
    }
}

/** Typed confirmation: the button only enables once the user types DELETE. */
@Composable
fun WipeDialog(isDemo: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wipe all data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isDemo) "This clears the demo database. Your real ledger is not touched."
                    else "This deletes your ledger, rules, budgets and unrecognised messages from this app. " +
                        "Your SMS are not touched: while SMS access is allowed, the ledger is rebuilt from the inbox the next time you open the app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Type DELETE to confirm.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "DELETE") { Text("Wipe", color = AresRed) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

