"""PreToolUse hook for the orchestrator: shell only for the harness scripts and the commit cycle.

The agent has no Write or Edit tool; this closes the same door through the shell.
Exit code 2 blocks the tool call and shows stderr to the agent.
"""
import json
import os
import re
import sys
from pathlib import Path

ALLOWED = re.compile(
    r"^(python \.claude/scripts/[\w-]+\.py\b"
    r"|git (status|diff|log|add -A|commit|stash push)\b)"
)

ROOT = Path(os.environ.get("CLAUDE_PROJECT_DIR", Path(__file__).resolve().parents[2])).resolve()


def is_project_cd(part):
    """Models often prefix commands with a cd to the project itself; that one is harmless."""
    match = re.fullmatch(r'cd\s+"?([^"]+)"?', part)
    return bool(match) and Path(match[1]).resolve() == ROOT


payload = json.load(sys.stdin)
command = payload.get("tool_input", {}).get("command", "").strip()

# Only the command is checked, never the text of a heredoc or a commit message.
for part in re.split(r"\s*(?:&&|;)\s*", command.split("<<")[0].split(" -m ")[0]):
    if part and not ALLOWED.match(part) and not is_project_cd(part):
        print(
            f"The orchestrator may not run: {part[:120]}\n"
            "Allowed: python .claude/scripts/*.py and git status/diff/log/add -A/commit/stash push. "
            "Delegate everything else to a subagent.",
            file=sys.stderr,
        )
        sys.exit(2)
sys.exit(0)
