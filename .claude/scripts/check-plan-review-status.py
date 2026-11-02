"""Print the plan-review verdict and the open findings that target one increment.

Usage:
  python .claude/scripts/check-plan-review-status.py <n>

First line is `OK:` or `BLOCKED:` (always exits 0 so command `!` injection shows the reason):
  READY                         -> OK; open findings targeting I<n> are listed to address
  READY WITH MINOR CHANGES      -> OK unless an open major or blocker targets I<n>
  REVISE BEFORE IMPLEMENTATION  -> BLOCKED
"""
import re
import sys
from pathlib import Path

REVIEW = Path(__file__).resolve().parents[2] / ".claude" / "context" / "PLAN_REVIEW.md"
SECTIONS = {"## Blockers": "blocker", "## Major Findings": "major", "## Minor Findings": "minor"}
OPEN = re.compile(r"^- \*\*Status:\*\* `?(open|needs context)", re.IGNORECASE)


def field(lines, name):
    pattern = re.compile(rf"^- \*\*{name}:\*\* (.*)$")
    return next((m[1].strip() for l in lines if (m := pattern.match(l))), None)


def findings(lines):
    """Yield (severity, block lines) for every finding in Blockers/Major/Minor."""
    severity, block = None, []
    for line in lines + ["## End"]:
        if line.startswith("## "):
            if block:
                yield severity, block
            severity, block = SECTIONS.get(line.strip()), []
        elif line.startswith("### ") and severity:
            if block:
                yield severity, block
            block = [line]
        elif block:
            block.append(line)


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if len(argv) != 1 or not re.fullmatch(r"[Ii]?\d+[a-z]?", argv[0]):
        print(f"BLOCKED: invalid increment '{' '.join(argv)}'; use a number like 7 or I7.")
        return 0
    key = "I" + argv[0].lstrip("Ii")
    if not REVIEW.exists():
        print("BLOCKED: .claude/context/PLAN_REVIEW.md does not exist; run review-plan first.")
        return 0

    lines = REVIEW.read_text(encoding="utf-8").splitlines()
    verdict = (field(lines, "Verdict") or "").upper()
    summary = f"Verdict: {verdict or 'missing'} (pass {field(lines, 'Review pass') or '?'}); Open: {field(lines, 'Open') or '?'}"

    targeted = []
    for severity, block in findings(lines):
        targets = field(block, "Targets") or ""
        if any(OPEN.match(l) for l in block) and key in re.findall(r"\bI\d+[a-z]?\b", targets):
            targeted.append((severity, block))

    ok = f"OK: plan review allows implementing {key}."
    if verdict == "READY":
        status = ok
    elif verdict == "READY WITH MINOR CHANGES":
        serious = [b[0].removeprefix("### ") for s, b in targeted if s in ("blocker", "major")]
        status = f"BLOCKED: open {', '.join(serious)} target {key}; revise the plan first" if serious else ok
    else:
        status = f"BLOCKED: plan verdict is '{verdict or 'missing'}'; implementation needs READY or READY WITH MINOR CHANGES"

    print(f"{status}\n{summary}")
    if not targeted:
        print(f"No open findings target {key}.")
    for severity, block in targeted:
        print(f"\n[{severity}] " + "\n".join(block).rstrip())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
