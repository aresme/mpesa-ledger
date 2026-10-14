#!/usr/bin/env bash
# Simulate an incoming M-Pesa SMS through the debug-only receiver (same pipeline as a real
# SMS_RECEIVED). Debug builds only. Real SMS are never sent or written.
# Usage: scripts/sim-sms.sh "<sms body>" [serial]
set -euo pipefail
body="${1:?usage: scripts/sim-sms.sh \"<sms body>\" [serial]}"
source "$(dirname "$0")/_adb.sh" "${2:-}"

# `adb shell` re-parses its arguments with the device's sh, so wrap the body in single quotes
# and escape any single quotes inside it ('  ->  '\'').
quoted="'${body//\'/\'\\\'\'}'"
adb_ shell am broadcast \
    -a com.ares.mpesaledger.DEBUG_SMS \
    -n "$PKG/.debug.DebugSmsReceiver" \
    --es body "$quoted" | tail -1
