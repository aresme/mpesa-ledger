# M-Pesa Ledger

*Built by **AreqaTech** · version 1.0.0*

An offline Android app that turns your M-Pesa confirmation SMS into a monthly ledger: money in and out, fees,
Fuliza, savings, spending by category, top recipients, a 12-month trend and per-category budgets with alerts.

**Everything stays on the phone.** The app has no `INTERNET` permission, no Google Play Services, no analytics and
no accounts. It reads SMS from `MPESA` plus the financial senders you switch on under *Settings → Sources*
(GlobalPay, Safaricom, Okoa Jahazi, KCB, Ziidi, e-top-up), and it never sends, deletes or changes a message.

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

- **Inbox import**: reads `MPESA` / `M-PESA` messages. It re-scans incrementally every time you open the app, and
  re-imports are idempotent: a transaction is stored once per `(code, kind)`.
- **Live updates**: new M-Pesa SMS are added as they arrive.
- **Parser**: received, sent (including Pochi la Biashara), pay bill, buy goods, withdrawal, deposit, airtime,
  M-Shwari and KCB M-PESA savings in and out, Fuliza loan and Fuliza repayment. Reversals and M-Shwari loans are
  deliberately left unrecognised until a real example shows their exact wording.
  Messages with a transaction code but an unknown wording go to *Unrecognised messages*; the parser never guesses.
- **Dashboard**: In / Out / Fees (and % of out) / Net / Fuliza borrowed and repaid / Saved, spending by category,
  top 5 recipients, and income vs spending over 12 months. Tap a category to see its transactions.
- **Categories**: editable keyword rules (yours win over the built-ins, longer keywords win), per-transaction
  overrides that survive "Recategorize all", and "Always categorize *NAME* as …".
- **Budgets**: monthly limit per category, with one notification at 80% and one at 100% per month.
- **Other financial senders** (Settings → Sources, each switchable), built from real messages:
  - **M-PESA GlobalPay** card payments: counted as money out (no M-Pesa SMS exists for them), forex charge as the fee.
  - **Safaricom** data bundles (name and expiry) and airtime recharges: shown, but *not* counted again, because the
    money is already in the M-Pesa or airtime record (e.g. "SAFARICOM DATA BUNDLES" pay bill → *Data bundles* category).
  - **Okoa Jahazi**: airtime borrowed on credit and repaid, with an "owed / pay by" card.
  - **KCB** money received, **Ziidi** withdrawals, **e-top-up** airtime received: shown, kept out of M-Pesa totals.
  - Adverts and notices from these senders are ignored. Airtel and betting senders are not read.
- **Fuliza tracker**: how much you owe and when it's due (read from Safaricom's own loan SMS), your Fuliza limit,
  access fees this month and this year, and the full loan/repayment history.
- **Bills**: payments to the same place in at least 3 of the last 6 months (usually once or twice a month) become
  bills with their usual day and amount, marked paid / due soon / overdue, with a reminder 2 days before.
  "Not a bill" hides a false match.
- **Export**: CSV of all transactions and JSON of your rules, through the system file picker. Rules can be imported too.
- **Import from file**: `.txt` (one message per blank-line-separated block) or `termux-sms-list` JSON, for when SMS
  access isn't available.
- **App lock**: fingerprint, face or screen lock (androidx.biometric, works from Android 7). Screenshots and the
  recents thumbnail are blocked in release builds while it's on.
- **Wipe**: deletes the ledger, protected by a typed `DELETE` confirmation.

Money is stored as `Long` cents throughout, and times are Africa/Nairobi wall time.

## Requirements

- JDK 17 or newer (developed on Temurin 25), Android SDK with platform 37 and build-tools 36.0.0.
- `export ANDROID_HOME=$HOME/Android/Sdk; export PATH=$ANDROID_HOME/platform-tools:$PATH`
  (use the SDK's `adb`, not Ubuntu's older `/usr/bin/adb`; mixing the two keeps restarting the adb server).
- A phone running Android 7.0 (API 24) or newer. No Play Services needed.

Toolchain (all in `gradle/libs.versions.toml`): Gradle 9.8.1, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12,
Compose BOM 2026.09.00, Room 2.8.5, Navigation 2.10.2, androidx.biometric 1.1.0.

## Build and run

There are two packages, so a development build and the real app can be installed side by side:

| Build | Package | Label | Signed with |
|---|---|---|---|
| debug | `com.ares.mpesaledger.debug` | M-Pesa Ledger dev | Android debug key |
| release | `com.ares.mpesaledger` | M-Pesa Ledger | your keystore (see below) |

```bash
scripts/install.sh            # build debug, install, launch
scripts/logs.sh               # logcat for tag MpesaLedger + crashes
./gradlew :app:testDebugUnitTest   # JVM tests (parser, categorizer, budgets, export, formatting)
scripts/androidtest.sh        # Room tests on the phone (keeps the app installed)
```

The first launch explains what's read and asks for SMS access. On newer Android versions, an APK installed from a
file can be blocked by a **"Restricted setting"** dialog. The app detects this and shows the fix: *App info → ⋮ →
Allow restricted settings*, then *Permissions → SMS → Allow*.

## Device workflow

All scripts take an optional device serial (or `ANDROID_SERIAL`). With more than one phone connected, they refuse to guess.

| Script | What it does |
|---|---|
| `scripts/install.sh [serial]` | `assembleDebug`, `install -r`, launch |
| `scripts/logs.sh [serial]` | clear logcat, then follow `MpesaLedger` + `AndroidRuntime` |
| `scripts/shot.sh <name> [serial]` | screenshot + UI dump into `screenshots/` (git-ignored). **Refuses unless demo mode is on.** |
| `scripts/demo.sh on\|off\|reset\|empty` | switch to the demo database without touching the UI (`reset` reseeds it, `empty` previews first-run screens) |
| `scripts/sim-sms.sh "<sms>"` | simulate an incoming M-Pesa SMS through the same pipeline as a real one |
| `scripts/perf.sh [count]` | import N synthetic SMS into the demo database and time the import and queries |
| `scripts/unparsed.sh --counts \| --masked` | database counts, or unrecognised messages with names and digits masked |

Mirror the phone on your laptop with [`scrcpy`](https://github.com/Genymobile/scrcpy) while you work (optional).

### Wireless ADB

- **Android 11 and newer:** Developer options → *Wireless debugging* → *Pair device with pairing code*, then
  ```bash
  adb pair <phone-ip>:<pairing-port>     # enter the 6-digit code
  adb connect <phone-ip>:<debug-port>    # the port shown on the Wireless debugging screen
  ```
- **Android 7–10 (e.g. Infinix X559):** connect by USB once, then
  ```bash
  adb tcpip 5555
  adb connect <phone-ip>:5555            # now unplug
  ```
  On a rooted phone without USB: `su -c 'setprop service.adb.tcp.port 5555; stop adbd; start adbd'` in a terminal app.

Phone and laptop must be on the same Wi-Fi. `adb devices -l` shows `unauthorized` until you accept the RSA prompt on
the phone; `no permissions` means the udev rules or the `plugdev` group are missing.

## Demo mode (debug builds only)

Settings → Developer → *Demo mode* (or `scripts/demo.sh on`) restarts the app on a separate database,
`mpesa_demo.db`, filled from `sample_sms.txt`. A **DEMO** badge shows in every top bar. Your real ledger isn't
touched, and real SMS never go into the demo database. Take screenshots only in demo mode.

## Adding a new SMS format

Safaricom changes its wording from time to time. Messages with a transaction code that the parser doesn't know
show up under *Settings → Unrecognised messages*.

1. Copy one, or run `scripts/unparsed.sh --masked` to get them with names, numbers and codes masked.
2. In `app/src/main/java/com/ares/mpesaledger/parser/`:
   - add a `Kind` in `Model.kt` if it's a new kind of transaction (with its direction: `IN`, `OUT` or `NONE` for
     things that shouldn't count as spending, like Fuliza),
   - add a `KindPattern` in `MpesaParser.kt` **at the right priority** (first match wins). Its `anchor` must be
     a literal phrase from its own regex,
   - give it a fallback category in `domain/Categorizer.kt`.
3. Add a test in `app/src/test/.../parser/EdgeCaseTest.kt` using the masked message's shape.
4. Bump `MpesaParser.VERSION`. On the next start, the app re-scans the whole inbox once, so messages that were
   unrecognised before move into the ledger.

Example: the Fuliza *repayment* format ("Ksh X from your M-PESA has been used to fully pay your outstanding Fuliza
M-PESA… Your M-PESA balance is 99.99.") was added this way. It has no date and no `Ksh` before the balance, so it
uses the SMS received time.

## Release build and signing

Release builds use R8 (minify and resource shrinking). Signing settings are read **only** from
`~/.gradle/gradle.properties` or environment variables, never from this repo.

1. Create a keystore once (keep it and its passwords somewhere safe and backed up offline; if you lose it, you
   can't update the installed app without uninstalling it, which deletes its data). *On the development laptop this
   is already done: `~/keys/mpesa-ledger.jks`, with its password in `~/.gradle/gradle.properties`, both `chmod 600`.*
   ```bash
   mkdir -p ~/keys
   keytool -genkeypair -v -keystore ~/keys/mpesa-ledger.jks -alias mpesaledger \
           -keyalg RSA -keysize 4096 -validity 10000
   chmod 600 ~/keys/mpesa-ledger.jks
   ```
2. Add to `~/.gradle/gradle.properties` (then `chmod 600 ~/.gradle/gradle.properties`):
   ```properties
   MPESA_KEYSTORE=/home/<you>/keys/mpesa-ledger.jks
   MPESA_KEYSTORE_PASSWORD=<store password>
   MPESA_KEY_ALIAS=mpesaledger
   MPESA_KEY_PASSWORD=<key password>
   ```
   The same four names also work as environment variables.
3. Build, verify and install:
   ```bash
   ./gradlew :app:assembleRelease
   $ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/MpesaLedger-1.0.0-release.apk
   adb install -r app/build/outputs/apk/release/MpesaLedger-1.0.0-release.apk
   ```
   APKs are named `MpesaLedger-<version>-<buildType>.apk`. Without the signing properties the release is unsigned.

Release builds contain no debug code: no demo data, no simulated-SMS receiver and no logging. All logging goes
through `AppLog`, which does nothing in release.

## Privacy notes

- Permissions: `READ_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` (re-arms bill reminders),
  plus the biometric permissions for the app lock.
- Only M-Pesa and the senders enabled under *Sources* are read; every other SMS is ignored.
  No `INTERNET`.
- Backups are disabled (`allowBackup=false` plus data-extraction rules), so the ledger isn't copied to the cloud
  or to a new phone.
- Notifications show amounts only when the phone is unlocked; the lock screen just says that something happened.
- Debug builds log transaction codes and import counts under the tag `MpesaLedger`, never message text, names,
  balances or amounts.
- Exports go only to the file you choose in the system picker.

## Phone-specific notes

- **Infinix / Tecno (XOS)** stops apps in the background aggressively, so the live receiver can miss a message.
  Nothing is lost: the app catches up from the inbox every time you open it. For live updates, set the app to
  *No restrictions* under battery settings (Settings → Background updates has a shortcut) and allow it in
  *Phone Master → Auto-start*.
- XOS hides `Log.d` system-wide (`persist.log.tag=I`), so debug logging uses INFO level.

## Performance (Infinix X6886, Android 16, debug build)

| | |
|---|---|
| Import 5,000 SMS (parse 1.2 s, insert 1.6 s) | 3.0 s, in the background with a progress bar |
| Dashboard queries with 4,865 transactions | 8–45 ms each |
| Full Activity list (4,865 rows) | 107 ms |
| Cold start, signed release build | 0.34–0.37 s (about 1 s on the very first launch) |

## About, privacy and help in the app

*Settings → Help & about* has an About screen (version, build, credits, open-source licences, technical details),
the privacy policy and a Help & FAQ. M-Pesa Ledger is independent and not affiliated with Safaricom PLC; M-PESA,
Fuliza, M-Shwari, Okoa Jahazi and GlobalPay are trademarks of their owners.

## Licences

Inter and JetBrains Mono are bundled under the SIL Open Font License 1.1 (`app/src/main/assets/licenses/`).
`mpesa_ledger.py` is the original Python prototype the parser was ported from.

---

© 2026 AreqaTech. All rights reserved.
