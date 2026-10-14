package com.ares.mpesaledger.ui.onboarding

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ares.mpesaledger.AppLog
import com.ares.mpesaledger.sms.SmsFileReader
import com.ares.mpesaledger.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import from a .txt (blank-line separated) or Termux .json file via the Storage Access
 * Framework, so it works without SMS permission. Runs in the app scope, not the screen's.
 */
@Composable
fun rememberFileImport(onFilePicked: () -> Unit = {}): () -> Unit {
    val container = appContainer()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onFilePicked()
        container.appScope.launch {
            val message = runCatching {
                val messages = SmsFileReader.read(context.contentResolver, uri)
                val r = container.importer.import(messages, source = "file")
                "Imported ${r.inserted} new · ${r.duplicates} already there · ${r.unparsed} unrecognised"
            }.getOrElse {
                AppLog.w({ "file import failed" }, it)
                "Couldn't import that file: ${it.message}"
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        }
    }
    return { launcher.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }
}
