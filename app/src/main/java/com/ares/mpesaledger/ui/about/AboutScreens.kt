package com.ares.mpesaledger.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ares.mpesaledger.Brand
import com.ares.mpesaledger.BuildConfig
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.ui.components.AppCard
import com.ares.mpesaledger.ui.components.AppTopBar
import com.ares.mpesaledger.ui.components.ListRow
import com.ares.mpesaledger.ui.components.SectionTitle
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.Mono

private val WHATS_NEW = listOf(
    "Dashboard: money in and out, fees, net, savings, spending by category and a 12-month trend",
    "Reads M-Pesa plus GlobalPay, Safaricom bundles and airtime, Okoa Jahazi, KCB, Ziidi and e-top-up (your choice)",
    "Fuliza and Okoa Jahazi trackers: what you owe and when it's due",
    "Bills: repeat payments found automatically, with reminders",
    "Budgets with alerts at 80% and 100%, category rules, CSV export, app lock",
)

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit, onOpenHelp: () -> Unit) {
    var showLicences by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "About", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(88)
                    Spacer(Modifier.height(16.dp))
                    Text(Brand.APP_NAME, style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" + if (BuildConfig.DEBUG) " · development build" else "",
                        style = MaterialTheme.typography.bodyMedium, color = AresMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(Brand.CREDIT, style = MaterialTheme.typography.titleSmall, color = AresGreen)
                }
            }
            item {
                AppCard {
                    Text("100% offline", style = MaterialTheme.typography.titleSmall)
                    Text("No internet permission, no accounts, no ads, no tracking. Your messages and ledger never leave this phone.",
                        style = MaterialTheme.typography.bodyMedium, color = AresMuted)
                }
            }
            item {
                AppCard {
                    Text("What's in ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    WHATS_NEW.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = AresMuted, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
            item {
                AppCard {
                    ListRow(title = "Help & FAQ", onClick = onOpenHelp)
                    HorizontalDivider()
                    ListRow(title = "Privacy policy", onClick = onOpenPrivacy)
                    HorizontalDivider()
                    ListRow(title = "Open-source licences", subtitle = if (showLicences) null else "Fonts and libraries", onClick = { showLicences = !showLicences })
                    if (showLicences) Licences()
                }
            }
            item {
                AppCard {
                    Row { Text("Technical details", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)) }
                    Spacer(Modifier.height(6.dp))
                    Detail("Parser", "v${MpesaParser.VERSION}")
                    Detail("Database", "schema 3")
                    Detail("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    Detail("Package", LocalContext.current.packageName)
                }
            }
            item {
                Text(Brand.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = AresMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            item {
                Text(Brand.COPYRIGHT, style = MaterialTheme.typography.bodySmall, color = AresMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AresMuted, modifier = Modifier.weight(1f))
        Text(value, style = Mono.code)
    }
}

@Composable
private fun Licences() {
    val items = listOf(
        "Inter" to "SIL Open Font License 1.1 · The Inter Project Authors",
        "JetBrains Mono" to "SIL Open Font License 1.1 · The JetBrains Mono Project Authors",
        "AndroidX, Jetpack Compose, Room, Navigation, Biometric" to "Apache License 2.0 · The Android Open Source Project",
        "Kotlin, kotlinx.coroutines" to "Apache License 2.0 · JetBrains s.r.o.",
        "Material icons" to "Apache License 2.0 · Google",
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (name, licence) ->
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(licence, style = MaterialTheme.typography.bodySmall, color = AresMuted)
            }
        }
    }
}

/** A titled list of paragraphs (Privacy, Help). */
@Composable
private fun TextPage(title: String, onBack: () -> Unit, intro: String, sections: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = title, onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(intro, style = MaterialTheme.typography.bodyMedium, color = AresMuted) }
            sections.forEach { (heading, body) ->
                item {
                    AppCard {
                        Text(heading, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = AresMuted)
                    }
                }
            }
            item { Text(Brand.CREDIT, style = MaterialTheme.typography.bodySmall, color = AresMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) = TextPage(
    title = "Privacy policy",
    onBack = onBack,
    intro = "Last updated 15 October 2026. ${Brand.APP_NAME} by ${Brand.BUILT_BY} is built so that your financial data stays yours.",
    sections = listOf(
        "Everything stays on your phone" to "The app has no internet permission, so it cannot send anything anywhere. There are no accounts, ads, analytics or crash reporters.",
        "What the app reads" to "Only SMS from M-PESA and the financial senders you switch on under Settings → Sources (GlobalPay, Safaricom, Okoa Jahazi, KCB, Ziidi, e-top-up). All other messages are ignored. It never sends, deletes or changes a message.",
        "What is stored" to "The parsed transactions, the original text of those messages, your category rules, budgets and settings — in the app's private storage on this phone.",
        "Backups" to "Android cloud backup and device-to-device transfer are turned off for this app, so your ledger is not copied to Google or to a new phone.",
        "Notifications" to "Budget, bill and transaction alerts are created on the phone. On the lock screen they don't show amounts.",
        "Exports" to "CSV and rules files are written only to a place you choose with the system file picker.",
        "Deleting your data" to "Settings → Wipe all data deletes the ledger from the app. Uninstalling the app removes everything it stored. Your SMS are never touched.",
        "Permissions" to "SMS (read and receive), notifications, start after reboot (bill reminders) and biometrics (app lock, optional).",
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit) = TextPage(
    title = "Help & FAQ",
    onBack = onBack,
    intro = "How ${Brand.APP_NAME} works, and answers to common questions.",
    sections = listOf(
        "Where do the numbers come from?" to "From the confirmation SMS Safaricom sends for every M-Pesa transaction. The app reads your inbox each time you open it and adds new messages live when they arrive.",
        "Why isn't a transaction showing?" to "Open Settings → Unrecognised messages. If it's there, its wording is new to the app. If you deleted the SMS, it can't be imported. You can also import a saved SMS file from Settings.",
        "Is anything counted twice?" to "No. Data bundles and airtime are counted once, from the M-Pesa payment; Safaricom's confirmation only adds the bundle name. Bank, Okoa and savings messages are kept out of your M-Pesa totals.",
        "Why is Fuliza or Okoa different from *234#?" to "The app works it out from your messages. Safaricom adds daily Fuliza charges without sending an SMS, so its figure can be slightly higher.",
        "How are bills found?" to "A payment to the same place in at least 3 of the last 6 months (usually once or twice a month) becomes a bill. Tap \"Not a bill\" to hide a wrong one.",
        "\"Restricted setting\" when allowing SMS" to "On newer Android versions, open App info → ⋮ → Allow restricted settings, confirm, then Permissions → SMS → Allow.",
        "Live updates stop on Infinix / Tecno" to "XOS stops apps in the background. Set the app to \"No restrictions\" for battery and allow it in Phone Master → Auto-start. Nothing is lost either way: the app catches up when you open it.",
        "Categories" to "Open a transaction to change its category, or choose \"Always categorize … as\" to make a rule for every past and future payment to that place. Manage rules in Settings → Category rules.",
    ),
)
