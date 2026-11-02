"""Print the plan header and one increment of .claude/context/PLAN.md, without the other increments.

Usage:
  python .claude/scripts/read-increment.py <n> [--no-header]    (accepts 7, I7 or I7a)
"""
import re
import sys
from pathlib import Path

PLAN = Path(__file__).resolve().parents[2] / ".claude" / "context" / "PLAN.md"


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    no_header = "--no-header" in argv
    argv = [a for a in argv if a != "--no-header"]
    if len(argv) != 1 or not re.fullmatch(r"[Ii]?\d+[a-z]?", argv[0]):
        sys.exit(__doc__)
    key = "I" + argv[0].lstrip("Ii")

    lines = PLAN.read_text(encoding="utf-8").splitlines()
    header_end = next((i for i, l in enumerate(lines) if l.startswith("## Increments")), len(lines))
    start = next((i for i, l in enumerate(lines) if l.startswith(f"### {key} ")), None)
    if start is None:
        found = re.findall(r"^### (I\d+[a-z]?) ", "\n".join(lines), re.MULTILINE)
        sys.exit(f"{key} not found. Available: {', '.join(found)}")
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith(("### I", "## "))), len(lines))

    if not no_header:
        print("\n".join(lines[:header_end]).rstrip())
        print("\n## Increments\n")
    print("\n".join(lines[start:end]).rstrip())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
