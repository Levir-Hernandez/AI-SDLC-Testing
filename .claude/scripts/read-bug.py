"""Print the bug review header and one bug of .claude/context/BUGS.md, without the other bugs.

Usage:
  python .claude/scripts/read-bug.py <id>    (accepts 1, 001 or BUG-001)
"""
import re
import sys
from pathlib import Path

BUGS = Path(__file__).resolve().parents[2] / ".claude" / "context" / "BUGS.md"


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if len(argv) != 1 or not re.fullmatch(r"(?i:BUG-)?\d+", argv[0]):
        sys.exit(__doc__)
    key = f"BUG-{int(re.sub(r'\D', '', argv[0])):03d}"

    lines = BUGS.read_text(encoding="utf-8").splitlines()
    header_end = next((i for i, l in enumerate(lines) if l.startswith("## Bugs")), len(lines))
    start = next((i for i, l in enumerate(lines) if l.startswith(f"### {key}:")), None)
    if start is None:
        found = re.findall(r"^### (BUG-\d+):", "\n".join(lines), re.MULTILINE)
        sys.exit(f"{key} not found. Available: {', '.join(found) or 'none'}")
    end = next((i for i in range(start + 1, len(lines)) if re.match(r"^(### BUG-|## |---\s*$)", lines[i])), len(lines))

    print("\n".join(lines[:header_end]).rstrip())
    print("\n## Bugs\n")
    print("\n".join(lines[start:end]).rstrip())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
