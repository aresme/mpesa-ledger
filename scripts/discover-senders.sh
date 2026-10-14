#!/usr/bin/env bash
# For the USER to run (not Claude): shows the *shapes* of messages from financial senders so new
# SMS formats can be supported. Everything is masked before printing: ALL-CAPS names -> XXXX,
# amounts -> 9s (format kept), account/phone/reference numbers -> #, leading codes replaced.
# Read the output, remove anything you still don't want to share, then paste it to Claude.
# Usage: scripts/discover-senders.sh [sender ...]      (defaults to the list below)
set -euo pipefail
source "$(dirname "$0")/_adb.sh" ""
# DISCOVER_MAX (default 4) shapes per sender; DISCOVER_GREP keeps only shapes matching a regex.
senders=("$@")
[[ ${#senders[@]} -gt 0 ]] || senders=(Globalpay INTERNET SAF_Balance "Okoa Jahazi" TOP-UP ETOPUP ME2U BUY_CREDIT KCB ZIIDI Safaricom SAFARICOM)

python3 -I - "$ADB_BIN" "$SERIAL" "${senders[@]}" <<'PY'
import re, subprocess, sys
adb, serial, senders = sys.argv[1], sys.argv[2], sys.argv[3:]
KEEP = {"M-PESA","MPESA","M-SHWARI","KCB","FULIZA","KSH","KES","AM","PM","OK","SMS","GB","MB","SAFARICOM","AIRTEL","OKOA",
        "JAHAZI","BONGA","DATA","ID","PIN","OTP","USSD","GLOBALPAY","VISA","USD","EUR","GBP","FX","ATM","POS","A/C","ACC","REF","TOPUP","ME2U"}
TOKEN = re.compile(r"(?P<amount>\d[\d,]*\.\d{1,2})|(?P<date>\d{1,2}/\d{1,2}/\d{2,4})|(?P<time>\d{1,2}:\d{2}(?::\d{2})?)"
                   r"|(?P<num>[\d*]{3,})|(?P<word>(?<![\w-])[A-Z][A-Z'&.-]+(?![\w-]))")
def mask(t):
    t = re.sub(r"^\s*[A-Z0-9]{10}(?=\s)", "QXX0000000", t)
    # M-Pesa transaction codes anywhere in the text (e.g. "M-PESA Ref UHHQ22FJL2").
    t = re.sub(r"(?<![A-Za-z0-9])(?=[A-Z0-9]{10}(?![A-Za-z0-9]))(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{10}", "QXX0000000", t)
    def sub(m):
        g, v = m.lastgroup, m.group()
        if g == "amount": return re.sub(r"\d", "9", v)
        if g in ("date", "time"): return v
        if g == "num": return re.sub(r"\d", "#", v)
        return v if v.rstrip(".").upper() in KEEP else "XXXX"
    return " ".join(TOKEN.sub(sub, t).split())
for s in senders:
    cmd = f"content query --uri content://sms/inbox --projection body --where \"address='{s}'\""
    out = subprocess.run([adb, "-s", serial, "shell", cmd], capture_output=True, text=True).stdout
    bodies = [b.strip() for b in re.split(r"(?m)^Row: \d+ body=", out) if b.strip()]
    shapes = {}
    for b in bodies:
        m = mask(b)
        shapes.setdefault(re.sub(r"[9#]+", "9", m)[:70], m)
    print(f"===== {s}: {len(bodies)} messages, {len(shapes)} different shapes")
    import os
    flt = os.environ.get("DISCOVER_GREP")
    picked = [m for m in shapes.values() if not flt or re.search(flt, m, re.I)]
    for m in picked[:int(os.environ.get("DISCOVER_MAX", "4"))]:
        print("  -", m[:300])
PY
