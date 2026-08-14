"""Spot-check the generated Report 5.2 TSV."""
import csv
import sys
from collections import Counter

rows = list(csv.reader(open("report_5_2_integration_tests.tsv", encoding="utf-8-sig"), delimiter="\t"))
hdr, data = rows[0], rows[1:]

mode = sys.argv[1] if len(sys.argv) > 1 else "anomalies"

if mode == "anomalies":
    print("--- non-pass rows ---")
    for r in data:
        if r[12] != "Pass":
            print(f"{r[0]} | {r[12]} | {r[14]}")
    print("\n--- suspicious Role/Session ---")
    for r in data:
        if "token" in r[6] or r[6] not in {
            "admin_01 (ADMIN)", "reader_01 (READER)", "author_01 (AUTHOR)",
            "moderator_01 (MODERATOR)", "leader_01 (PROJECT_LEADER)",
            "translator_01 (TRANSLATOR)", "(no session)",
        }:
            print(f"{r[0]} | {r[6]} | {r[14]}")
    print("\n--- rows with no endpoint / no status code ---")
    for r in data:
        if not r[1] or r[10].startswith("request completed"):
            print(f"{r[0]} | endpoint='{r[1]}' | then='{r[10][:80]}' | {r[14]}")
    print("\n--- longest When/Then cells ---")
    for r in sorted(data, key=lambda x: -len(x[9]))[:3]:
        print(f"{r[0]} WHEN({len(r[9])}): {r[9][:300]}")
    for r in sorted(data, key=lambda x: -len(x[10]))[:3]:
        print(f"{r[0]} THEN({len(r[10])}): {r[10][:300]}")
else:
    wanted = mode
    for r in data:
        if r[0].startswith(wanted) or wanted in r[14]:
            for name, val in zip(hdr, r):
                print(f"  {name:34}: {val}")
            print("-" * 90)
