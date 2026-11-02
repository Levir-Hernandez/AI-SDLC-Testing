"""Change the status of one increment in .claude/context/PLAN.md, touching no other line.

Usage:
  python .claude/scripts/update-increment-status.py <n> <pending|completed|blocked|superseded>

  blocked marks an increment that failed; it can be retried without resetting it to pending.
  superseded marks an increment that planner Mode 3 split into sub-increments.
"""
import re
import sys
from pathlib import Path

PLAN = Path(__file__).resolve().parents[2] / ".claude" / "context" / "PLAN.md"
STATUSES = ("pending", "completed", "blocked", "superseded")
STATUS = re.compile(r"^(- \*\*Status:\*\* `)([a-z-]+)(`.*)$")


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if len(argv) != 2 or not re.fullmatch(r"[Ii]?\d+[a-z]?", argv[0]) or argv[1] not in STATUSES:
        sys.exit(__doc__)
    key, new = "I" + argv[0].lstrip("Ii"), argv[1]

    # Read and write bytes-exact so CRLF line endings survive.
    lines = PLAN.read_bytes().decode("utf-8").splitlines(keepends=True)
    in_section = False
    for i, line in enumerate(lines):
        if line.startswith("### I"):
            in_section = line.startswith(f"### {key} ")
        elif in_section and (m := STATUS.match(line.rstrip("\r\n"))):
            if m[2] == new:
                print(f"{key} is already '{new}'.")
                return 0
            eol = line[len(line.rstrip("\r\n")):]
            lines[i] = f"{m[1]}{new}{m[3]}{eol}"
            PLAN.write_text("".join(lines), encoding="utf-8", newline="")
            print(f"{key}: '{m[2]}' -> '{new}'.")
            return 0
    sys.exit(f"{key} or its status line not found in {PLAN.name}.")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
