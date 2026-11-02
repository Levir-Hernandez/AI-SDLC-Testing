"""Change the status of one bug in .claude/context/BUGS.md, touching no other bug, and refresh the header counts.

Usage:
  python .claude/scripts/update-bug-status.py <id> <Open|In Progress|Fixed|Verified>   (accepts 1, 001 or BUG-001)
  python .claude/scripts/update-bug-status.py <id> Duplicate <BUG-XXX>
  python .claude/scripts/update-bug-status.py --refresh

  Quote "In Progress" or pass it as two words. The header line `- **Open:** <n> (Critical <n>, ...)`
  counts Open and In Progress bugs; --refresh recomputes it after new bugs are written.
"""
import re
import sys
from pathlib import Path

BUGS = Path(__file__).resolve().parents[2] / ".claude" / "context" / "BUGS.md"
STATUSES = ("Open", "In Progress", "Fixed", "Verified")
SEVERITIES = ("Critical", "High", "Medium", "Low")
UNRESOLVED = ("Open", "In Progress")
HEADING = re.compile(r"^### (BUG-\d+):")
FIELD = re.compile(r"^(- \*\*(Status|Severity):\*\* )(.*)$")


def key_of(value):
    if not re.fullmatch(r"(?i:BUG-)?\d+", value):
        sys.exit(__doc__)
    return f"BUG-{int(re.sub(r'\D', '', value)):03d}"


def refresh(lines):
    """Recompute the header's Open line from every bug's Status and Severity."""
    bugs, current = [], None
    for line in lines:
        if HEADING.match(line):
            current = {}
            bugs.append(current)
        elif line.startswith("## "):
            current = None
        elif current is not None and (m := FIELD.match(line.rstrip("\r\n"))):
            current[m[2]] = m[3].strip()
    open_severities = [b.get("Severity") for b in bugs if b.get("Status") in UNRESOLVED]
    counts = ", ".join(f"{s} {open_severities.count(s)}" for s in SEVERITIES)
    for i, line in enumerate(lines):
        if line.startswith("- **Open:** "):
            eol = line[len(line.rstrip("\r\n")):]
            lines[i] = f"- **Open:** {len(open_severities)} ({counts}){eol}"
            break
    return lines


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    # Read and write bytes-exact so CRLF line endings survive.
    lines = BUGS.read_bytes().decode("utf-8").splitlines(keepends=True)

    if argv == ["--refresh"]:
        BUGS.write_text("".join(refresh(lines)), encoding="utf-8", newline="")
        print("Header counts updated.")
        return 0
    if len(argv) < 2:
        sys.exit(__doc__)
    key, words = key_of(argv[0]), " ".join(argv[1:])
    if words.lower().startswith("duplicate"):
        target = key_of(argv[-1]) if len(argv) == 3 else None
        if target is None or target == key or not any(l.startswith(f"### {target}:") for l in lines):
            sys.exit(f"Duplicate needs another existing bug than {key}: <id> Duplicate <BUG-XXX>.")
        new = f"Duplicate of {target}"
    else:
        new = next((s for s in STATUSES if s.lower() == words.lower()), None)
        if new is None:
            sys.exit(__doc__)

    in_section = False
    for i, line in enumerate(lines):
        if line.startswith("### ") or line.startswith("## "):
            in_section = line.startswith(f"### {key}:")
        elif in_section and (m := FIELD.match(line.rstrip("\r\n"))) and m[2] == "Status":
            if m[3].strip() == new:
                print(f"{key} is already '{new}'.")
                return 0
            eol = line[len(line.rstrip("\r\n")):]
            lines[i] = f"{m[1]}{new}{eol}"
            BUGS.write_text("".join(refresh(lines)), encoding="utf-8", newline="")
            print(f"{key}: '{m[3].strip()}' -> '{new}'.")
            return 0
    sys.exit(f"{key} or its status line not found in {BUGS.name}.")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
