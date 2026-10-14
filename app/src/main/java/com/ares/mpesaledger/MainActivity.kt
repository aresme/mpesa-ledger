package com.ares.mpesaledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ares.mpesaledger.devtools.DevTools
import com.ares.mpesaledger.ui.importstatus.ImportStatusScreen
import com.ares.mpesaledger.ui.importstatus.ImportStatusViewModel
import com.ares.mpesaledger.ui.theme.MpesaLedgerTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as MpesaLedgerApp).container
    private val importStatus: ImportStatusViewModel by viewModels { ImportStatusViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only app: force light (white) system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        AppLog.d { "MainActivity created (demo=${container.isDemo})" }
        setContent {
            MpesaLedgerTheme {
                ImportStatusScreen(
                    viewModel = importStatus,
                    demoToggleAvailable = DevTools.AVAILABLE,
                    onToggleDemo = { DevTools.setDemoModeAndRestart(this, it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Incremental inbox scan on every start/resume; catches anything the receiver missed.
        importStatus.refreshPermission()
        container.syncInboxInBackground()
    }
}
