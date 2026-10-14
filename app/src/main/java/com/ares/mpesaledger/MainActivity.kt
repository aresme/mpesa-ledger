package com.ares.mpesaledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ares.mpesaledger.devtools.DevTools
import com.ares.mpesaledger.ui.navigation.AppNavigation
import com.ares.mpesaledger.ui.theme.MpesaLedgerTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as MpesaLedgerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only app: force light (white) system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        AppLog.d { "MainActivity created (demo=${container.isDemo})" }
        // Onboarding only on a real first run without SMS access; demo data needs no setup
        // (except the debug "empty demo", which exists to preview first-run screens).
        val showOnboarding = if (container.isDemo) DevTools.isDemoEmpty(this)
        else !container.prefs.onboardingDone && !container.inboxSync.hasPermission()
        setContent {
            MpesaLedgerTheme(isDemo = container.isDemo) {
                AppNavigation(
                    showOnboarding = showOnboarding,
                    demoToggleAvailable = DevTools.AVAILABLE,
                    onToggleDemo = { DevTools.setDemoModeAndRestart(this, it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Incremental inbox scan on every start/resume; catches anything the receiver missed.
        container.syncInboxInBackground()
    }
}
