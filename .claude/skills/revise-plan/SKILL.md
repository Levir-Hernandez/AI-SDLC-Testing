---
name: revise-plan
description: Fix the issues found in the latest plan review, optionally applying the user's decisions. Use when the review verdict is not READY.
argument-hint: "[codes e.g. B1 M2] [-- context: answers or decisions]"
context: fork
agent: planner
allowed-tools: Read, Glob, Grep, Write, Edit, Skill
---

# Revise Plan

## Arguments

- **Codes** (optional): findings to fix, by current-pass code, e.g. `B1 M2 m3`. Without codes: all open blockers and majors, plus minors when cheap.
- **Context** (optional, after `--`): answers to `needs context` questions or decisions from the user, e.g. `-- owners may cancel only before Scheduled`. Treat it as authoritative alongside `TASK.md`.

## Steps

1. Read `.claude/context/PLAN_REVIEW.md`, `.claude/context/PLAN.md` and `TASK.md`. Missing review then stop: `Run review-plan first`. An unknown code then stop and name it.
2. For each selected finding, apply its **Suggested fix** (or a better one that resolves the same **Problem**) at every location in **Targets**.
   - `needs context` finding: fix it only if the context argument answers it; otherwise leave it untouched.
   - Disagree with a finding: leave it `open` and record why in `PROGRESS.md`.
3. Keep the plan valid under the `generate-plan` rules: at most 20 increments, dependencies only backward, `completed` increments unchanged. Update the Backend API Summary, Frontend Views Summary and every affected **Covers** and **Depends on**. Record new decisions from the context under Assumptions.
4. In `PLAN_REVIEW.md`, change the **Status** of each finding you fixed to `fixed` and `- **Open:** <b> blockers, <M> majors, <m> minors` line. Do not edit other fields, codes or the verdict.
5. Run `update-progress`, then `update-changelog`.

## Reply

```
Fixed: B1 <title>; M2 <title> | None
Left open: <code> <reason> | None
Run review-plan to re-check.
```
