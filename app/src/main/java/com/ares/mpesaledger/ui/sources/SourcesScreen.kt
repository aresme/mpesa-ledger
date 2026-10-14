package com.ares.mpesaledger.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.parser.Source
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.ListRow
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresSurface

/** Which SMS senders the app reads. M-Pesa is always on; everything else is the user's choice. */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val container = appContainer()
    var enabled by remember { mutableStateOf(container.prefs.enabledSources) }

    fun set(source: Source, on: Boolean) {
        enabled = if (on) enabled + source else enabled - source
        container.prefs.enabledSources = enabled
        // A newly enabled sender's older messages are before the incremental high-water mark.
        if (on) container.syncInboxInBackground(full = true)
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Sources", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(
                    "The app reads only the senders switched on here, and nothing leaves this phone. Money paid through " +
                        "M-Pesa is counted once: Safaricom bundle and airtime messages only add detail, and bank or Okoa " +
                        "messages are kept separate from your M-Pesa totals.",
                    style = MaterialTheme.typography.bodyMedium, color = AresMuted,
                )
            }
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = AresSurface)) {
                    Source.entries.forEachIndexed { i, source ->
                        if (i > 0) HorizontalDivider()
                        val always = source == Source.MPESA
                        ListRow(
                            title = source.label,
                            subtitle = source.description + if (always) " · always on" else "",
                            onClick = if (always) null else ({ set(source, source !in enabled) }),
                            trailing = {
                                Switch(
                                    checked = always || source in enabled,
                                    enabled = !always,
                                    onCheckedChange = { set(source, it) },
                                )
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    "Turning a sender off stops reading new messages from it; what was already imported stays until you wipe.",
                    style = MaterialTheme.typography.bodySmall, color = AresMuted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
