---
description: Test the acceptance criteria in the browser and report the bugs found
disable-model-invocation: true
context: fork
agent: qa
---

Run a QA pass for every scenario using the `qa` subagent. Write `.claude/context/BUGS.md` and `.claude/context/QA_REPORT.md` with the templates below, and evidence to `.claude/context/evidence/`.

## 1. Verify prerequisites

- Run `python .claude/scripts/qa-env.py`. If it exits 1, stop and reply with its output and the start commands from `CLAUDE.md`. Do not start the apps. Its first three lines are the Environment of every bug found in this pass.
- Call `browser_navigate` to `http://localhost:3000` and `browser_snapshot`. If the Playwright tools are missing or fail, stop and reply: `QA environment not ready; run /qa-env-healthcheck`.
- The pass number is the previous `.claude/context/QA_REPORT.md`'s + 1 (1 if none). Replace the report; git keeps earlier passes.

## 2. Select what to test

- `.claude/context/QA_SCENARIOS.md` exists → every scenario defined there.
- No `.claude/context/QA_SCENARIOS.md` → the default UI criteria below, applied to every route in `frontend/app/` that each seed account reaches. Say so in the Summary, map each failure to the ACs it affects, and mark ACs no criterion reached Untested.

**Default UI criteria**
1. Every route loads without console errors or failed requests.
2. Protected routes redirect to sign-in when signed out; a role cannot open another role's area.
3. Sign-out ends the session; the JWT is not readable by scripts (`document.cookie`, `localStorage`).
4. Required fields and invalid input show a readable message next to the field, and nothing is created.
5. Server errors appear as readable text, never raw codes, stack traces or `[object Object]`.
6. Pressing submit twice does not create duplicates.
7. Lists have an empty state, and loading states resolve.
8. Data refreshes after an action without a manual reload.
9. Status badges match across screens, and only allowed actions are offered.
10. Every control is reachable by role and label; Tab and Enter work.
11. At 375 px wide (`browser_resize`) there is no horizontal scroll or clipped control.
12. Dates, times and money use one format, and totals add up.

## 3. Run

1. Run each scenario (or criterion) step by step against its expected result.
2. On a mismatch, capture evidence, then reproduce once more from a clean state.
   - Not reproduced → flaky, noted under Coverage, no bug.
   - Same root cause as an existing bug (`check-bug-status.py --list`, then `read-bug.py`) → no new bug. If that bug was `Verified`, it regressed: set it `Open` and report it as Regressed.
   - New → `check-bug-status.py --next` for the ID, then append it to `## Bugs` with status `Open`.
3. Keep going while later steps are still meaningful; otherwise mark the dependent ACs Blocked by that bug.
4. For each `Fixed` bug from earlier passes, run its Steps to Reproduce once and say whether it still happens. Do not change its status; `/verify-fix` does that.

## 4. Write

**`.claude/context/BUGS.md`**: create it from this template if missing, otherwise update only the header and append new bugs. Then run `python .claude/scripts/update-bug-status.py --refresh` for the Open counts.

```markdown
# Bug Review

- **Date:** YYYY-MM-DD
- **QA pass:** <n>
- **Commit reviewed:** `<sha>`
- **Open:** <n> (Critical <n>, High <n>, Medium <n>, Low <n>)

## Bugs

### BUG-001: <Short, specific title: what breaks, where>

- **Status:** Open | In Progress | Fixed | Verified | Duplicate of BUG-XXX
- **Severity:** Critical | High | Medium | Low

#### Environment
- **Branch / Commit:** `<branch>` @ `<sha>`
- **OS / Runtime:** <e.g. Ubuntu 24.04, Node 22.3>
- **Config / Flags:** <env vars, feature flags, seed data>

#### Steps to Reproduce
1. ...
2. ...
3. ...

#### Expected Behavior
<What should happen, citing the spec, test, or requirement if possible.>

#### Actual Behavior
<What happens instead: exact error text, wrong value, crash.>

#### Evidence
- `<path/to/screenshot.png>`
- `<path/to/recording.gif>`
```

**`.claude/context/QA_REPORT.md`**: replace it with this template. Every AC appears once: passing, Failing (bug), Blocked (bug) or Untested (reason).

```markdown
# QA Report

- **Date:** YYYY-MM-DD
- **QA pass:** <n>
- **Commit reviewed:** `<branch>` @ `<sha>`
- **Acceptance:** <passing>/<total> passing (<n> failing, <n> blocked, <n> untested)

## Summary
<2–4 sentences: overall state, which criteria aren't passing, what blocks release.>

## Acceptance Criteria
- [x] **AC-1:** <criterion>
- [ ] **AC-2:** <criterion>: **Failing**, see BUG-003
- [ ] **AC-3:** <criterion>: **Blocked** by BUG-002
- [ ] **AC-4:** <criterion>: **Untested**, <reason>

## Coverage
- **Tested:** <areas and flows exercised>
- **Not tested:** <what was skipped and why>
- **Automated tests:** <n> passed, <n> failed, <n> skipped (`<command used>`)

## Bugs Found This Pass
- **BUG-004** (High): <title>, affects AC-2

## Previously Reported Bugs
- **BUG-001:** Verified fixed, AC-1 now passing
- **BUG-002:** Still open, AC-3 blocked
- **BUG-003:** Regressed in `<sha>`, AC-2 failing again
```

Do not update `.claude/context/PROGRESS.md` or `.claude/context/CHANGELOG.md`; the bug review and the QA report are the record of the pass.

## 5. Reply

The Acceptance line, the bugs found this pass with severity, the state of previously reported bugs, and `Next: /implement-fix BUG-xxx` for the most severe Open bug.
