#!/usr/bin/env bash
# Print the Unparsed messages from the phone's database, for YOU to read and mask before
# sharing. Copies the DB (with its WAL) to a private temp dir and deletes it afterwards.
# Usage: scripts/unparsed.sh [--counts|--masked] [--demo] [serial]
#   --counts  only print table counts (no message text)
#   --masked  print messages with names, codes and digits masked (wording kept)
#   --demo    read mpesa_demo.db instead of mpesa.db
set -euo pipefail
mode=full; db=mpesa.db; serial=""
for arg in "$@"; do
    case "$arg" in
        --counts) mode=counts ;;
        --masked) mode=masked ;;
        --demo) db=mpesa_demo.db ;;
        *) serial="$arg" ;;
    esac
done
source "$(dirname "$0")/_adb.sh" "$serial"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT; chmod 700 "$tmp"
for suffix in "" -wal -shm; do
    adb_ exec-out run-as "$PKG" cat "databases/$db$suffix" > "$tmp/$db$suffix" 2>/dev/null || rm -f "$tmp/$db$suffix"
done

python3 -I - "$tmp/$db" "$mode" <<'PY'
import re, sqlite3, sys

# Safaricom product words kept as-is; every other ALL-CAPS word is treated as a name.
KEEP = {"M-PESA", "MPESA", "M-SHWARI", "MSHWARI", "KCB", "FULIZA", "KSH", "AM", "PM", "OK",
        "MMF", "PIN", "ATM", "SMS", "ID", "ZIIDI", "POCHI", "LA", "BIASHARA", "BONGA", "GLOBAL",
        "PAY", "TILL", "AIRTIME", "M-PESA.", "MPESA."}

TOKEN = re.compile(
    r"(?P<amount>\d[\d,]*\.\d{2})"            # amounts: digits -> 9, shape kept
    r"|(?P<date>\d{1,2}/\d{1,2}/\d{2,4})"       # dates kept
    r"|(?P<time>\d{1,2}:\d{2})"                  # times kept
    r"|(?P<num>[\d*]{3,})"                        # accounts, phones, tills -> #
    r"|(?P<word>(?<![\w-])[A-Z][A-Z'&.-]+(?![\w-]))")  # ALL-CAPS words -> XXXX unless product terms

def mask(raw, i):
    body = re.sub(r"^\s*[A-Z0-9]{10}(?=\s)", "", raw, flags=re.I)
    def sub(m):
        g = m.lastgroup; v = m.group()
        if g == "amount": return re.sub(r"\d", "9", v)
        if g in ("date", "time"): return v
        if g == "num": return re.sub(r"\d", "#", v)
        return v if v.rstrip(".") in KEEP else "XXXX"
    return f"QXX{i:07d}" + TOKEN.sub(sub, body)
db = sqlite3.connect(sys.argv[1])
q = lambda s: db.execute(s).fetchone()[0]
print(f"transactions={q('select count(*) from transactions')} "
      f"unparsed={q('select count(*) from unparsed')} "
      f"undated={q('select count(*) from transactions where timestamp is null')}")
for kind, n in db.execute("select kind, count(*) from transactions group by kind order by 2 desc"):
    print(f"  {kind:<9} {n}")
if sys.argv[2] != "counts":
    for i, (raw,) in enumerate(db.execute("select raw from unparsed order by firstSeen"), 1):
        print("-" * 60); print(mask(raw, i) if sys.argv[2] == "masked" else raw)
    print("-" * 60)
PY
