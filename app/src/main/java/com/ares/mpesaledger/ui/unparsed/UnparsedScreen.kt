package com.ares.mpesaledger.ui.unparsed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppIcons
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.EmptyState
import com.ares.mpesaledger.ui.detail.copyToClipboard
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresSurfaceHigh
import com.ares.mpesaledger.ui.theme.Mono

/** Messages with a transaction code in a format the parser doesn't know yet. */
@Composable
fun UnparsedScreen(onBack: () -> Unit) {
    val container = appContainer()
    val rows by container.repository.observeUnparsed().collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Unrecognised messages", onBack = onBack)
        val list = rows ?: return@Column
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Check,
                title = "Everything recognised",
                message = "Every M-Pesa message with a transaction code was understood. New formats from Safaricom will show up here.",
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "These look like M-Pesa transactions but use a wording the app doesn't know yet, so they're not in your totals. " +
                        "Copy one (mask names and numbers) to add support for it.",
                    style = MaterialTheme.typography.bodyMedium, color = AresMuted,
                )
            }
            items(list, key = { it.rawHash }) { row ->
                AppCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            row.raw, style = Mono.raw,
                            modifier = Modifier.weight(1f).background(AresSurfaceHigh, MaterialTheme.shapes.small).padding(12.dp),
                        )
                        IconButton(onClick = { copyToClipboard(context, "M-Pesa SMS", row.raw) }) {
                            Icon(AppIcons.ContentCopy, contentDescription = "Copy message", tint = AresGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

