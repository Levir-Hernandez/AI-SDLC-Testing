"""Read .claude/context/PROGRESS.md without loading all of it, and answer the orchestrator's counters.

Usage:
  python .claude/scripts/read-progress.py --last <n>            last n entries (0 = every entry)
  python .claude/scripts/read-progress.py --attempts <id>       failed gates since its last escalation
  python .claude/scripts/read-progress.py --escalations <id>    escalations of the increment and its splits
  python .claude/scripts/read-progress.py --since-validation    increments passed since the last validation
  python .claude/scripts/read-progress.py --repeated-findings   Medium findings on the last 2 passed increments
  python .claude/scripts/read-progress.py --stats               run totals for the Workflow Complete message
"""
import re
import sys
from pathlib import Path

PROGRESS = Path(__file__).resolve().parents[1] / "context" / "PROGRESS.md"
TITLE = re.compile(r"^## \[[^\]]*\] — (\w+) — ")
RETRY_WARNING = 40
OPTIONS = ("--last", "--attempts", "--escalations", "--since-validation", "--repeated-findings", "--stats")


def entries():
    """Return [{type, text, fields}] for every entry; entries outside the patterns have type None."""
    if not PROGRESS.exists():
        return []
    result = []
    for block in re.split(r"(?m)^(?=## )", PROGRESS.read_text(encoding="utf-8")):
        if block.startswith("## "):
            match = TITLE.match(block)
            fields = dict(re.findall(r"(?m)^- (\w+): (.*)$", block))
            result.append({"type": match[1] if match else None, "text": block.rstrip(), "fields": fields})
    return result


def normalize(value):
    return "I" + value.lstrip("Ii")


def root(increment):
    return re.sub(r"[a-z]$", "", increment)


def attempts(all_entries, key):
    count = 0
    for entry in all_entries:
        if entry["fields"].get("increment") != key:
            continue
        if entry["type"] == "Escalation":
            count = 0
        elif entry["type"] == "Gate" and entry["fields"].get("result") == "FAIL":
            count += 1
    return count


def passed(all_entries):
    return [e for e in all_entries if e["type"] == "Gate" and e["fields"].get("result") == "PASS"]


def finding_keys(entry):
    return set(re.findall(r"[\w-]+", entry["fields"].get("medium_findings", ""))) - {"none"}


def stats(all_entries):
    done = passed(all_entries)
    retried = {
        e["fields"].get("increment")
        for e in all_entries
        if e["type"] == "Gate" and e["fields"].get("result") == "FAIL"
    }
    rate = round(100 * len(retried) / len(done)) if done else 0
    print(f"increments_passed: {len(done)}")
    print(f"autonomous_first_try: {sum(e['fields'].get('increment') not in retried for e in done)}")
    print(f"required_retries: {len(retried)}")
    print(f"mode3_escalations: {sum(e['type'] == 'Escalation' for e in all_entries)}")
    print(f"forward_validations: {sum(e['type'] == 'Validation' for e in all_entries)}")
    print(f"refactors: {sum(e['type'] == 'Refactor' for e in all_entries)}")
    print(f"retry_rate: {rate}%")
    if rate > RETRY_WARNING:
        print(f"WARNING: more than {RETRY_WARNING}% of increments required retries; "
              "the planner is producing increments that are too large.")


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if not argv or argv[0] not in OPTIONS:
        sys.exit(__doc__)
    all_entries = entries()
    option = argv[0]

    if option == "--last":
        count = int(argv[1])
        print("\n\n".join(e["text"] for e in (all_entries[-count:] if count else all_entries)) or "No entries yet.")
    elif option == "--attempts":
        print(attempts(all_entries, normalize(argv[1])))
    elif option == "--escalations":
        key = root(normalize(argv[1]))
        print(sum(e["type"] == "Escalation" and root(e["fields"].get("increment", "")) == key for e in all_entries))
    elif option == "--since-validation":
        count = 0
        for entry in all_entries:
            if entry["type"] == "Validation":
                count = 0
            elif entry["type"] == "Gate" and entry["fields"].get("result") == "PASS":
                count += 1
        print(count)
    elif option == "--repeated-findings":
        last_two = passed(all_entries)[-2:]
        repeated = sorted(finding_keys(last_two[0]) & finding_keys(last_two[1])) if len(last_two) == 2 else []
        print(", ".join(repeated) or "none")
    else:
        stats(all_entries)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
