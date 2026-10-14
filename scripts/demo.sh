#!/usr/bin/env bash
# Switch the debug app to/from demo mode without touching the UI (so real data is never
# shown while driving the phone), then launch it.
# Usage: scripts/demo.sh on|off|reset|empty [serial]
#   reset = fresh demo DB from sample_sms.txt, then on
#   empty = fresh, UNSEEDED demo DB + onboarding on launch (for first-run/empty-state screenshots)
set -euo pipefail
state="${1:?usage: scripts/demo.sh on|off|reset|empty [serial]}"
source "$(dirname "$0")/_adb.sh" "${2:-}"
case "$state" in on|reset|empty) value=true ;; off) value=false ;; *) echo "on|off|reset|empty" >&2; exit 1 ;; esac
empty=false; [[ "$state" == empty ]] && empty=true

adb_ shell am force-stop "$PKG"
if [[ "$state" == reset || "$state" == empty ]]; then
    # Only ever the demo database; the real ledger (mpesa.db) is never touched.
    adb_ shell "run-as $PKG sh -c 'rm -f databases/mpesa_demo.db databases/mpesa_demo.db-wal databases/mpesa_demo.db-shm'"
fi
adb_ shell "run-as $PKG sh -c 'mkdir -p shared_prefs && printf \"%s\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><boolean name=\\\"demo_mode\\\" value=\\\"$value\\\" /><boolean name=\\\"demo_empty\\\" value=\\\"$empty\\\" /></map>\" > shared_prefs/devtools.xml'"
adb_ shell am start -n "$PKG/.MainActivity" >/dev/null
echo "demo mode $state"
