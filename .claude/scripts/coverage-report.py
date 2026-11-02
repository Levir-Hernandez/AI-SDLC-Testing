"""Print backend line and branch coverage from the JaCoCo report written by `./mvnw verify`.

Usage:
  python .claude/scripts/coverage-report.py [--min <percent>]

Exits 1 when the report is missing or either coverage is below --min.
"""
import csv
import sys
from pathlib import Path

REPORT = Path(__file__).resolve().parents[2] / "backend" / "target" / "site" / "jacoco" / "jacoco.csv"


def percent(rows, kind):
    covered = sum(int(r[f"{kind}_COVERED"]) for r in rows)
    total = covered + sum(int(r[f"{kind}_MISSED"]) for r in rows)
    return 100 * covered / total if total else 100.0


def main(argv):
    minimum = float(argv[1]) if argv[:1] == ["--min"] else None
    if not REPORT.exists():
        print(f"COVERAGE: missing {REPORT.name}; run ./mvnw verify with the JaCoCo plugin configured.")
        return 1
    with REPORT.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))
    line, branch = percent(rows, "LINE"), percent(rows, "BRANCH")
    print(f"COVERAGE: {line:.1f}% lines, {branch:.1f}% branches")
    return 1 if minimum is not None and min(line, branch) < minimum else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
