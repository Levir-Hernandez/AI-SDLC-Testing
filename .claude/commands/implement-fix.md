---
description: Fix one reported bug at its root cause and prove it with a regression test
argument-hint: "[bug: ID of the bug to fix, e.g. BUG-001]"
disable-model-invocation: true
context: fork
agent: refactor
---

Fix bug `$ARGUMENTS` from `.claude/context/BUGS.md` using the `refactor` subagent.

## 1. Verify prerequisites

If `$ARGUMENTS` is empty or is not a single bug ID, stop and ask for one.

Run `python .claude/scripts/check-bug-status.py $ARGUMENTS --for fix`. If it prints `BLOCKED:`, stop, reply with that line and modify nothing.

## 2. Fix and validate

1. Read only that bug: `python .claude/scripts/read-bug.py $ARGUMENTS`.
2. Claim it: `python .claude/scripts/update-bug-status.py $ARGUMENTS In Progress`.
3. Locate the root cause by following the Steps to Reproduce through the code: the route in `frontend/app/`, the API client in `frontend/lib/`, then the backend controller and service. State it in one sentence before changing anything.
4. Propose the solution in 2–4 lines: the files to change, and why it is the smallest change that removes the cause. If it needs a decision `TASK.md` does not make, or reaches beyond the bug's flow, set the bug back to `Open` and stop with the question.
5. Write the regression test and run it: it must fail on the current code for the reason in the bug.
6. Fix the root cause. Run the regression test (now passing), then every check: `cd backend && ./mvnw verify`, `cd frontend && npm run lint && npm run build`, and `npm test` if a suite exists.
7. Set `Fixed` only if the regression test passes and every check is green; otherwise leave it `In Progress` and report the blocker.

## 3. Record

Append a `Fix` entry to `.claude/context/PROGRESS.md` with `update-progress`:

1. **Root cause**: one sentence.
2. **Solution**: what changed and why, with the files.
3. **Regression test**: its name and what it asserts, or why there is none.
4. **Tests**: aggregate results per check (passed, failed, skipped).
5. **Blockers**: anything keeping the bug from `Fixed`, or "None".

Then run `update-changelog`.

## 4. Reply

The resulting status, the root cause and solution in one line each, the regression test, the check results, anything **Proposed, not applied**, and `Next: /verify-fix $ARGUMENTS`.
