#!/usr/bin/env python3
"""
mpesa_ledger.py - turn M-Pesa confirmation SMS into a local SQLite ledger.

  python mpesa_ledger.py import --termux          # read SMS directly (Termux:API)
  python mpesa_ledger.py import sms.json          # saved `termux-sms-list` output
  python mpesa_ledger.py import sample_sms.txt    # plain text, one message per blank-line block
  python mpesa_ledger.py report                   # this month
  python mpesa_ledger.py report --month 2026-09   # a specific month
  python mpesa_ledger.py report --all             # everything
  python mpesa_ledger.py trend                    # monthly burn chart
  python mpesa_ledger.py recategorize             # re-apply categories.json to all rows
  python mpesa_ledger.py unparsed                 # messages the parser didn't understand
  python mpesa_ledger.py export ledger.csv

Everything stays on your device. No network calls.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sqlite3
import subprocess
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

DEFAULT_DB = Path.home() / ".mpesa_ledger.db"
RULES_FILE = Path(__file__).with_name("categories.json")

# ---------------------------------------------------------------- parsing

AMT = r"([\d,]+\.\d{2})"
KSH = r"Ksh\s?" + AMT

RE_CODE = re.compile(r"^\s*([A-Z0-9]{10})\s+confirmed", re.I)
RE_WHEN = re.compile(r"on (\d{1,2}/\d{1,2}/\d{2,4}) at (\d{1,2}:\d{2}\s?[AP]M)", re.I)
RE_BALANCE = re.compile(r"New M-PESA balance is " + KSH, re.I)
RE_COST = re.compile(r"(?:Transaction cost,?|Access Fee charged)\s*" + KSH, re.I)
RE_PHONE_TAIL = re.compile(r"\s+((?:\+?254|0)[\d*]{6,12})$")

# (kind, direction, pattern). Order matters: paybill must come before plain "sent to".
# direction: "in" = money into your wallet, "out" = money leaving it, "none" = not counted.
PATTERNS = [
    ("fuliza",   "none", re.compile(r"Fuliza M-Pesa amount is " + KSH, re.I)),
    ("received", "in",   re.compile(r"You have received " + KSH + r" from (.+?) on \d", re.I)),
    ("paybill",  "out",  re.compile(KSH + r" sent to (.+?) for account (.+?) on \d", re.I)),
    ("sent",     "out",  re.compile(KSH + r" sent to (.+?) on \d", re.I)),
    ("till",     "out",  re.compile(KSH + r" paid to (.+?)\.?\s*on \d", re.I)),
    ("withdraw", "out",  re.compile(r"Withdraw " + KSH + r" from (.+?)\s*New M-PESA", re.I)),
    ("deposit",  "in",   re.compile(r"Give " + KSH + r" cash to (.+?)\s*New M-PESA", re.I)),
    ("airtime",  "out",  re.compile(r"You bought " + KSH + r" of airtime", re.I)),
    ("savings",  "none", re.compile(r"transferred " + KSH + r" to your M-Shwari", re.I)),
]


def to_float(s: str | None) -> float | None:
    return float(s.replace(",", "")) if s else None


def money(match: re.Match | None) -> float | None:
    return to_float(match.group(1)) if match else None


def parse_when(text: str) -> str | None:
    m = RE_WHEN.search(text)
    if not m:
        return None
    stamp = f"{m.group(1)} {m.group(2).upper().replace(' ', '')}"
    for fmt in ("%d/%m/%y %I:%M%p", "%d/%m/%Y %I:%M%p"):
        try:
            return datetime.strptime(stamp, fmt).isoformat(timespec="minutes")
        except ValueError:
            pass
    return None


def parse_sms(body: str) -> dict | None:
    """Return a transaction dict, a dict with kind='unknown', or None if not an M-Pesa txn."""
    text = " ".join(body.split())  # collapse newlines and double spaces
    m = RE_CODE.match(text)
    if not m:
        return None  # promos, Fuliza reminders, etc.
    rec = {
        "code": m.group(1).upper(), "kind": "unknown", "direction": "none",
        "amount": None, "counterparty": "", "phone": "", "account": "",
        "cost": money(RE_COST.search(text)), "balance": money(RE_BALANCE.search(text)),
        "ts": parse_when(text), "raw": body.strip(), "category": None,
    }
    for kind, direction, pat in PATTERNS:
        p = pat.search(text)
        if not p:
            continue
        rec.update(kind=kind, direction=direction, amount=to_float(p.group(1)))
        if kind in ("airtime", "fuliza", "savings"):
            rec["counterparty"] = {"savings": "M-Shwari"}.get(kind, kind.capitalize())
        else:
            name = p.group(2).strip().rstrip(".")
            phone = RE_PHONE_TAIL.search(name)
            if phone:
                rec["phone"] = phone.group(1)
                name = name[: phone.start()].strip()
            rec["counterparty"] = name
            if kind == "paybill":
                rec["account"] = p.group(3).strip()
        break
    return rec


# ---------------------------------------------------------------- categories

DEFAULT_RULES = {
    "KPLC": "Electricity",
    "NAIVAS": "Groceries", "QUICKMART": "Groceries", "CARREFOUR": "Groceries",
    "CHANDARANA": "Groceries",
    "SAFARICOM HOME": "Internet", "ZUKU": "Internet", "FAIBA": "Internet",
    "KABARAK": "School",
    "TOTALENERGIES": "Fuel", "RUBIS": "Fuel", "SHELL": "Fuel",
    "JAVA HOUSE": "Food out", "KFC": "Food out",
    "UBER": "Transport", "BOLT": "Transport",
}
KIND_FALLBACK = {
    "received": "Income", "deposit": "Cash in", "withdraw": "Cash out",
    "airtime": "Airtime", "sent": "People", "fuliza": "Fuliza", "savings": "Savings",
}


def load_rules() -> list[tuple[str, str]]:
    """User rules (categories.json) win over the built-in defaults."""
    user = {}
    if RULES_FILE.exists():
        user = {k.upper(): v for k, v in json.loads(RULES_FILE.read_text()).items()
                if not k.startswith("_")}
    merged = {**{k.upper(): v for k, v in DEFAULT_RULES.items()}, **user}
    # user keys first, then longest keyword first so "SAFARICOM HOME" beats "SAFARICOM"
    return sorted(merged.items(), key=lambda kv: (kv[0] not in user, -len(kv[0])))


def categorize(rec: dict, rules: list[tuple[str, str]]) -> str:
    haystack = f"{rec['counterparty']} {rec['account']}".upper()
    for keyword, category in rules:
        if keyword in haystack:
            return category
    return KIND_FALLBACK.get(rec["kind"], "Uncategorized")


# ---------------------------------------------------------------- storage

SCHEMA = """
CREATE TABLE IF NOT EXISTS txns (
    code TEXT NOT NULL, kind TEXT NOT NULL, direction TEXT,
    amount REAL, cost REAL, balance REAL,
    counterparty TEXT, phone TEXT, account TEXT,
    ts TEXT, category TEXT, raw TEXT,
    PRIMARY KEY (code, kind)
);
CREATE TABLE IF NOT EXISTS unparsed (raw TEXT PRIMARY KEY);
"""
COLUMNS = ["code", "kind", "direction", "amount", "cost", "balance",
           "counterparty", "phone", "account", "ts", "category", "raw"]


def open_db(path: Path) -> sqlite3.Connection:
    db = sqlite3.connect(path)
    db.executescript(SCHEMA)
    return db


# ---------------------------------------------------------------- input

def bodies_from_json(items: list[dict]) -> list[str]:
    out = []
    for m in items:
        sender = str(m.get("number") or m.get("sender") or m.get("address") or "")
        if sender and "MPESA" not in sender.upper().replace("-", ""):
            continue
        out.append(m.get("body", ""))
    return out


def load_messages(source: str, limit: int) -> list[str]:
    if source == "termux":
        try:
            out = subprocess.run(
                ["termux-sms-list", "-l", str(limit), "-t", "inbox"],
                capture_output=True, text=True, check=True,
            ).stdout
        except FileNotFoundError:
            sys.exit("termux-sms-list not found. Run `pkg install termux-api` "
                     "and install the Termux:API app (grant it SMS permission).")
        return bodies_from_json(json.loads(out))
    path = Path(source)
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        return bodies_from_json(json.loads(text))
    return [block for block in re.split(r"\n\s*\n", text) if block.strip()]


# ---------------------------------------------------------------- output helpers

def bars(pairs: list[tuple[str, float]], width: int = 24) -> None:
    if not pairs:
        print("  (nothing)")
        return
    top = max(v for _, v in pairs) or 1
    label_w = max(len(k) for k, _ in pairs)
    for label, value in pairs:
        n = max(1, round(width * value / top)) if value > 0 else 0
        print(f"  {label:<{label_w}}  {'█' * n:<{width}}  {value:>11,.2f}")


def ksh(v: float) -> str:
    return f"Ksh {v:>12,.2f}"


# ---------------------------------------------------------------- commands

def cmd_import(db, args):
    if args.termux:
        source = "termux"
    elif args.source:
        source = args.source
    else:
        sys.exit("Give a file to import, or use --termux.")
    rules = load_rules()
    added = dupes = unknown = skipped = 0
    for body in load_messages(source, args.limit):
        rec = parse_sms(body)
        if rec is None:
            skipped += 1
            continue
        if rec["kind"] == "unknown":
            cur = db.execute("INSERT OR IGNORE INTO unparsed (raw) VALUES (?)", (rec["raw"],))
            unknown += cur.rowcount
            continue
        rec["category"] = categorize(rec, rules)
        cur = db.execute(
            f"INSERT OR IGNORE INTO txns ({', '.join(COLUMNS)}) "
            f"VALUES ({', '.join(':' + c for c in COLUMNS)})", rec)
        if cur.rowcount:
            added += 1
        else:
            dupes += 1
    # Fuliza messages carry no date; borrow it from the transaction they share a code with.
    db.execute(
        "UPDATE txns SET ts = (SELECT t2.ts FROM txns t2 WHERE t2.code = txns.code "
        "AND t2.ts IS NOT NULL LIMIT 1) WHERE ts IS NULL")
    db.commit()
    print(f"Imported {added} new · {dupes} already in ledger · "
          f"{unknown} unparsed · {skipped} non-transaction messages skipped")
    if unknown:
        print("Run `unparsed` to see the messages the parser didn't recognise.")


def cmd_report(db, args):
    if args.all:
        where, params, label = "", (), "all time"
    else:
        month = args.month or datetime.now().strftime("%Y-%m")
        where, params, label = "WHERE substr(ts, 1, 7) = ?", (month,), month
    rows = db.execute(
        f"SELECT kind, direction, amount, COALESCE(cost, 0), counterparty, category "
        f"FROM txns {where}", params).fetchall()
    if not rows:
        print(f"No transactions for {label}.")
        return

    money_in = sum(a for k, d, a, c, cp, cat in rows if d == "in")
    money_out = sum(a for k, d, a, c, cp, cat in rows if d == "out")
    fees = sum(c for k, d, a, c, cp, cat in rows)
    fuliza = sum(a for k, d, a, c, cp, cat in rows if k == "fuliza")
    saved = sum(a for k, d, a, c, cp, cat in rows if k == "savings")

    print(f"\nM-PESA ledger · {label}  ({len(rows)} transactions)\n")
    print(f"  In      {ksh(money_in)}")
    print(f"  Out     {ksh(money_out)}")
    share = f"   ({fees / money_out:.1%} of out)" if money_out else ""
    print(f"  Fees    {ksh(fees)}{share}")
    print(f"  Net     {ksh(money_in - money_out - fees)}")
    if fuliza:
        print(f"  Fuliza  {ksh(fuliza)}   borrowed")
    if saved:
        print(f"  Saved   {ksh(saved)}   moved to M-Shwari")

    by_cat, by_who, count = defaultdict(float), defaultdict(float), defaultdict(int)
    for kind, d, amount, cost, cp, cat in rows:
        if d == "out":
            by_cat[cat] += amount
            by_who[cp] += amount
            count[cp] += 1

    print("\nSpending by category")
    bars(sorted(by_cat.items(), key=lambda kv: -kv[1]))

    print("\nTop recipients")
    top = sorted(by_who.items(), key=lambda kv: -kv[1])[: args.top]
    w = max((len(k) for k, _ in top), default=0)
    for i, (who, total) in enumerate(top, 1):
        print(f"  {i}. {who:<{w}}  {total:>11,.2f}  ({count[who]}x)")
    print()


def cmd_trend(db, args):
    rows = db.execute(
        "SELECT substr(ts, 1, 7) AS m, "
        "SUM(CASE WHEN direction = 'out' THEN amount + COALESCE(cost, 0) ELSE 0 END), "
        "SUM(CASE WHEN direction = 'in' THEN amount ELSE 0 END) "
        "FROM txns WHERE ts IS NOT NULL GROUP BY m ORDER BY m DESC LIMIT ?",
        (args.months,)).fetchall()
    if not rows:
        print("Ledger is empty.")
        return
    rows.reverse()
    print("\nMonthly burn (out + fees)\n")
    bars([(m, out) for m, out, _ in rows])
    print("\nIncoming per month\n")
    bars([(m, inc) for m, _, inc in rows])
    print()


def cmd_recategorize(db, args):
    rules = load_rules()
    rows = db.execute("SELECT code, kind, counterparty, account FROM txns").fetchall()
    changed = 0
    for code, kind, cp, account in rows:
        cat = categorize({"kind": kind, "counterparty": cp, "account": account}, rules)
        changed += db.execute(
            "UPDATE txns SET category = ? WHERE code = ? AND kind = ? AND category IS NOT ?",
            (cat, code, kind, cat)).rowcount
    db.commit()
    print(f"Re-categorized {changed} of {len(rows)} transactions.")


def cmd_unparsed(db, args):
    rows = db.execute("SELECT raw FROM unparsed").fetchall()
    if not rows:
        print("Nothing unparsed. 🎉")
        return
    for (raw,) in rows:
        print("-" * 60)
        print(raw)
    print("-" * 60)
    print(f"{len(rows)} message(s). Add a pattern to PATTERNS in mpesa_ledger.py to handle them.")


def cmd_export(db, args):
    rows = db.execute(f"SELECT {', '.join(COLUMNS[:-1])} FROM txns ORDER BY ts").fetchall()
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(COLUMNS[:-1])
        w.writerows(rows)
    print(f"Wrote {len(rows)} rows to {args.out}")


# ---------------------------------------------------------------- main

def main() -> None:
    ap = argparse.ArgumentParser(description="Local M-Pesa SMS ledger")
    ap.add_argument("--db", type=Path, default=DEFAULT_DB, help=f"database file (default {DEFAULT_DB})")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("import", help="import SMS from Termux or a file")
    p.add_argument("source", nargs="?", help=".txt (blank-line separated) or .json")
    p.add_argument("--termux", action="store_true", help="read straight from the phone's inbox")
    p.add_argument("--limit", type=int, default=5000)
    p.set_defaults(fn=cmd_import)

    p = sub.add_parser("report", help="totals, categories and top recipients")
    p.add_argument("--month", help="YYYY-MM (default: current month)")
    p.add_argument("--all", action="store_true")
    p.add_argument("--top", type=int, default=5)
    p.set_defaults(fn=cmd_report)

    p = sub.add_parser("trend", help="monthly burn chart")
    p.add_argument("--months", type=int, default=12)
    p.set_defaults(fn=cmd_trend)

    sub.add_parser("recategorize", help="re-apply category rules").set_defaults(fn=cmd_recategorize)
    sub.add_parser("unparsed", help="show messages the parser missed").set_defaults(fn=cmd_unparsed)

    p = sub.add_parser("export", help="write the ledger to CSV")
    p.add_argument("out")
    p.set_defaults(fn=cmd_export)

    args = ap.parse_args()
    db = open_db(args.db)
    try:
        args.fn(db, args)
    finally:
        db.close()


if __name__ == "__main__":
    main()
