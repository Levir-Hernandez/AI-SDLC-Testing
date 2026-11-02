"""Write the flight recorder .claude/context/TOOLS_USED.md from Claude Code hook payloads.

Usage (stdin is the hook JSON):
  python .claude/hooks/tools_used.py <task-start|log-tool-use|subagent-start|subagent-stop|task-finish>

One write per row, in append mode, so parallel subagents do not interleave within a row.
.current-task keeps the prompt between UserPromptSubmit and Stop. Both files are git-ignored.
"""
import json
import re
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOG = ROOT / ".claude" / "context" / "TOOLS_USED.md"
CURRENT = ROOT / ".claude" / "context" / ".current-task"
RULES = ROOT / ".claude" / "rules"
HEADER = "| Date | Tool | Tokens |\n|---|---|---|\n"
PROMPT_LIMIT = 200


def cell(text):
    """Keep a value on one table row."""
    return re.sub(r"\s+", " ", str(text)).replace("|", "\\|").strip()


def append(text):
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with LOG.open("a", encoding="utf-8", newline="\n") as log:
        log.write(text)


def row(tool, tokens="—"):
    append(f"| {datetime.now():%Y-%m-%d %H:%M:%S} | {tool} | {tokens} |\n")


def glob_regex(pattern):
    """Translate a rule `paths` glob (**, *, ?) to a regex."""
    out, i = "", 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out, i = out + "(?:.*/)?", i + 3
        elif pattern.startswith("**", i):
            out, i = out + ".*", i + 2
        elif pattern[i] == "*":
            out, i = out + "[^/]*", i + 1
        elif pattern[i] == "?":
            out, i = out + "[^/]", i + 1
        else:
            out, i = out + re.escape(pattern[i]), i + 1
    return re.compile(out + r"\Z")


def rule_paths(text):
    """Return the `paths` list of a rule file's frontmatter."""
    match = re.match(r"---\r?\n(.*?)\r?\n---", text, re.DOTALL)
    if not match or "paths:" not in match[1]:
        return []
    return re.findall(r'^\s*-\s*"?([^"\r\n]+?)"?\s*$', match[1].split("paths:", 1)[1], re.MULTILINE)


def matched_rules(relative):
    if not RULES.is_dir():
        return []
    return [
        rule.stem
        for rule in sorted(RULES.glob("*.md"))
        if any(glob_regex(p).match(relative) for p in rule_paths(rule.read_text(encoding="utf-8")))
    ]


def relative_path(path):
    try:
        return Path(path).resolve().relative_to(ROOT.resolve()).as_posix()
    except (ValueError, OSError):
        return str(path).replace("\\", "/")


def describe(payload):
    """Return the Tool cell for a tool call, or None when the tool is not logged."""
    name, data = payload.get("tool_name", ""), payload.get("tool_input") or {}
    if name in ("Read", "Write", "Edit"):
        relative = relative_path(data.get("file_path", ""))
        text = f"{name}: {relative}"
        if name != "Read":
            text += f" [rules: {', '.join(matched_rules(relative)) or 'none'}]"
    elif name in ("Bash", "PowerShell"):
        text = f"{name}: {cell(data.get('command', ''))[:120]}"
    elif name in ("Agent", "Task"):
        text = f"{name}: {data.get('subagent_type', 'general-purpose')} — {data.get('description', '')}"
    elif name == "Skill":
        text = f"Skill: {data.get('skill', '')} {data.get('args', '')}"
    else:
        return None
    agent = payload.get("agent_type")
    return cell(f"[{agent}] {text}" if agent else text)


def transcript_tokens(path):
    """Sum input + output tokens of a subagent transcript, or None when not visible."""
    try:
        lines = Path(path).expanduser().read_text(encoding="utf-8").splitlines()
    except (OSError, TypeError, ValueError):
        return None
    usage_by_message = {}
    for line in lines:
        try:
            message = json.loads(line).get("message") or {}
        except ValueError:
            continue
        if isinstance(message, dict) and message.get("usage"):
            usage_by_message[message.get("id", len(usage_by_message))] = message["usage"]
    if not usage_by_message:
        return None
    return sum(
        u.get("input_tokens", 0) + u.get("cache_creation_input_tokens", 0) + u.get("output_tokens", 0)
        for u in usage_by_message.values()
    )


def format_tokens(count):
    if count is None:
        return "—"
    return f"{count / 1000:.1f}k" if count >= 1000 else str(count)


def main(event):
    sys.stdin.reconfigure(encoding="utf-8")
    payload = json.load(sys.stdin)
    if event == "task-start":
        # A finished background subagent arrives as a prompt; it is not a new task.
        if payload.get("prompt", "").lstrip().startswith("<task-notification"):
            return 0
        prompt = cell(payload.get("prompt", ""))[:PROMPT_LIMIT]
        CURRENT.parent.mkdir(parents=True, exist_ok=True)
        CURRENT.write_text(prompt, encoding="utf-8")
        append(f"\n**Task start:** {prompt}\n\n{HEADER}")
    elif event == "log-tool-use":
        if text := describe(payload):
            row(text)
    elif event == "subagent-start":
        row(cell(f"Agent: {payload.get('agent_type', 'unknown')} (start)"))
    elif event == "subagent-stop":
        tokens = transcript_tokens(payload.get("agent_transcript_path"))
        row(cell(f"Agent: {payload.get('agent_type', 'unknown')} (done)"), format_tokens(tokens))
    elif event == "task-finish" and CURRENT.exists():
        # Stop fires once per turn; only the first one closes the open block.
        append(f"\n**Task finished:** {CURRENT.read_text(encoding='utf-8')}\n")
        CURRENT.unlink()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
