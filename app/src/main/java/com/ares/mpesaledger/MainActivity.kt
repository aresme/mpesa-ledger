package com.ares.mpesaledger

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.ares.mpesaledger.devtools.DevTools
import com.ares.mpesaledger.ui.lock.AppLock
import com.ares.mpesaledger.ui.lock.LockScreen
import com.ares.mpesaledger.ui.navigation.AppNavigation
import com.ares.mpesaledger.ui.theme.MpesaLedgerTheme

/** FragmentActivity (not ComponentActivity) because androidx.biometric's prompt is fragment-based. */
class MainActivity : FragmentActivity() {

    private val container get() = (application as MpesaLedgerApp).container
    lateinit var appLock: AppLock
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded launch screen; must run before super.onCreate (switches to the app theme after).
        installSplashScreen()
        // Dark-only app: force light (white) system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        AppLog.d { "MainActivity created (demo=${container.isDemo})" }
        appLock = AppLock(this) { container.prefs.appLock }
        applySecureFlag()
        // Onboarding only on a real first run without SMS access; demo data needs no setup
        // (except the debug "empty demo", which exists to preview first-run screens).
        val showOnboarding = if (container.isDemo) DevTools.isDemoEmpty(this)
        else !container.prefs.onboardingDone && !container.inboxSync.hasPermission()
        setContent {
            MpesaLedgerTheme(isDemo = container.isDemo) {
                val locked by appLock.locked
                Box {
                    AppNavigation(
                        showOnboarding = showOnboarding,
                        demoToggleAvailable = DevTools.AVAILABLE,
                        onToggleDemo = { DevTools.setDemoModeAndRestart(this@MainActivity, it) },
                    )
                    if (locked) {
                        LockScreen(onUnlock = { appLock.prompt() })
                        LaunchedEffect(Unit) { appLock.prompt() }
                    }
                }
            }
        }
    }

    /**
     * With the app lock on, FLAG_SECURE keeps balances out of the recents thumbnail and
     * screenshots. Release builds only: in debug it would block `adb screencap`.
     */
    fun applySecureFlag() {
        if (!BuildConfig.DEBUG && container.prefs.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        appLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        appLock.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Incremental inbox scan on every start/resume; catches anything the receiver missed.
        container.syncInboxInBackground()
    }
}
