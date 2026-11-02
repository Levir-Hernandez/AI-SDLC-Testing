"""PreToolUse hook for the refactor agent: keep it on its bug and on application code.

.claude/context/PLAN.md and .claude/context/PLAN_REVIEW.md are blocked; a fix checks expected behavior against TASK.md.
.claude/context/BUGS.md and .claude/context/QA_REPORT.md are reached only through the bug scripts.
Writes are allowed only to application code, tests and the journal files.
Exit code 2 blocks the tool call and shows stderr to the agent.
"""
import json
import os
import sys
from pathlib import Path

ROOT = Path(os.environ.get("CLAUDE_PROJECT_DIR", Path(__file__).resolve().parents[2])).resolve()
BLOCKED = ("PLAN.md", "PLAN_REVIEW.md")
PROTECTED = ("BUGS.md", "QA_REPORT.md")
ALLOWED = (
    "read-bug.py",
    "check-bug-status.py",
    "update-bug-status.py",
    "context-write.py",
    "read-progress.py",
)
WRITABLE_FILES = (".claude/context/PROGRESS.md", ".claude/context/CHANGELOG.md")
WRITABLE_DIRS = ("backend/", "frontend/")
GENERATED = ("backend/target/", "frontend/node_modules/", "frontend/.next/")


def block(message):
    print(message, file=sys.stderr)
    sys.exit(2)


payload = json.load(sys.stdin)
tool_input = json.dumps(payload.get("tool_input", {}))

if any(name in tool_input for name in BLOCKED):
    block("Access to .claude/context/PLAN.md and .claude/context/PLAN_REVIEW.md is blocked. "
          "Check the expected behavior against TASK.md and the bug.")
if any(name in tool_input for name in PROTECTED) and not any(script in tool_input for script in ALLOWED):
    block(
        "Direct access to .claude/context/BUGS.md and .claude/context/QA_REPORT.md is blocked. Use the bug scripts:\n"
        "  python .claude/scripts/read-bug.py <id>                       read your bug\n"
        "  python .claude/scripts/check-bug-status.py <id> --for fix     can it be fixed\n"
        "  python .claude/scripts/update-bug-status.py <id> <status>     In Progress, Fixed or Open"
    )

if payload.get("tool_name") in ("Write", "Edit"):
    path = Path(payload.get("tool_input", {}).get("file_path", ""))
    try:
        relative = (path if path.is_absolute() else ROOT / path).resolve().relative_to(ROOT).as_posix()
    except ValueError:
        relative = None
    if relative is None or not (
        relative in WRITABLE_FILES or (relative.startswith(WRITABLE_DIRS) and not relative.startswith(GENERATED))
    ):
        block(f"The refactor agent may not write {relative or path}. Writable: backend/, frontend/ "
              f"(not generated folders), {', '.join(WRITABLE_FILES)}.")
sys.exit(0)
