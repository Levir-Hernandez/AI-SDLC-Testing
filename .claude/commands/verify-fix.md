---
description: Try to provoke one fixed bug in the browser and report whether it is solved or still present
argument-hint: "[bug: ID of the fixed bug to verify, e.g. BUG-001]"
disable-model-invocation: true
context: fork
agent: qa
---

Verify bug `$ARGUMENTS` using the `qa` subagent.

## 1. Verify prerequisites

- If `$ARGUMENTS` is empty or is not a single bug ID, stop and ask for one.
- Run `python .claude/scripts/check-bug-status.py $ARGUMENTS --for verify`. If it prints `BLOCKED:`, stop and reply with that line.
- Run `python .claude/scripts/qa-env.py`; if it exits 1, stop and reply with its output.
- Call `browser_navigate` to `http://localhost:3000` and `browser_snapshot`. If the Playwright tools are missing or fail, stop and reply: `QA environment not ready; run /qa-env-healthcheck`.

## 2. Try to provoke it

1. Read the bug with `python .claude/scripts/read-bug.py $ARGUMENTS`. Do not open `.claude/context/BUGS.md` whole.
2. Follow its Steps to Reproduce exactly, from a clean signed-out context, twice. Then try one close variation (another record, role or order of the same steps): a fix that only covers the exact input is not a fix.
3. Check the Expected Behavior at the step that failed, and that the rest of that flow still works.
4. Capture evidence as `.claude/context/evidence/V-BUG-<id>-<step>.png` (full path as the screenshot filename), or a GIF if the bug was temporal.

## 3. Record

- **Solved** (no attempt reproduces it and the Expected Behavior holds): `python .claude/scripts/update-bug-status.py $ARGUMENTS Verified`.
- **Still present** (any attempt reproduces it): `python .claude/scripts/update-bug-status.py $ARGUMENTS Open`.

The bug status is the only record; do not update `.claude/context/PROGRESS.md` or `.claude/context/CHANGELOG.md`.

## 4. Reply

`$ARGUMENTS: solved` or `$ARGUMENTS: still present`, then one line per attempt (steps, result) and the evidence paths. If still present, end with `Next: /implement-fix $ARGUMENTS`.
