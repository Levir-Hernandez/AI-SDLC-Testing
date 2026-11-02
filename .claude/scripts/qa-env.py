"""Print the QA environment (affected version, OS, runtimes) and check that both apps are up. Never starts them.

Usage:
  python .claude/scripts/qa-env.py

  Overrides: QA_BACKEND_URL (default http://localhost:8080), QA_FRONTEND_URL (default http://localhost:3000).
  Exit code 0 when both apps answer, 1 otherwise. Paste the first three lines into the Environment of a bug.
"""
import os
import platform
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = os.environ.get("QA_BACKEND_URL", "http://localhost:8080").rstrip("/")
FRONTEND = os.environ.get("QA_FRONTEND_URL", "http://localhost:3000").rstrip("/")
CHECKS = (
    ("Backend", f"{BACKEND}/api/health", "cd backend && ./mvnw spring-boot:run"),
    ("Frontend", f"{FRONTEND}/", "cd frontend && npm run dev"),
)


def run(*command):
    executable = shutil.which(command[0])
    if not executable:
        return None
    try:
        result = subprocess.run([executable, *command[1:]], cwd=ROOT, capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0:
        return None
    # `java -version` prints to stderr.
    output = (result.stdout or result.stderr).strip()
    return output.splitlines()[0] if output else ""


def version():
    branch = run("git", "rev-parse", "--abbrev-ref", "HEAD")
    sha = run("git", "rev-parse", "--short", "HEAD")
    if not sha:
        return "unknown (not a git repository)"
    dirty = run("git", "status", "--porcelain", "--untracked-files=no")
    return f"`{branch}` @ `{sha}`" + (" + uncommitted changes" if dirty else "")


def probe(url):
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            return response.status, None
    except urllib.error.HTTPError as error:
        # The frontend may redirect to sign-in; any HTTP answer means it is up.
        return error.code, None
    except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
        return None, error


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"- **Branch / Commit:** {version()}")
    node, java = run("node", "--version"), run("java", "-version")
    print(f"- **OS / Runtime:** {platform.system()} {platform.release()} ({platform.version()}), "
          f"Node {node or 'not found'}, {java or 'Java not found'}")
    print(f"- **Config / Flags:** backend {BACKEND}, frontend {FRONTEND}, seed data from `DemoDataSeeder`")
    down = 0
    for name, url, start in CHECKS:
        status, error = probe(url)
        if status is not None and (name != "Backend" or status == 200):
            print(f"{name}: up ({status}) {url}")
        else:
            down += 1
            print(f"{name}: DOWN {url} ({status or error}). Start it with: {start}")
    return 1 if down else 0


if __name__ == "__main__":
    sys.exit(main())
