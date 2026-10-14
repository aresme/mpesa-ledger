#!/usr/bin/env bash
# Print the Unparsed messages from the phone's database, for YOU to read and mask before
# sharing. Copies the DB (with its WAL) to a private temp dir and deletes it afterwards.
# Usage: scripts/unparsed.sh [--counts] [--demo] [serial]
#   --counts  only print table counts (no message text)
#   --demo    read mpesa_demo.db instead of mpesa.db
set -euo pipefail
counts_only=0; db=mpesa.db; serial=""
for arg in "$@"; do
    case "$arg" in
        --counts) counts_only=1 ;;
        --demo) db=mpesa_demo.db ;;
        *) serial="$arg" ;;
    esac
done
source "$(dirname "$0")/_adb.sh" "$serial"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT; chmod 700 "$tmp"
for suffix in "" -wal -shm; do
    adb_ exec-out run-as "$PKG" cat "databases/$db$suffix" > "$tmp/$db$suffix" 2>/dev/null || rm -f "$tmp/$db$suffix"
done

python3 -I - "$tmp/$db" "$counts_only" <<'PY'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
q = lambda s: db.execute(s).fetchone()[0]
print(f"transactions={q('select count(*) from transactions')} "
      f"unparsed={q('select count(*) from unparsed')} "
      f"undated={q('select count(*) from transactions where timestamp is null')}")
for kind, n in db.execute("select kind, count(*) from transactions group by kind order by 2 desc"):
    print(f"  {kind:<9} {n}")
if sys.argv[2] == "0":
    for (raw,) in db.execute("select raw from unparsed order by firstSeen"):
        print("-" * 60); print(raw)
    print("-" * 60)
PY
