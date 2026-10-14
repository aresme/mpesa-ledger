package com.ares.mpesaledger.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresGreenContainer
import com.ares.mpesaledger.ui.theme.AresMuted

/** First launch: what is read, what isn't, then the permission request (or file import). */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val container = appContainer()
    // Leave onboarding only after a file was picked: navigating first would dispose this
    // screen's picker launcher and the picker would never open.
    val importFile = rememberFileImport(onFilePicked = {
        container.prefs.onboardingDone = true
        onDone()
    })
    val requestSms = rememberSmsPermissionRequest { granted ->
        if (granted) {
            container.prefs.onboardingDone = true
            container.syncInboxInBackground()
            onDone()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(72.dp).background(AresGreenContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(AppIcons.PieChart, contentDescription = null, tint = AresGreen, modifier = Modifier.size(36.dp))
        }
        Text("Your M-Pesa, organised", style = MaterialTheme.typography.headlineMedium)
        Text(
            "M-Pesa Ledger turns your M-Pesa confirmation messages into a clear monthly picture: what came in, what went out, fees, Fuliza and where your money goes.",
            style = MaterialTheme.typography.bodyLarge, color = AresMuted,
        )
        Point(AppIcons.Sms, "Only money messages", "It reads M-Pesa and a few financial senders (GlobalPay, Safaricom bundles, Okoa, KCB, Ziidi). You can switch each one off. Everything else is ignored.")
        Point(Icons.Filled.Lock, "Stays on this phone", "No internet permission, no accounts, no analytics. Nothing is uploaded.")
        Point(Icons.Filled.Check, "Read-only", "It never sends, deletes or changes your messages.")
        Spacer(Modifier.weight(1f, fill = false))
        Button(onClick = requestSms, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow SMS access") }
        TextButton(
            onClick = importFile,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import from a file instead") }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = AresGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = AresMuted)
        }
    }
}
