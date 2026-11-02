---
name: review-plan
description: Independently review the implementation plan against the task requirements and give a verdict with findings. Use after the plan is generated or revised.
context: fork
agent: plan-reviewer
argument-hint: "[approach: structural direction for the plan review]"
allowed-tools: Read, Glob, Grep, Write, Skill
---

# Review Plan

Review the implementation plan as an independent reviewer. You judge the plan; you never fix it. The only file you write is `.claude/context/PLAN_REVIEW.md` (plus whatever `update-progress` and `update-changelog` write).

## Inputs

| File | Use |
|---|---|
| `TASK.md` | Source of truth: flows, business rules, acceptance criteria, state diagram, Out of Scope, Technology Constraints |
| `.claude/rules/*` | Project conventions the plan must respect |
| `.claude/context/PLAN.md` | The plan under review |
| `.claude/context/PLAN_REVIEW.md` | Previous review, if any; carries finding history |

## Approach

Always review the entire plan for consistency using the guidelines in **Steps > Check the plan**.

If an approach is provided as an argument, treat it as a structural direction for the review, not as context or a response to an existing finding.

## Steps

1. **Load inputs.** Read `TASK.md`, every file in `.claude/rules/`, and `.claude/context/PLAN.md`.
   - `.claude/context/PLAN.md` missing or empty then stop and reply only: `Run generate-plan first`.
   - `TASK.md` missing then stop and reply only: `TASK.md not found`.
2. **Load history.** If `.claude/context/PLAN_REVIEW.md` exists:
   - Set this pass number to the previous **Review pass** + 1; otherwise this is pass 1.
   - Match previous findings to the current plan by **title**, not code (codes are renumbered each pass).
   - For each finding marked `fixed`: verify the change in the plan. Fixed correctly then move it to **Resolved**. Not fixed then set it back to `open` and add one line to **Problem** saying why.
   - For each `needs context` finding: if `TASK.md` or the plan now answers the question, re-evaluate it; otherwise keep it as is.
3. **Check the plan.** Record each problem as a finding. Every finding must point to a specific plan location and, where relevant, a specific `TASK.md` section or criterion.
   - **Coverage:** every flow, business rule and acceptance criterion in `TASK.md` appears in at least one increment's **Covers**. Nothing listed in Out of Scope is planned.
   - **Order:** each **Depends on** names only I0 or lower-numbered increments. Nothing (endpoint, entity, view, role) is used before the increment that builds it.
   - **Size:** at most 20 increments. Each is one vertical slice, testable on its own, and leaves both apps building and running.
   - **Consistency:** the Frontend Views Summary uses only endpoints, fields and roles defined in the Backend API Summary. Lifecycle states and transitions match the `TASK.md` state diagram exactly - no missing, extra or renamed states.
   - **Constraints:** Technology Constraints in `TASK.md` and every rule in `.claude/rules/*` are respected.
   - **Testability:** each increment's **Tests** names at least one success case and one rejection case. **Verify** has concrete, runnable commands and one observable manual check.
   - **Assumptions:** no assumption silently narrows, widens or changes a requirement. An assumption that needs a human decision becomes a `needs context` finding.
4. **Assign severity** (see Rules), number the findings, and compute the verdict from the table.
5. **Write** `.claude/context/PLAN_REVIEW.md` in the format below, replacing the previous file.
6. **Run** `update-progress`, then `update-changelog`.
7. **Reply** as described at the end.

## Format

```markdown
# Plan Review

- **Date:** YYYY-MM-DD
- **Review pass:** <n>
- **Verdict:** READY | READY WITH MINOR CHANGES | REVISE BEFORE IMPLEMENTATION
- **Open:** <b> blockers, <M> majors, <m> minors

## Blockers

### B1 - <short title>
- **Status:** open | fixed | needs context: <question>
- **Targets:** plan locations, e.g. `I4`, `Backend API Summary`, `Frontend Views Summary`
- **Problem:** what is wrong, in one or two lines
- **Evidence:** the `TASK.md` section, rule or criterion it conflicts with, and how
- **Suggested fix:** the concrete change to make in the plan

## Major Findings

### M1 - <short title>
(same fields)

## Minor Findings

### m1 - <short title>
(same fields)

## Decisions Log

### YYYY-MM-DD HH:MM [codes]
context provided, exactly as given
```

## Rules

- **Read-only on the plan.** Never edit `.claude/context/PLAN.md` or `TASK.md`. Suggested fixes go in the review; `revise-plan` applies them.
- **Codes:** `B` blockers, `M` majors, `m` minors, numbered from 1 in order within each section and renumbered on every pass. Titles stay identical across passes so findings can be tracked; `revise-plan` refers to findings by the codes of the current pass.
- **Severity:**
  - **Blocker** - the plan cannot be implemented correctly as written (missing acceptance criterion, broken dependency order, contradicts the state diagram).
  - **Major** - likely rework or a failed acceptance criterion (weak tests, inconsistent API/view contract, oversized increment).
  - **Minor** - clarity or low-risk issues.
- **Status:** `open`; `fixed` (set by the planner, awaiting your re-check); or `needs context: <question>`, which counts as open for the verdict. Only you move a finding to **Resolved**.
- **Evidence over opinion:** do not raise a finding you cannot tie to `TASK.md`, a rule, or an internal contradiction in the plan. Style preferences are not findings.
- **Decisions log:** keep a `## Decisions Log` section at the end of `PLAN_REVIEW.md` listing only the context given to `revise-plan` (after `--`). Carry existing lines forward unchanged on every pass; never edit or remove them.
- Write `None.` under any empty section.

| Verdict | Rule |
|---|---|
| REVISE BEFORE IMPLEMENTATION | any open blocker, or more than 2 open majors |
| READY WITH MINOR CHANGES | no open blockers, 1-2 open majors |
| READY | no open blockers or majors (open minors allowed) |

## Reply

Return only:

```
Verdict: <verdict> (pass <n>)
Blockers: B1 <title>; B2 <title> | None
Majors: M1 <title> | None
Minors: m1 <title> | None
Needs context: <code> <question> | None
```