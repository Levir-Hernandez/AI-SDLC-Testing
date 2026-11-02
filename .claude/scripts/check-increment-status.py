"""Check whether an increment of .claude/context/PLAN.md can be implemented, or list all statuses.

Usage:
  python .claude/scripts/check-increment-status.py <n>      (accepts 7 or I7)
  python .claude/scripts/check-increment-status.py --list
  python .claude/scripts/check-increment-status.py --next

With <n>, the first line is `OK:` when the increment exists, is pending or blocked
(a blocked increment may be retried) and all its dependencies are completed (I0, the
existing skeleton, counts as completed), and `BLOCKED:` otherwise. Always exits 0 so command `!` injection shows the reason.
With --next, the first line is `NEXT: <id> — <title>`, `DONE:` when nothing is left, or `HALT:` when
BLOCKED.md exists or the remaining increments cannot start.
"""
import re
import sys
from pathlib import Path

PLAN = Path(__file__).resolve().parents[2] / ".claude" / "context" / "PLAN.md"
HEADING = re.compile(r"^### (I\d+[a-z]?) — (.*)$")
STATUS = re.compile(r"^- \*\*Status:\*\* `([a-z-]+)`")
DEPENDS = re.compile(r"^- \*\*Depends on:\*\* (.*)$")
BLOCKED_FILE = PLAN.with_name("BLOCKED.md")
SKELETON = "I0"
STARTABLE = ("pending", "blocked")
FINISHED = ("completed", "superseded")


def parse(lines):
    """Return {id: {title, status, depends}} in plan order."""
    increments, current = {}, None
    for line in lines:
        if m := HEADING.match(line):
            current = {"id": m[1], "title": m[2], "status": None, "depends": []}
            increments[m[1]] = current
        elif current:
            if s := STATUS.match(line):
                current["status"] = s[1]
            elif d := DEPENDS.match(line):
                current["depends"] = re.findall(r"\bI\d+[a-z]?\b", d[1])
    return increments


def dependency_problems(inc, increments):
    problems = []
    for dep in inc["depends"]:
        status = "completed" if dep == SKELETON else increments.get(dep, {}).get("status")
        if status != "completed":
            problems.append(f"Dependency {dep} is '{status}', not 'completed'.")
    return problems


def next_increment(increments):
    """Print the first startable increment in plan order, or why there is none."""
    if BLOCKED_FILE.exists():
        print("HALT: BLOCKED.md exists; resolve it with /escalate.")
        return 0
    remaining = [inc for inc in increments.values() if inc["status"] not in FINISHED]
    if not remaining:
        print(f"DONE: all {len(increments)} increments are finished.")
        return 0
    for inc in remaining:
        if inc["status"] in STARTABLE and not dependency_problems(inc, increments):
            print(f"NEXT: {inc['id']} — {inc['title']}")
            return 0
    print("HALT: no increment can start. " + " ".join(f"{inc['id']} is '{inc['status']}'." for inc in remaining))
    return 0


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    increments = parse(PLAN.read_text(encoding="utf-8").splitlines())

    if argv == ["--list"]:
        for inc in increments.values():
            print(f"{inc['id']}\t{inc['status']}\t{','.join(inc['depends']) or '-'}\t{inc['title']}")
        return 0
    if argv == ["--next"]:
        return next_increment(increments)
    if len(argv) != 1 or not re.fullmatch(r"[Ii]?\d+[a-z]?", argv[0]):
        print(f"BLOCKED: invalid increment '{' '.join(argv)}'; use a number like 7 or I7.")
        return 0

    key = "I" + argv[0].lstrip("Ii")
    inc = increments.get(key)
    if inc is None:
        print(f"BLOCKED: {key} not found. Available: {', '.join(increments)}")
        return 0

    problems = []
    if inc["status"] not in STARTABLE:
        problems.append(f"{key} is '{inc['status']}', not 'pending' or 'blocked'.")
    problems += dependency_problems(inc, increments)
    if problems:
        print("BLOCKED: " + " ".join(problems))
    else:
        print(f"OK: {key} — {inc['title']} is {inc['status']}; dependencies completed: {', '.join(inc['depends']) or 'none'}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
