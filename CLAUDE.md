# M-Pesa Ledger — working notes

Offline Android app (Kotlin, Compose M3, Room/KSP, manual DI) that parses M-Pesa SMS into a local ledger.
Package: `com.ares.mpesaledger`. minSdk 24, compile/target 37. The full spec was given in the first session; phases and checkpoints follow it.

## Hard rules
- **No `INTERNET` permission, no GMS/Firebase, no downloadable fonts.** Fonts are bundled in `app/src/main/res/font` (OFL licenses in `app/src/main/assets/licenses/`).
- **Money is `Long` cents.** Never `Double`. Format only in the UI.
- Times: Africa/Nairobi → epoch millis, `java.time` (desugared for API 24).
- Log only via `AppLog` (tag `MpesaLedger`), which is a no-op in release. Never log SMS bodies, balances or amounts.
- **Before any screenshot or UI dump, the app must be in DEMO mode** (DEMO badge visible). Real data never goes into a screenshot unless the user explicitly says so.
- The app is read-only towards the SMS provider: never send, delete or modify SMS.
- Debug test hooks live only in `app/src/debug`.
- Versions only in `gradle/libs.versions.toml`, verified against Maven metadata, never from memory.

## Commands
Device scripts take an optional serial (or set `ANDROID_SERIAL`); with more than one device connected they refuse to guess.
```bash
scripts/install.sh [serial]          # assembleDebug + install -r + launch
scripts/logs.sh [serial]             # logcat -c, then tag MpesaLedger + AndroidRuntime:E
scripts/shot.sh <name> [serial]      # screenshots/<name>.png + .xml (git-ignored)
scripts/sim-sms.sh "<body>" [serial] # Phase 4: debug SMS broadcast
./gradlew :app:testDebugUnitTest     # JVM unit tests: fixture (§5.6), edge cases, categorizer
# fixture lives in app/src/test/resources/sample_sms.txt (and app/src/debug/assets/ for demo mode)
# test/.../parser/FakeLedger.kt defines the dedupe / Fuliza-inheritance / month-total semantics the Room DAO must match
adb shell pm clear com.ares.mpesaledger
```
The scripts use the SDK adb (`~/Android/Sdk/platform-tools/adb`), not Ubuntu's older `/usr/bin/adb`.

## Layout (`app/src/main/java/com/ares/mpesaledger/`)
- `parser/` — pure Kotlin, **zero Android imports**. Port of `mpesa_ledger.py` (kept at repo root as the reference); spec §5.
  - `MpesaParser.kt`: all regexes + the ordered `patterns` list. `Model.kt`: `Kind`, `Direction`, `ParsedTransaction`, `ParseResult`. `SmsText.kt`: blank-line splitter.
  - New SMS format = new `Kind` (if needed) + pattern at the right priority + a test in `EdgeCaseTest` with a masked/synthetic message. Never guess unknown formats: they go to Unparsed.
- `data/` — Room entities, DAOs, migrations (schemas exported to `app/schemas/`), repository.
- `sms/` — inbox reader, live `SMS_RECEIVED` receiver.
- `domain/` — pure Kotlin too: `Categorizer` + `DefaultRules`, `MonthTotals`, later budget checker.
- `ui/` — theme (`ui/theme`), screens, components, ViewModels.
- `app/src/debug/` — debug-only receiver (`DEBUG_SMS`), demo mode (`mpesa_demo.db` from `sample_sms.txt`).
- `AppContainer` (manual DI) is owned by `MpesaLedgerApp`.

## Devices
- Daily: Infinix X6886, Android 16 / API 36, arm64-v8a, 1080×2400 @420dpi, XOS (aggressive background killing; "Restricted settings" likely for SMS permissions).
- Secondary: Infinix X559, Android 7.0 / API 24, armeabi-v7a, no GMS.
