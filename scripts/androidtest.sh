#!/usr/bin/env bash
# Run instrumented tests on the device WITHOUT uninstalling the app afterwards
# (AGP uninstalls by default, which would wipe the imported ledger).
# Usage: scripts/androidtest.sh [serial]
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/_adb.sh "${1:-}"
ANDROID_SERIAL="$SERIAL" ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
