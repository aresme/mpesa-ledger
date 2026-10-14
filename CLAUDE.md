# M-Pesa Ledger — working notes

Offline Android app (Kotlin, Compose M3, Room/KSP, manual DI) that parses M-Pesa SMS into a local ledger.
Packages: release `com.ares.mpesaledger`; debug `com.ares.mpesaledger.debug` ("M-Pesa Ledger dev", so both install side by side
with different keys). Source namespace is always `com.ares.mpesaledger`, so components are `<pkg>/com.ares.mpesaledger.MainActivity`.
minSdk 24, compile/target 37. The full spec was given in the first session; phases and checkpoints follow it. README.md is user-facing.

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
scripts/shot.sh <name> [serial]      # screenshots/<name>.png + .xml (git-ignored); REFUSES unless demo mode is on
scripts/androidtest.sh [serial]      # on-device Room tests; keeps the app installed (AGP would uninstall it)
scripts/demo.sh on|off|reset|empty   # flip demo mode without touching the UI (reset = reseed; empty = unseeded + onboarding)
scripts/unparsed.sh --counts         # table counts only (safe for Claude to run)
scripts/unparsed.sh --masked         # unparsed SMS with names/digits/codes masked (safe for Claude to read)
scripts/unparsed.sh                  # prints raw unparsed SMS: for the USER to run and mask, not Claude
scripts/sim-sms.sh "<body>" [serial] # simulated incoming SMS -> same LiveSmsPipeline as a real one (debug builds)
./gradlew :app:testDebugUnitTest     # JVM unit tests: fixture (§5.6), edge cases, categorizer
# fixture lives in app/src/test/resources/sample_sms.txt (and app/src/debug/assets/ for demo mode)
# test/.../parser/FakeLedger.kt defines the dedupe / Fuliza-inheritance / month-total semantics the Room DAO must match
scripts/perf.sh [count]              # synthetic N-SMS import + query timings (demo DB only)
./gradlew :app:assembleRelease       # R8, signed with ~/keys/mpesa-ledger.jks via MPESA_* in ~/.gradle/gradle.properties (never print the password)
```
`pm clear` / uninstall wipes the ledger: check `scripts/unparsed.sh --counts` and ask first.
The scripts use the SDK adb (`~/Android/Sdk/platform-tools/adb`), not Ubuntu's older `/usr/bin/adb`.

## Visual direction (decided at checkpoint 2)
The user rejected the terminal look: build a **modern, polished finance app that keeps the brand**.
Cards with rounded corners, icons, real charts, clear hierarchy; Inter for all text. Black background,
#4DFF7A green as the accent (not for body text), very dark grey surfaces, amber/red only for warnings.
JetBrains Mono only for amounts/codes where tabular alignment helps. No `>` prompts, `ares_sys://` headers or ASCII bars.

## Demo mode / real data
- Toggle: the "Demo mode"/"Exit demo" button (debug builds); it persists `shared_prefs/devtools.xml` and restarts the process.
- Real data (`mpesa.db`) must never be screenshotted, UI-dumped, or have its SMS text read by Claude. Counts and timings only.
- Inbox sync is skipped entirely in demo mode, so real SMS never enter `mpesa_demo.db`.
- XOS drops Log.d system-wide (`persist.log.tag=I`), so AppLog logs at INFO (debug builds only).

## Layout (`app/src/main/java/com/ares/mpesaledger/`)
- `parser/` — pure Kotlin, **zero Android imports**. Port of `mpesa_ledger.py` (kept at repo root as the reference); spec §5.
  - `MpesaParser.kt`: all regexes + the ordered `patterns` list. `Model.kt`: `Kind`, `Direction`, `ParsedTransaction`, `ParseResult`. `SmsText.kt`: blank-line splitter.
  - New SMS format = new `Kind` (if needed) + pattern at the right priority + a test in `EdgeCaseTest` with a masked/synthetic message. Never guess unknown formats: they go to Unparsed.
- `data/` — Room entities, DAOs, migrations (schemas exported to `app/schemas/`), repository.
- `sms/` — inbox reader + `InboxSync` (source of truth, runs on every resume), `SmsReceiver` (live, BROADCAST_SMS-guarded),
  `LiveSmsPipeline` (shared by the real and debug receivers; ignores real SMS in demo mode).
- `notify/` — notification channels ("Budgets", "Transactions" opt-in); content hidden on the lock screen.
- Debug receiver `debug/DebugSmsReceiver` is guarded by `android.permission.DUMP` (adb shell has it; apps don't).
- Kill the app for a cold-start test with `adb shell run-as com.ares.mpesaledger kill -9 <pid>`; `am force-stop` puts it in
  the stopped state where no broadcast is delivered (that's also what XOS does, hence the inbox catch-up).
- `domain/` — `Categorizer` + `DefaultRules`, `MonthTotals`, `BudgetRules` (pure, unit-tested) and `BudgetChecker`
  (Android: reads Room, posts notifications). SQL must stay compatible with API 24's SQLite 3.9 (no UPSERT, no window functions).
- `ui/` — `theme/` (tokens, Inter + tabular `Num` styles, `Mono` for codes/raw SMS), `components/` (cards, top bar with
  DEMO badge, charts, icons), `navigation/` (string routes, bottom bar), one package per screen with its ViewModel
  (`dashboard`, `transactions`, `detail`, `budgets`, `settings`, `unparsed`, `onboarding`, `rules`, `lock`). `appViewModel { container, saved -> }` = manual DI.
  Chart colours `ChartIncome`/`ChartSpending` were checked with the dataviz palette validator; don't swap them by eye.
- `app/src/debug/` — debug-only receiver (`DEBUG_SMS`), demo mode (`mpesa_demo.db` from `sample_sms.txt`).
- `AppContainer` (manual DI) is owned by `MpesaLedgerApp`.

## App lock
- androidx.biometric 1.1.0 (only stable), BIOMETRIC_WEAK | DEVICE_CREDENTIAL (works API 24+). MainActivity is a FragmentActivity for it.
- FLAG_SECURE only in release builds when the lock is on (debug must stay screencap-able).
- Test without a finger: add `<boolean name="app_lock" value="true" />` to `shared_prefs/app.xml` via run-as; screenshots of the
  system prompt come out black (Android blanks secure auth UI) — that's expected.

## Device-driving gotchas
- XOS sometimes ignores fast `input swipe`; use `input touchscreen swipe x1 y1 x2 y2 800`.
- Small-screen check without the X559: `wm size 480x854; wm density 240`, ALWAYS `wm size reset; wm density reset` after (use a trap).
- uiautomator text match: button labels may carry leading spaces ("  Add rule"); match on contains, not equals.
- Never drive the SAF file picker with dumps/screenshots: it lists the user's own files.
- READ_SMS and RECEIVE_SMS share a permission group: revoking one while the other is granted means the next request is auto-granted.

## Installed on the X6886
- `com.ares.mpesaledger` = signed release = the user's real app. Never uninstall/`pm clear` it.
- `com.ares.mpesaledger.debug` = dev build, normally left in DEMO mode (it then ignores real SMS, so no duplicate alerts).
- Never drive the system file picker with screenshots; `shot.sh` refuses unless the app itself is in front.

## Devices
- Daily: Infinix X6886, Android 16 / API 36, arm64-v8a, 1080×2400 @420dpi, XOS (aggressive background killing; "Restricted settings" likely for SMS permissions).
- The X6886 is the ONLY test device (decided 2026-10-14). minSdk stays 24, so keep API-24-safe code (SQLite 3.9, desugared java.time), but there is no Android 7 phone to verify on; use the `wm size`/`wm density` simulation for small screens.
