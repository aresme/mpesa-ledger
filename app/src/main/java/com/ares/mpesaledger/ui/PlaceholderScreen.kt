package com.ares.mpesaledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.BuildConfig
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresOutline
import com.ares.mpesaledger.ui.theme.MonoStyles

/** Phase 0 scaffold screen: proves theme, fonts and edge-to-edge insets on the device. */
@Composable
fun PlaceholderScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ares_sys://mpesa-ledger", style = MonoStyles.data, color = AresGreen)
        Text("M-Pesa Ledger", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Scaffold build. Parsing, import and the dashboard arrive in later phases.",
            style = MaterialTheme.typography.bodyMedium,
            color = AresMuted,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, AresOutline)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatRow("IN", "0.00", AresGreen)
            StatRow("OUT", "0.00", MaterialTheme.colorScheme.onBackground)
            StatRow("NET", "0.00", MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "> v${BuildConfig.VERSION_NAME} · ${if (BuildConfig.DEBUG) "debug" else "release"} · offline_",
            style = MonoStyles.data,
            color = AresGreen,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AresMuted)
        Text("Ksh $value", style = MonoStyles.amount, color = valueColor)
    }
}
