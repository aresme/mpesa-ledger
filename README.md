# M-Pesa Ledger

[![Android CI](https://github.com/aresme/mpesa-ledger/actions/workflows/android.yml/badge.svg)](https://github.com/aresme/mpesa-ledger/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

*Built by **AreqaTech** · version 1.0.0*

Turn your M-Pesa SMS into a personal finance tracker. This offline Android app reads your M-Pesa confirmation messages and organizes them into a monthly ledger with spending categories, budget tracking, and financial insights.

**Completely offline.** No internet permission, no Google Play Services, no analytics, no accounts. Your financial data never leaves your phone. The app only reads SMS from M-PESA and other financial services you enable (GlobalPay, Safaricom, KCB, and others), and never sends, deletes, or modifies any messages.

<p>
<img src="docs/screenshots/dashboard.png" width="200" alt="Dashboard">
<img src="docs/screenshots/dashboard-trend.png" width="200" alt="Top recipients and 12-month trend">
<img src="docs/screenshots/activity.png" width="200" alt="Activity list">
<img src="docs/screenshots/detail.png" width="200" alt="Transaction detail">
</p>
<p>
<img src="docs/screenshots/budgets.png" width="200" alt="Budgets">
<img src="docs/screenshots/rules.png" width="200" alt="Category rules">
<img src="docs/screenshots/onboarding.png" width="200" alt="Onboarding">
<img src="docs/screenshots/unrecognised.png" width="200" alt="Unrecognised messages">
</p>

*All screenshots use the built-in demo data (fictional names), never a real inbox.*

## Features

### Transaction Management
- **Automatic inbox scanning** – Re-scans your SMS inbox incrementally when you open the app. Each transaction is stored once based on its code and type.
- **Live updates** – New M-Pesa messages are processed immediately as they arrive.
- **Smart parser** – Recognizes received, sent, PayBill, BuyGoods, withdrawals, deposits, airtime, M-Shwari and KCB M-PESA savings transactions, Fuliza loans and repayments. Unknown message formats go to *Unrecognized messages* rather than being guessed.
- **Multiple sources** – Besides M-PESA, you can enable GlobalPay, Safaricom, Okoa Jahazi, KCB, Ziidi, and e-top-up in Settings. Each source is tracked appropriately to avoid double-counting.

### Financial Insights
- **Dashboard** – Monthly totals for income, expenses, fees, net savings, and Fuliza activity. See spending breakdown by category, your top recipients, and a 12-month trend.
- **Categories** – Automatic categorization using keyword rules. You can edit rules, override individual transactions, and quickly categorize by sender name.
- **Budgets** – Set monthly spending limits per category. Get notified at 80% and 100% of your budget.
- **Bill detection** – Recurring payments (3+ times in the last 6 months) are automatically identified as bills, with reminders 2 days before they're due.
- **Fuliza tracking** – See what you owe, when it's due, your limit, access fees, and full history.

### Privacy & Security
- **App lock** – Protect the app with fingerprint, face, or device PIN/pattern. Screenshots are blocked when locked.
- **No backups** – Your data won't sync to the cloud or transfer to a new phone.
- **Export & import** – Export transactions to CSV and rules to JSON. Import transactions from `.txt` or `termux-sms-list` JSON files.

Technical: Money is stored as integer cents throughout. Times use Africa/Nairobi timezone.

## Building from Source

### Requirements
- JDK 17+ (developed with Temurin 25)
- Android SDK with API 37 and build-tools 36.0.0
- Android 7.0+ device (API 24+, no Play Services required)

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

### Tech Stack
- Kotlin 2.4.21, Jetpack Compose, Material 3
- Room 2.8.5, Navigation 2.10.2
- Gradle 9.8.1, AGP 9.4.1
- See `gradle/libs.versions.toml` for full dependency list

### Build Variants

Debug and release builds use different package names, so you can install both:

| Build | Package | Signed with |
|---|---|---|
| debug | `com.ares.mpesaledger.debug` | Android debug key |
| release | `com.ares.mpesaledger` | Your release keystore |

### Quick Start

```bash
scripts/install.sh            # Build debug, install, and launch
scripts/logs.sh               # View filtered logcat output
./gradlew :app:testDebugUnitTest   # Run unit tests
scripts/androidtest.sh        # Run instrumented tests on device
```

On first launch, the app explains what it reads and requests SMS permission. If you sideload the APK on newer Android versions and see a "Restricted setting" dialog, go to *App info → ⋮ → Allow restricted settings*, then grant SMS permission.

## Development Scripts

Scripts accept an optional device serial (or set `ANDROID_SERIAL`). If multiple devices are connected, they'll prompt for which one to use.

| Script | Purpose |
|---|---|
| `scripts/install.sh` | Build debug APK, install, and launch |
| `scripts/logs.sh` | Stream filtered logcat (MpesaLedger tag + crashes) |
| `scripts/shot.sh <name>` | Capture screenshot (requires demo mode) |
| `scripts/demo.sh on\|off\|reset\|empty` | Toggle demo mode |
| `scripts/sim-sms.sh "<text>"` | Simulate incoming SMS (debug builds) |
| `scripts/perf.sh [count]` | Performance test with synthetic data |
| `scripts/unparsed.sh --counts\|--masked` | Check unrecognized messages |

### Wireless ADB Setup

**Android 11+:** Developer options → Wireless debugging → Pair device with pairing code
```bash
adb pair <phone-ip>:<pairing-port>
adb connect <phone-ip>:<debug-port>
```

**Android 7-10:** Connect via USB first
```bash
adb tcpip 5555
adb connect <phone-ip>:5555
```

## Demo Mode

Debug builds include a demo mode (Settings → Developer → Demo mode, or `scripts/demo.sh on`) that uses a separate database pre-filled with sample data from `sample_sms.txt`. A **DEMO** badge appears in the UI, and your real transaction data isn't affected. This mode is useful for taking screenshots or testing without exposing personal information.

## Contributing New SMS Formats

Safaricom occasionally changes message wording. If you find a transaction that doesn't parse correctly:

1. Check *Settings → Unrecognized messages* or run `scripts/unparsed.sh --masked` to see masked examples
2. Add the new pattern in `app/src/main/java/com/ares/mpesaledger/parser/`:
   - Define a new `Kind` in `Model.kt` if needed (specify `IN`, `OUT`, or `NONE` for direction)
   - Add a `KindPattern` in `MpesaParser.kt` at the correct priority (first match wins)
   - Add a default category in `domain/Categorizer.kt`
3. Write a test case in `app/src/test/.../parser/EdgeCaseTest.kt`
4. Bump `MpesaParser.VERSION` so the app re-scans on next launch

When adding patterns, the parser intentionally avoids guessing – unrecognized formats are flagged rather than misclassified.

## Release Builds

Release builds use R8 for code minification and resource shrinking. Signing credentials are read from `~/.gradle/gradle.properties` or environment variables (never committed to the repo).

### Setup Signing

1. Generate a keystore (back it up securely – you'll need it to update the app):
   ```bash
   mkdir -p ~/keys
   keytool -genkeypair -v -keystore ~/keys/mpesa-ledger.jks -alias mpesaledger \
           -keyalg RSA -keysize 4096 -validity 10000
   chmod 600 ~/keys/mpesa-ledger.jks
   ```

2. Add to `~/.gradle/gradle.properties`:
   ```properties
   MPESA_KEYSTORE=/home/<you>/keys/mpesa-ledger.jks
   MPESA_KEYSTORE_PASSWORD=<store password>
   MPESA_KEY_ALIAS=mpesaledger
   MPESA_KEY_PASSWORD=<key password>
   ```

3. Build:
   ```bash
   ./gradlew :app:assembleRelease
   ```

Release builds strip all debug code, including demo mode and detailed logging. APKs are named `MpesaLedger-<version>-<buildType>.apk`.

## Privacy

- **Permissions**: `READ_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, plus biometric permissions for app lock. No internet permission.
- **SMS access**: Only M-PESA and enabled financial sources are read. Other messages are ignored.
- **No cloud backups**: `allowBackup=false` prevents data from syncing to cloud or transferring to new devices.
- **Secure notifications**: Amounts only show when your phone is unlocked.
- **No tracking**: Debug builds log transaction codes and counts only, never message text, names, or amounts.
- **Export control**: Files go only to locations you explicitly choose.

## Device-Specific Notes

**Infinix / Tecno (XOS)**: Background apps are aggressively killed, which may cause the live SMS receiver to miss messages. The app compensates by re-scanning the inbox on each launch, so no transactions are lost. For real-time updates, disable battery restrictions in settings and enable Auto-start in Phone Master.

## Performance

Tested on Infinix X6886, Android 16, debug build:

- Import 5,000 SMS: 3.0s (1.2s parsing, 1.6s database insert)
- Dashboard queries (4,865 transactions): 8–45ms per query
- Full transaction list (4,865 rows): 107ms
- Cold start (release): 0.34–0.37s (~1s on first launch)

## In-App Documentation

The app includes an About screen, Privacy Policy, and Help & FAQ under *Settings → Help & about*.

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

When reporting issues with SMS parsing, always provide masked examples (replace real names, numbers, and codes).

## Security

Found a security vulnerability? Please see [SECURITY.md](SECURITY.md) for responsible disclosure guidelines.

## License

Licensed under the MIT License. See [LICENSE](LICENSE) for details.

Bundled fonts (Inter and JetBrains Mono) are used under the SIL Open Font License 1.1. See `app/src/main/assets/licenses/`.

M-Pesa Ledger is independent and not affiliated with Safaricom PLC. M-PESA, Fuliza, M-Shwari, Okoa Jahazi, and GlobalPay are trademarks of their respective owners.

---

Built by **AreqaTech** · [Report Bug](https://github.com/aresme/mpesa-ledger/issues/new?template=bug_report.md) · [Request Feature](https://github.com/aresme/mpesa-ledger/issues/new?template=feature_request.md)
