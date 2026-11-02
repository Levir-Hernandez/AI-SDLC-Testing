"""Check, without starting a browser, that Playwright MCP is configured, permitted and installed for QA.

Usage:
  python .claude/scripts/check-playwright.py

Prints one line per check, `PASS`, `WARN` or `FAIL`, with a `Fix:` line for anything not passing, and
ends with `READY` or `NOT READY`. Exit code 1 when any check fails. The live browser checks are done
by /qa-env-healthcheck.
"""
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = "playwright"
REQUIRED_TOOLS = ("browser_navigate", "browser_snapshot", "browser_click", "browser_type",
                  "browser_take_screenshot", "browser_evaluate", "browser_close")
results = []


def report(level, check, detail, fix=None):
    results.append(level)
    print(f"{level} {check}: {detail}")
    if fix and level != "PASS":
        print(f"     Fix: {fix}")


def load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    except (OSError, json.JSONDecodeError) as error:
        report("FAIL", path.name, f"cannot be parsed ({error})", f"Fix the JSON in {path}.")
        return {}


def check_server():
    server = load_json(ROOT / ".mcp.json").get("mcpServers", {}).get(SERVER)
    if not server:
        report("FAIL", ".mcp.json", f"no '{SERVER}' server", "Add a 'playwright' server to .mcp.json running `npx -y @playwright/mcp@<version> --browser chromium --isolated --output-dir .playwright-mcp` (on Windows through `cmd /c`).")
        return
    args = [str(a) for a in server.get("args", [])]
    command = " ".join([server.get("command", ""), *args])
    report("PASS", ".mcp.json", f"'{SERVER}' server: {command}")
    if platform.system() == "Windows" and server.get("command") == "npx":
        report("FAIL", ".mcp.json command", "'npx' cannot be spawned directly on Windows",
               'Use "command": "cmd" with "args": ["/c", "npx", ...].')
    for flag, why in (("--isolated", "each session must start signed out"),
                      ("--output-dir", "MCP snapshots and logs must stay in the git-ignored .playwright-mcp, not in .claude/context/evidence")):
        if flag not in args:
            report("WARN", f".mcp.json {flag}", f"missing; {why}", f"Add {flag} to the server args.")
    if re.search(r"@playwright/mcp@latest\b", command) or re.search(r"@playwright/mcp(\s|$)", command):
        report("WARN", ".mcp.json version", "not pinned, so runs are not reproducible",
               "Replace @latest with the output of `npm view @playwright/mcp version`.")


def check_settings():
    files = [ROOT / ".claude" / "settings.json", ROOT / ".claude" / "settings.local.json",
             Path.home() / ".claude" / "settings.json"]
    allow, deny, enabled, disabled, enable_all = [], [], [], [], False
    for path in files:
        settings = load_json(path)
        permissions = settings.get("permissions", {})
        allow += permissions.get("allow", [])
        deny += permissions.get("deny", [])
        enabled += settings.get("enabledMcpjsonServers", [])
        disabled += settings.get("disabledMcpjsonServers", [])
        enable_all = enable_all or settings.get("enableAllProjectMcpServers", False)

    if SERVER in disabled:
        report("FAIL", "MCP server enabled", f"'{SERVER}' is in disabledMcpjsonServers",
               f"Remove it from disabledMcpjsonServers, or enable it in /mcp.")
    elif SERVER in enabled or enable_all:
        report("PASS", "MCP server enabled", "approved in settings")
    else:
        report("WARN", "MCP server enabled", "not pre-approved; Claude Code asks once per user",
               f'Add "enabledMcpjsonServers": ["{SERVER}"] to .claude/settings.json.')

    prefix = f"mcp__{SERVER}"
    blocked = [t for t in REQUIRED_TOOLS if any(d in (prefix, f"{prefix}__*", f"{prefix}__{t}") for d in deny)]
    if blocked:
        report("FAIL", "tool permissions", f"denied: {', '.join(blocked)}",
               "Remove those entries from permissions.deny.")
    elif any(a in (prefix, f"{prefix}__*") for a in allow):
        report("PASS", "tool permissions", f"'{prefix}' allowed, no required tool denied")
    else:
        report("WARN", "tool permissions", "browser tools are not pre-allowed; every call will prompt",
               f'Add "{prefix}" to permissions.allow in .claude/settings.json.')
    if not any(d == f"{prefix}__browser_install" for d in deny):
        report("WARN", "browser_install", "not denied; the agent could download browsers on its own",
               f'Add "{prefix}__browser_install" to permissions.deny.')


def check_node():
    node, npx = shutil.which("node"), shutil.which("npx")
    if not node or not npx:
        report("FAIL", "Node.js", "node or npx not on PATH", "Install Node.js 18+ and reopen the terminal.")
        return
    version = subprocess.run([node, "--version"], capture_output=True, text=True).stdout.strip()
    major = int(re.sub(r"\D.*", "", version.lstrip("v")) or 0)
    if major < 18:
        report("FAIL", "Node.js", f"{version} is older than 18", "Upgrade Node.js to 18 or newer.")
    else:
        report("PASS", "Node.js", f"{version}, npx found")


def check_browser():
    custom = os.environ.get("PLAYWRIGHT_BROWSERS_PATH")
    if custom:
        cache = Path(custom)
    elif platform.system() == "Windows":
        cache = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "ms-playwright"
    elif platform.system() == "Darwin":
        cache = Path.home() / "Library" / "Caches" / "ms-playwright"
    else:
        cache = Path.home() / ".cache" / "ms-playwright"
    found = sorted(p.name for p in cache.glob("chromium*")) if cache.exists() else []
    if found:
        report("PASS", "Chromium", f"{', '.join(found)} in {cache}")
    else:
        report("FAIL", "Chromium", f"no Playwright Chromium in {cache}",
               "Run `npx -y playwright install chromium` once (browser_install is denied to the agent).")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    for check in (check_server, check_settings, check_node, check_browser):
        check()
    failed, warned = results.count("FAIL"), results.count("WARN")
    print(f"\n{'NOT READY' if failed else 'READY'}: {failed} failed, {warned} warnings")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
