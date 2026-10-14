package com.ares.mpesaledger.ui.rules

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ares.mpesaledger.data.CategoryRuleEntity
import com.ares.mpesaledger.ui.appContainer
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.ListRow
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** keyword -> category rules. User rules are editable; built-in defaults are read-only. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val context = LocalContext.current
    val rules by repo.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val choices by repo.observeCategoryChoices().collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<CategoryRuleEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun run(done: String, block: suspend () -> Int) {
        container.appScope.launch {
            val changed = block()
            val count = if (changed == 1) "1 transaction" else "$changed transactions"
            withContext(Dispatchers.Main) { Toast.makeText(context, done.format(count), Toast.LENGTH_SHORT).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Category rules", onBack = onBack, actions = {
            IconButton(onClick = { run("Recategorized: %s changed") { repo.recategorizeAll() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Recategorize all")
            }
        })
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(
                    "A rule matches when its keyword appears in the name or account. Your rules win over the built-in ones, " +
                        "and longer keywords win over shorter ones. Categories you set by hand on a transaction are never changed.",
                    style = MaterialTheme.typography.bodyMedium, color = AresMuted,
                )
            }
            item {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null); Text("  Add rule")
                }
            }
            val user = rules.filter { it.isUser }
            item { Group("Your rules (${user.size})") {
                if (user.isEmpty()) Text("No rules yet. Add one here, or use \"Always categorize\" on a transaction.",
                    style = MaterialTheme.typography.bodyMedium, color = AresMuted, modifier = Modifier.padding(16.dp))
                user.forEachIndexed { i, rule ->
                    if (i > 0) HorizontalDivider()
                    ListRow(title = rule.keyword, subtitle = "→ ${rule.category}", onClick = { editing = rule }, trailing = {
                        IconButton(onClick = { run("Rule deleted · %s changed") { repo.deleteUserRule(rule.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = AresMuted)
                        }
                    })
                }
            } }
            val defaults = rules.filter { !it.isUser }
            item { Group("Built-in (${defaults.size})") {
                defaults.forEachIndexed { i, rule ->
                    if (i > 0) HorizontalDivider()
                    ListRow(title = rule.keyword, subtitle = "→ ${rule.category}")
                }
            } }
        }
    }

    if (adding || editing != null) {
        RuleDialog(
            initial = editing,
            choices = choices,
            onDismiss = { adding = false; editing = null },
            onSave = { keyword, category ->
                val id = editing?.id
                adding = false; editing = null
                run("Saved · %s changed") { repo.saveUserRule(id, keyword, category) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(
    initial: CategoryRuleEntity?,
    choices: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var keyword by remember { mutableStateOf(initial?.keyword.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(keyword, { keyword = it }, label = { Text("Keyword (e.g. NAIVAS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Wrapping chips: every choice is visible without sideways scrolling.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { c -> SuggestionChip(onClick = { category = c }, label = { Text(c) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(keyword, category) }, enabled = keyword.isNotBlank() && category.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = AresMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = AresSurface)) { content() }
    }
}

