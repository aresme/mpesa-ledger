package com.ares.mpesaledger.ui.lock

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.ui.theme.AresBlack
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresGreenContainer
import com.ares.mpesaledger.ui.theme.AresMuted

/**
 * App lock with androidx.biometric (no Play Services). BIOMETRIC_WEAK | DEVICE_CREDENTIAL
 * works from API 24: on 24-27 the library falls back to the system PIN/pattern screen
 * (KeyguardManager); on 28+ it's the BiometricPrompt sheet with a "Use PIN" option.
 */
class AppLock(private val activity: FragmentActivity, private val isEnabled: () -> Boolean) {

    /** Compose observes this; true hides the app behind the lock screen. */
    val locked = mutableStateOf(isEnabled())
    private var stoppedAt = 0L
    private var prompting = false

    fun onStop() { stoppedAt = SystemClock.elapsedRealtime() }

    /**
     * Re-lock after the app has been in the background for a little while. The grace period
     * covers our own excursions (file picker, permission dialog, the PIN screen on API < 28)
     * so they don't immediately ask again.
     */
    fun onStart() {
        if (isEnabled() && !prompting && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > GRACE_MS) {
            locked.value = true
        }
    }

    fun prompt(onSuccess: () -> Unit = {}) {
        if (prompting) return
        prompting = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                locked.value = false
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock M-Pesa Ledger")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }

    companion object {
        private const val GRACE_MS = 30_000L
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /** False when the phone has no screen lock at all: nothing to authenticate with. */
        fun isAvailable(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AresBlack), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(72.dp).background(AresGreenContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = AresGreen, modifier = Modifier.size(34.dp))
            }
            Text("M-Pesa Ledger is locked", style = MaterialTheme.typography.titleLarge)
            Text("Unlock with your fingerprint, face or screen lock.", style = MaterialTheme.typography.bodyMedium, color = AresMuted, textAlign = TextAlign.Center)
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Unlock") }
        }
    }
}
