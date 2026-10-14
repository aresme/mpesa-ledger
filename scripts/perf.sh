#!/usr/bin/env bash
# Performance test on the device: fresh demo DB + N synthetic SMS, timed import and queries,
# then a cold-start measurement of the dashboard. Debug builds, demo mode only.
# Usage: scripts/perf.sh [count=5000] [serial]
set -euo pipefail
cd "$(dirname "$0")/.."
count="${1:-5000}"
source scripts/_adb.sh "${2:-}"

scripts/demo.sh reset "$SERIAL" >/dev/null
sleep 3
adb_ logcat -c
adb_ shell am broadcast -a com.ares.mpesaledger.DEBUG_PERF -n "$PKG/.debug.PerfReceiver" --ei count "$count" >/dev/null
for _ in $(seq 1 120); do
    adb_ logcat -d -s "$TAG:V" | grep -q 'perf: dashboard' && break
    sleep 1
done
adb_ logcat -d -s "$TAG:V" | grep -E 'perf' | sed 's/^.*perf: /  /'
echo "Cold start with $count+ rows (am start -W):"
adb_ shell am force-stop "$PKG"
adb_ shell am start -W -n "$PKG/.MainActivity" | grep -E 'TotalTime|WaitTime' | sed 's/^/  /'
