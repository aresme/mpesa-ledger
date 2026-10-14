#!/usr/bin/env bash
# Simulate an incoming M-Pesa SMS through the debug-only receiver (same pipeline as a real
# SMS_RECEIVED). Debug builds only. Real SMS are never sent or written.
# Usage: [SENDER="Okoa Jahazi"] scripts/sim-sms.sh "<sms body>" [serial]   (SENDER defaults to MPESA)
set -euo pipefail
body="${1:?usage: scripts/sim-sms.sh \"<sms body>\" [serial]}"
source "$(dirname "$0")/_adb.sh" "${2:-}"

# `adb shell` re-parses its arguments with the device's sh, so wrap the body in single quotes
# and escape any single quotes inside it ('  ->  '\'').
quoted="'${body//\'/\'\\\'\'}'"
sender="${SENDER:-MPESA}"
qsender="'${sender//\'/\'\\\'\'}'"
adb_ shell am broadcast \
    -a com.ares.mpesaledger.DEBUG_SMS \
    -n "$PKG/$NS.debug.DebugSmsReceiver" \
    --es body "$quoted" \
    --es sender "$qsender" | tail -1
