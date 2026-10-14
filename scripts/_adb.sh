# Sourced by the other scripts. Resolves the adb binary and target device.
# Usage inside a script:  source "$(dirname "$0")/_adb.sh" "$1"   (optional serial)
# Serial precedence: explicit argument > $ANDROID_SERIAL > the single connected device.

# Scripts drive the DEBUG build (applicationIdSuffix ".debug"). Component class names keep the
# source namespace, so they are spelled out in full rather than relative to the package.
PKG="com.ares.mpesaledger.debug"
NS="com.ares.mpesaledger"
MAIN="$PKG/$NS.MainActivity"
TAG="MpesaLedger"

# Prefer the SDK's adb: Ubuntu's /usr/bin/adb is older, and mixing the two makes
# the adb server restart every time a different client connects.
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [[ -x "$SDK_DIR/platform-tools/adb" ]]; then
    ADB_BIN="$SDK_DIR/platform-tools/adb"
else
    ADB_BIN="$(command -v adb)" || { echo "adb not found" >&2; exit 1; }
fi

SERIAL="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$SERIAL" ]]; then
    mapfile -t devices < <("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1}')
    case ${#devices[@]} in
        0) echo "No authorized device. Check 'adb devices -l' (unauthorized = accept the RSA prompt)." >&2; exit 1 ;;
        1) SERIAL="${devices[0]}" ;;
        *) echo "Multiple devices connected; pass a serial:" >&2; printf '  %s\n' "${devices[@]}" >&2; exit 1 ;;
    esac
fi

adb_() { "$ADB_BIN" -s "$SERIAL" "$@"; }
