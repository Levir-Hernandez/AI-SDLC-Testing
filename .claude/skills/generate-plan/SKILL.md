---
name: generate-plan
description: Create or rebuild the implementation plan from the task requirements. Use to start planning or to replan after the requirements change.
context: fork
agent: planner
---

# Generate Plan

## Steps

1. Read `TASK.md` and `CLAUDE.md`, then any `.claude/rules/*`. If `TASK.md` or `CLAUDE.md` is missing, stop and name the missing file.
2. Inspect `backend/` and `frontend/`, skipping dependencies, generated files and build artifacts (`node_modules/`, `.next/`, `target/`).
3. If `.claude/context/PLAN.md` exists, read it and note every `completed` increment. If `.claude/context/PLAN_REVIEW.md` exists, read it and address each finding in the new plan.
4. Write `.claude/context/PLAN.md` in the format below, following the rules.
5. Run the `update-progress` skill, then the `update-changelog` skill.

## Format

```markdown
# Implementation Plan — <system name>

## Overview
Goal, stack, number of increments and how to run both apps locally, in 3–5 lines.

## Repository Findings
What the skeleton (I0) already provides, and the conventions every increment must follow.

## Architecture Decisions
Short bullets: package and folder layout, auth flow, lifecycle handling, API conventions.

## Backend API Summary
| Method | Path | Roles | Increment |

## Frontend Views Summary
| Component | Route | Roles | Increment |

## Increments

### I<n> — <Short increment title>

- **Status:** `pending` | `completed` | `blocked` | `superseded`
- **Goal:** <One line describing the observable outcome.>
- **Covers:** <`TASK.md` references: flows, business rules, acceptance criteria>
- **Depends on:** <I0 and/or lower-numbered increments>

#### Files

**Backend**
- **Create:**
  - `<path>`: <purpose>
- **Modify:**
  - `<path>`: <what changes>
- **Delete:**
  - `<path>`: <why>

**Frontend**
- **Create:**
  - `<path>`: <purpose>
- **Modify:**
  - `<path>`: <what changes>
- **Delete:**
  - `<path>`: <why>

#### Implementation Tasks
<One numbered item per file. Simple file → one line. Complex file → sub-bullets per method, bean or config (backend), or component, hook or action (frontend).>

**Backend**
1. `<Class>`: <responsibility>
   - `<method / bean / config>`: <behavior, inputs → outputs, rules enforced>
2. `<File>`: <one-line change>

**Frontend**
1. `<Component / module>`: <responsibility>
   - `<function / hook / action>`: <behavior, inputs → outputs>
2. `<File>`: <one-line change>

#### Expected Interfaces and Outputs

**Backend**
- `<METHOD /path>` `<request>` → `<status>` `<response shape>`; `<invalid case>` → `<status>` `<error code>`.
- `<config file>` contains `<specific value>`.

**Frontend**
- `<route>` <condition> → <observable result>.
- `<cookie / storage / UI element>` has `<specific property or value>`.

#### Automated Tests
<One plain sentence: what the tests prove.>

**Backend**
- `<TestClass>` (<unit | slice | integration>, <key setup>)
  - **Success:** <scenario>
  - **Rejections:** <scenario> → <expected error or status>; …

**Frontend**
- `<TestFile>` (<unit | component | e2e>, <key setup>)
  - **Success:** <scenario>
  - **Rejections:** <scenario> → <expected result>; …
<or: "None in this increment; covered by lint, build and manual checks in Completion Criteria.">

#### Completion Criteria
<2–4 sentences: how this increment is verified. Say what the automated checks prove and what the live checks confirm on the running apps.>

**Automated**

Backend:
- `<command>` → <expected result>

Frontend:
- `<command>` → <expected result>

**Manual** (both apps running)
- `<command or browser action>` → <expected result>

## Plan Changelog

| Date | Mode | Increments | Change |
|---|---|---|---|
```

## Rules

- **Scope:** Every flow, business rule and acceptance criterion in `TASK.md` appears in at least one increment's **Covers**. Add nothing that is not in `TASK.md`.
- **Size:** At most 20 increments. Prefer vertical slices; merge trivial work and split oversized or unrelated flows.
- **Order:** Authentication, roles and seed data first; then core records in dependency order; then the central workflow and its lifecycle transitions; then payments, history and search; UI polish and a final acceptance pass last.
- **Dependencies:** An increment depends only on I0 or lower-numbered increments. If it needs later work, move that work earlier and renumber, or re-split so the dependency disappears. Never leave a forward dependency or a placeholder for future work.
- **Stability:** Every increment leaves both apps building and working.
- **Regeneration:** Copy `completed` increments unchanged, keeping their numbers. Renumber, reorder or replan only `pending` and `blocked` increments, and place them after the preserved ones when dependencies allow.
- **Status:** New increments start as `pending`.
- **Plan Changelog:** The last section of the plan. Modes 2 and 3 add one row per change through `plan-edit.py changelog`.
- **Brevity:** Use signatures, paths and one-line sketches; never write full code.
