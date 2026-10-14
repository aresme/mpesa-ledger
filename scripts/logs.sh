#!/usr/bin/env bash
# Clear logcat and stream only the app's tag plus crashes.
# Usage: scripts/logs.sh [serial] [--no-clear]
set -euo pipefail
source "$(dirname "$0")/_adb.sh" "${1:-}"

[[ "${2:-}" == "--no-clear" ]] || adb_ logcat -c
adb_ logcat -v time -s "$TAG:V" AndroidRuntime:E
