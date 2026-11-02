"""Change .claude/context/PLAN.md section by section for planner Modes 2 and 3, touching nothing else.

Usage:
  python .claude/scripts/plan-edit.py replace <id> <<'PLAN'         new text of a non-completed increment
  python .claude/scripts/plan-edit.py insert-after <id> <<'PLAN'    new sub-increments right after <id>
  python .claude/scripts/plan-edit.py rewire <old> <new>            pending "Depends on" from <old> to <new>
  python .claude/scripts/plan-edit.py changelog <mode> <increments> <change...>   add a Plan Changelog row

Stdin sections must start with `### I<n>[a-z] — <title>`. A script is used because Write and Edit
on .claude/ always prompt, which would stall an autonomous run.
"""
import re
import sys
from datetime import date
from pathlib import Path

PLAN = Path(__file__).resolve().parents[1] / "context" / "PLAN.md"
SECTION = re.compile(r"^### (I\d+[a-z]?) — ")
TABLE = "## Plan Changelog"
ARG_COUNTS = {"replace": 2, "insert-after": 2, "rewire": 3}


def normalize(value):
    return "I" + value.lstrip("Ii")


def bounds(lines, key):
    """Return (start, end) of an increment; it ends at the next increment or `## ` heading."""
    start = next((i for i, l in enumerate(lines) if l.startswith(f"### {key} ")), None)
    if start is None:
        sys.exit(f"{key} not found in {PLAN.name}.")
    end = next(
        (i for i in range(start + 1, len(lines)) if lines[i].startswith("### I") or lines[i].startswith("## ")),
        len(lines),
    )
    return start, end


def section_text():
    sys.stdin.reconfigure(encoding="utf-8")
    text = sys.stdin.read().strip("\n")
    if not SECTION.match(text):
        sys.exit("Stdin must start with `### I<n> — <title>`.")
    return text.splitlines() + [""]


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    valid = argv and (len(argv) >= 4 if argv[0] == "changelog" else len(argv) == ARG_COUNTS.get(argv[0]))
    if not valid:
        sys.exit(__doc__)
    command = argv[0]
    lines = PLAN.read_text(encoding="utf-8").splitlines()

    if command == "replace":
        key = normalize(argv[1])
        start, end = bounds(lines, key)
        if any("**Status:** `completed`" in l for l in lines[start:end]):
            sys.exit(f"{key} is completed; completed increments are never rewritten.")
        lines[start:end] = section_text()
    elif command == "insert-after":
        _, end = bounds(lines, normalize(argv[1]))
        lines[end:end] = section_text()
    elif command == "rewire":
        old, new = normalize(argv[1]), normalize(argv[2])
        status = None
        for i, line in enumerate(lines):
            if m := re.match(r"^- \*\*Status:\*\* `([a-z-]+)`", line):
                status = m[1]
            elif line.startswith("- **Depends on:**") and status != "completed":
                lines[i] = re.sub(rf"\b{old}\b", new, line)
    else:
        row = f"| {date.today()} | {argv[1]} | {argv[2]} | {' '.join(argv[3:]).replace('|', '/')} |"
        if TABLE not in lines:
            lines += ["", TABLE, "", "| Date | Mode | Increments | Change |", "|---|---|---|---|"]
        lines.append(row)

    PLAN.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"PLAN.md: {command} {' '.join(argv[1:3])} done.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
