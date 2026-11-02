---
description: Implement one approved plan increment, verify its completion criteria, and record the outcome
argument-hint: "[increment: number of the plan increment to implement, e.g. 1]"
disable-model-invocation: true
context: fork
agent: implementation
---

Implement Increment `$ARGUMENTS` from `.claude/context/PLAN.md` using the `implementation` subagent.

## 1. Verify prerequisites

If `$ARGUMENTS` is empty or is not a single increment number, stop and ask for one.

Run the check scripts and confirm that:

- Increment `$ARGUMENTS` exists in `.claude/context/PLAN.md` and its status is `pending`.
- Every dependency listed by the increment is `completed`. `I0` (the existing skeleton) counts as completed.
- `.claude/context/PLAN_REVIEW.md` exists and its latest verdict is `READY` or `READY WITH MINOR CHANGES`.

If any check fails, stop. Report which prerequisite failed and the exact values found. Do not modify any files.

## 2. Implement and Validate

Implement the increment and verify each completion criterion. Then update the two files below, changing only the parts named here.

**`.claude/context/PLAN.md`**: with `update-increment-status.py`, set Increment `$ARGUMENTS` to `completed` only if every completion criterion was met, all tests pass, and there are no blockers. Otherwise set it to `blocked`.

**`.claude/context/PROGRESS.md`**: append an `Increment` entry through `update-progress`, with what now works, each completion criterion met or not met, material decisions and deviations, how each review finding targeting this increment was handled (fixed, deferred, or rejected, with a reason), test results and blockers.

## 3. Reply

Reply with the resulting status, a one-line summary of the entry written to `PROGRESS.md` and any blockers.