"""Check whether a bug of .claude/context/BUGS.md can be fixed or verified, list all bugs, or get the next free ID.

Usage:
  python .claude/scripts/check-bug-status.py <id> --for fix       (accepts 1, 001 or BUG-001)
  python .claude/scripts/check-bug-status.py <id> --for verify
  python .claude/scripts/check-bug-status.py --list
  python .claude/scripts/check-bug-status.py --next

With <id>, the first line is `OK:` when the bug exists and its status allows the step
(fix: Open or In Progress, so an interrupted fix may be retried; verify: Fixed), and `BLOCKED:`
otherwise. Always exits 0 so command `!` injection shows the reason.
"""
import re
import sys
from pathlib import Path

BUGS = Path(__file__).resolve().parents[2] / ".claude" / "context" / "BUGS.md"
HEADING = re.compile(r"^### (BUG-\d+): (.*)$")
FIELD = re.compile(r"^- \*\*(Status|Severity):\*\* (.*)$")
STARTABLE = {"fix": ("Open", "In Progress"), "verify": ("Fixed",)}


def parse(lines):
    """Return {id: {title, status, severity}} in file order."""
    bugs, current = {}, None
    for line in lines:
        if m := HEADING.match(line):
            current = {"id": m[1], "title": m[2], "Status": None, "Severity": None}
            bugs[m[1]] = current
        elif line.startswith("## "):
            current = None
        elif current and (f := FIELD.match(line)):
            current[f[1]] = f[2].strip()
    return bugs


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    bugs = parse(BUGS.read_text(encoding="utf-8").splitlines()) if BUGS.exists() else {}

    if argv == ["--list"]:
        for bug in bugs.values():
            print(f"{bug['id']}\t{bug['Status']}\t{bug['Severity']}\t{bug['title']}")
        if not bugs:
            print("No bugs.")
        return 0
    if argv == ["--next"]:
        print(f"BUG-{max((int(b[4:]) for b in bugs), default=0) + 1:03d}")
        return 0
    if len(argv) != 3 or argv[1] != "--for" or argv[2] not in STARTABLE \
            or not re.fullmatch(r"(?i:BUG-)?\d+", argv[0]):
        print(f"BLOCKED: invalid arguments '{' '.join(argv)}'; use <id> --for fix|verify.")
        return 0

    key = f"BUG-{int(re.sub(r'\D', '', argv[0])):03d}"
    bug = bugs.get(key)
    if bug is None:
        print(f"BLOCKED: {key} not found. Available: {', '.join(bugs) or 'none'}")
        return 0

    allowed = STARTABLE[argv[2]]
    if bug["Status"] not in allowed:
        print(f"BLOCKED: {key} is '{bug['Status']}', not {' or '.join(repr(s) for s in allowed)}.")
    else:
        print(f"OK: {key} — {bug['title']} is {bug['Status']} ({bug['Severity']}).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
