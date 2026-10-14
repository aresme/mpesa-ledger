package com.ares.mpesaledger.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.ares.mpesaledger.ui.theme.AresMuted

/** SMS read + live receive, plus notifications on API 33+ (budget alerts). */
fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/**
 * Returns a lambda that asks for SMS access. If Android refuses without showing a dialog
 * (permanently denied, or Android 13+'s "Restricted setting" for sideloaded APKs), it shows
 * step-by-step instructions and a shortcut to App info instead of failing silently.
 */
@Composable
fun rememberSmsPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.READ_SMS] == true
        if (!granted) {
            val activity = context.findActivity()
            // No rationale after a denial = the system didn't (or won't) show the dialog.
            val blocked = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
            if (blocked) showHelp = true
        }
        onResult(granted)
    }
    if (showHelp) RestrictedSettingsDialog(onDismiss = { showHelp = false }, onOpenAppInfo = {
        showHelp = false
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    })
    return { launcher.launch(requiredPermissions()) }
}

@Composable
private fun RestrictedSettingsDialog(onDismiss: () -> Unit, onOpenAppInfo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow SMS access in Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Android didn't show the permission prompt. For apps installed from a file, newer Android versions block SMS access until you allow it:")
                Text("1. Open App info (button below)\n2. Tap ⋮ (top right) → Allow restricted settings\n3. Confirm with your PIN or fingerprint\n4. Permissions → SMS → Allow",
                    style = MaterialTheme.typography.bodyMedium)
                Text("If there's no ⋮ menu, just open Permissions → SMS → Allow.", color = AresMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onOpenAppInfo) { Text("Open App info") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
