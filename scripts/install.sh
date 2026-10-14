#!/usr/bin/env bash
# Build the debug APK, install it on the device and launch it.
# Usage: scripts/install.sh [serial]
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/_adb.sh "${1:-}"

./gradlew :app:assembleDebug
adb_ install -r -d app/build/outputs/apk/debug/app-debug.apk
adb_ shell am start -n "$PKG/.MainActivity"
echo "Installed and launched on $SERIAL"
