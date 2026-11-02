---
description: Plan the QA scenarios that check every acceptance criterion in the browser
argument-hint: "[focus: optional QA focus, e.g. functionality, UI/UX, security]"
disable-model-invocation: true
context: fork
agent: qa
---

Plan QA scenarios into `.claude/context/QA_SCENARIOS.md` using the `qa` subagent. Focus: `$ARGUMENTS` (empty means full acceptance). You plan; do not open the browser or run the apps.

## 1. Read

- `TASK.md`: Main User Flows, Business Rules, lifecycle table and Acceptance Criteria. Number the criteria `AC-1`, `AC-2`, ... in the order they appear.
- The routes in `frontend/app/` and the forms and actions in `frontend/components/`, to learn the real labels, fields, validation messages and role areas.
- The backend seed data (`DemoDataSeeder`) for accounts.
- `python .claude/scripts/check-bug-status.py --list`, if `.claude/context/BUGS.md` exists: Open and Fixed bugs deserve a scenario that would catch them again.

## 2. Focus

With a specific focus write scenarios only for it, still list every AC, and mark the ones outside it `Out of focus`. Without one, cover every AC.

## 3. Write

Overwrite `.claude/context/QA_SCENARIOS.md` with this template. Take the commit from the first line of `python .claude/scripts/qa-env.py` (the apps may be down).

```markdown
# QA Scenarios

- **Date:** YYYY-MM-DD
- **Planned against:** `<branch>` @ `<sha>`
- **Focus:** <focus, or Full acceptance>
- **Scenarios:** <n> (High <n>, Medium <n>, Low <n>)

## Accounts
- **<Role>:** `<user>` / `<password>` (<seed data | created in SC-xx>)

## Acceptance Criteria
- **AC-1:** <criterion>: SC-01, SC-02
- **AC-2:** <criterion>: **Not in browser**, <reason>
- **AC-3:** <criterion>: **Out of focus**

## Scenarios

### SC-01: <What the user does and the outcome checked>

- **Covers:** AC-1, AC-2
- **Priority:** High | Medium | Low
- **Role / Route:** <role> on `<path>`
- **Preconditions:** <signed out | data from SC-xx>

#### Steps
1. <action> → <expected result>
2. ...

#### Evidence
- <Screenshot at step n | GIF over steps n–m>
```

## 4. Rules

- **One scenario per user goal**, 3–10 steps. Name the control the user sees (button text, field label), not CSS.
- **Every expected result is checkable in the UI** and traceable to `TASK.md` or a plan assumption (quote the assumption in the step, since the fixer cannot read the plan). If `TASK.md` is silent, keep the step but write the open question after it instead of inventing a rule.
- **Include the negative paths** the rules imply: missing fields, conflicts, forbidden transitions, another role's or owner's data.
- **Chain data explicitly** in Preconditions, and order scenarios so data-creating ones come first.
- **An AC the browser cannot check** (time-based expiry, build checks) is `Not in browser` with the reason.
- **Evidence:** a screenshot for a static outcome; a GIF when the defect would only show between interactions.

## 5. Reply

The number of scenarios, one line per AC (scenarios, Not in browser or Out of focus), and `Next: /run-qa`.
