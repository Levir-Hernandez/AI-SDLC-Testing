"""PreToolUse hook for the orchestrated agents: block direct access to the plan context files.

.claude/context/PLAN.md, PLAN_REVIEW.md and PROGRESS.md are reached only through the scripts,
because these files grow with every increment and re-reading them is the main cost of a run.
Exit code 2 blocks the tool call and shows stderr to the agent.
"""
import json
import sys

PROTECTED = ("PLAN.md", "PLAN_REVIEW.md", "PROGRESS.md")
ALLOWED = (
    "read-increment.py",
    "update-increment-status.py",
    "check-increment-status.py",
    "check-plan-review-status.py",
    "read-progress.py",
    "context-write.py",
)

payload = json.load(sys.stdin)
tool_input = json.dumps(payload.get("tool_input", {}))

if any(name in tool_input for name in PROTECTED) and not any(script in tool_input for script in ALLOWED):
    print(
        "Direct access to PLAN.md, PLAN_REVIEW.md and PROGRESS.md in .claude/context/ is blocked. Use the scripts:\n"
        "  python .claude/scripts/read-increment.py <n>                 read your increment\n"
        "  python .claude/scripts/check-increment-status.py <n>         pending + dependencies check\n"
        "  python .claude/scripts/check-plan-review-status.py <n>       verdict + findings for <n>\n"
        "  python .claude/scripts/update-increment-status.py <n> completed   mark it done\n"
        "  python .claude/scripts/read-progress.py --last <n>           last journal entries\n"
        "  python .claude/scripts/context-write.py append PROGRESS.md   append an entry from stdin",
        file=sys.stderr,
    )
    sys.exit(2)
sys.exit(0)
