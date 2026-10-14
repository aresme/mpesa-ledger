#!/usr/bin/env bash
# Screenshot (and UI dump) into screenshots/. Make sure the app shows DEMO data first.
# Usage: scripts/shot.sh <name> [serial]
set -euo pipefail
cd "$(dirname "$0")/.."
name="${1:?usage: scripts/shot.sh <name> [serial]}"
source scripts/_adb.sh "${2:-}"

mkdir -p screenshots
adb_ exec-out screencap -p > "screenshots/$name.png"
adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 \
    && adb_ pull /sdcard/ui.xml "screenshots/$name.xml" >/dev/null \
    && adb_ shell rm /sdcard/ui.xml
echo "screenshots/$name.png"
