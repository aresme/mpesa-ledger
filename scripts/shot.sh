#!/usr/bin/env bash
# Screenshot (and UI dump) into screenshots/.
# Refuses unless the debug app is in DEMO mode, so real transactions never land in a
# screenshot. Override only when the user explicitly allows it: ALLOW_REAL=1 scripts/shot.sh ...
# Usage: scripts/shot.sh <name> [serial]
set -euo pipefail
cd "$(dirname "$0")/.."
name="${1:?usage: scripts/shot.sh <name> [serial]}"
source scripts/_adb.sh "${2:-}"

if [[ "${ALLOW_REAL:-}" != "1" ]]; then
    prefs=$(adb_ shell run-as "$PKG" cat shared_prefs/devtools.xml 2>/dev/null || true)
    if ! grep -q 'name="demo_mode" value="true"' <<<"$prefs"; then
        echo "Refusing: app is not in DEMO mode (real data could be on screen)." >&2
        exit 3
    fi
    # Demo mode isn't enough: a system file picker or another app on top would show the
    # user's own files. Only shoot when this app's activity is the one in front.
    top=$(adb_ shell dumpsys activity activities | grep -m1 topResumedActivity || true)
    if [[ "$top" != *"$PKG/"* ]]; then
        echo "Refusing: $PKG is not in the foreground." >&2
        exit 4
    fi
fi

mkdir -p screenshots
adb_ exec-out screencap -p > "screenshots/$name.png"
adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 \
    && adb_ pull /sdcard/ui.xml "screenshots/$name.xml" >/dev/null \
    && adb_ shell rm /sdcard/ui.xml
echo "screenshots/$name.png"
