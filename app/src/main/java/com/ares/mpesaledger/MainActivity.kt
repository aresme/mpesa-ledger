package com.ares.mpesaledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ares.mpesaledger.ui.PlaceholderScreen
import com.ares.mpesaledger.ui.theme.MpesaLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only app: force light (white) system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        AppLog.d { "MainActivity created" }
        setContent {
            MpesaLedgerTheme {
                PlaceholderScreen()
            }
        }
    }
}
