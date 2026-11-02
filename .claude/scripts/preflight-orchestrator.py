"""Pre-flight checks for /run-orchestrator. Always exits 0 so command `!` injection shows the reason.

Usage:
  python .claude/scripts/preflight-orchestrator.py

Prints one `OK:`, `WARN:` or `BLOCKED:` line per check, then `PREFLIGHT: OK` or `PREFLIGHT: BLOCKED`.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTEXT = ROOT / ".claude" / "context"
SCAFFOLD = ("TASK.md", "backend/pom.xml", "frontend/package.json")
HOOKS = ("task-start.sh", "log-tool-use.sh", "subagent-stop.sh", "task-finish.sh")
STARTABLE = ("pending", "blocked")
DEFAULT_BRANCHES = ("main", "master")


def git(*args):
    done = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    return done.returncode, done.stdout.strip()


def check_plan():
    plan = CONTEXT / "PLAN.md"
    if not plan.exists():
        return "BLOCKED", "PLAN.md does not exist; run generate-plan first."
    statuses = re.findall(
        r"^### (I\d+[a-z]?) — (?:(?!^### ).)*?^- \*\*Status:\*\* `([a-z-]+)`",
        plan.read_text(encoding="utf-8"),
        re.MULTILINE | re.DOTALL,
    )
    pending = [key for key, status in statuses if status in STARTABLE]
    if not pending:
        return "BLOCKED", "PLAN.md has no pending increments."
    return "OK", f"PLAN.md has {len(pending)} pending increments: {', '.join(pending)}."


def check_review():
    review = CONTEXT / "PLAN_REVIEW.md"
    verdict = re.search(r"\*\*Verdict:\*\* (.*)", review.read_text(encoding="utf-8")) if review.exists() else None
    if verdict and verdict[1].strip().upper() in ("READY", "READY WITH MINOR CHANGES"):
        return "OK", f"Plan review verdict: {verdict[1].strip()}."
    return "BLOCKED", "The plan review must exist with verdict READY or READY WITH MINOR CHANGES."


def check_missing(paths, base, label):
    missing = [p for p in paths if not (base / p).exists()]
    return ("BLOCKED", f"{label} missing: {', '.join(missing)}.") if missing else ("OK", f"{label} present.")


def check_git():
    if git("rev-parse", "--verify", "HEAD")[0] != 0:
        return [("BLOCKED", "Repository not initialised or without a first commit.")]
    report = []
    identity = all(git("config", key)[1] for key in ("user.name", "user.email"))
    report.append(("OK", "Git identity configured.") if identity
                  else ("BLOCKED", "Set git user.name and user.email; the orchestrator commits per increment."))
    report.append(("BLOCKED", "Working tree not clean; commit the scaffolding and harness first.")
                  if git("status", "--porcelain")[1] else ("OK", "Working tree clean."))
    report.append(("OK", "TOOLS_USED.md is git-ignored.") if git("check-ignore", ".claude/context/TOOLS_USED.md")[0] == 0
                  else ("BLOCKED", "Add .claude/context/TOOLS_USED.md to .gitignore."))
    branch = git("branch", "--show-current")[1]
    report.append(("WARN", f"On '{branch}'; the deliverable is an MR, so prefer a feature branch.")
                  if branch in DEFAULT_BRANCHES else ("OK", f"On branch '{branch}'."))
    return report


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    report = [
        check_plan(),
        check_review(),
        ("BLOCKED", "BLOCKED.md exists; read it and resolve it with /escalate.")
        if (CONTEXT / "BLOCKED.md").exists() else ("OK", "No BLOCKED.md."),
        check_missing(SCAFFOLD, ROOT, "Scaffolding"),
        check_missing(HOOKS, ROOT / ".claude" / "hooks", "Hooks"),
        *check_git(),
    ]
    for level, message in report:
        print(f"{level}: {message}")
    print("PREFLIGHT: " + ("BLOCKED" if any(level == "BLOCKED" for level, _ in report) else "OK"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
