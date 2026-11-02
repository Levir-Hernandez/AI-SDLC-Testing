# Harness Architecture

How the Claude Code harness in `.claude/` turns `TASK.md` into a reviewed plan, builds it autonomously with one command, then tests it in a browser and fixes bugs one at a time. This file is for humans; no agent, skill or rule reads it.

## 1. Structure

```
.claude/
├── agents/                 Who: role, boundaries, judgment
│   ├── orchestrator.md     Coordinates the autonomous build; never writes code
│   ├── planner.md          Mode 1 plan, Mode 2 forward validation, Mode 3 escalation
│   ├── plan-reviewer.md    Reviews the plan (never edits it)
│   ├── implementation.md   Builds one increment
│   ├── code-review.md      Reviews one increment's diff (gate, read-only)
│   ├── test-runner.md      Runs build, tests, lint and coverage (gate, read-only)
│   ├── qa.md               Tests in the browser, reports and verifies bugs
│   └── refactor.md         Fixes one bug with a regression test, or removes a repeated finding
├── commands/               Entry points typed in the main session
│   ├── run-orchestrator.md      /run-orchestrator
│   ├── escalate.md              /escalate <id> [history]
│   ├── implement-increment.md   /implement-increment <n>
│   ├── setup-qa-env.md          /setup-qa-env
│   ├── qa-env-healthcheck.md    /qa-env-healthcheck
│   ├── plan-qa.md               /plan-qa [focus]
│   ├── run-qa.md                /run-qa
│   ├── implement-fix.md         /implement-fix BUG-xxx
│   └── verify-fix.md            /verify-fix BUG-xxx
├── context/                The plan, the journal and the logs (table below)
├── hooks/                  Observability: writes context/TOOLS_USED.md
│   ├── task-start.sh, log-tool-use.sh, subagent-start.sh, subagent-stop.sh, task-finish.sh
│   └── tools_used.py       The logic behind the shell wrappers
├── scripts/                Deterministic file access and guards (Python)
│   ├── read-increment.py, check-increment-status.py,
│   │   check-plan-review-status.py, update-increment-status.py   Plan access
│   ├── plan-edit.py            Planner Modes 2 and 3: section-level plan changes
│   ├── read-progress.py, context-write.py   Journal, changelog and BLOCKED.md access; run counters
│   ├── preflight-orchestrator.py, coverage-report.py   /run-orchestrator checks; final 80% gate
│   ├── read-bug.py, check-bug-status.py, update-bug-status.py    Bug access
│   ├── qa-env.py, check-playwright.py, make-gif.py               QA environment and evidence
│   ├── guard-plan-context.py   Hook: blocks direct access to PLAN.md / PLAN_REVIEW.md / PROGRESS.md
│   ├── guard-fix-scope.py      Hook: keeps the refactor agent inside its scope
│   └── guard-orchestrator.py   Hook: limits the orchestrator's shell to scripts and the commit cycle
├── skills/                 How: steps and formats
│   ├── generate-plan/, review-plan/, revise-plan/
│   └── update-progress/, update-changelog/, generate-changelog/
├── rules/                  Path-scoped coding/testing rules (java-*, react-*)
└── settings.json           Permissions, the hooks, and the Playwright MCP server (.mcp.json)
```

| File | Role |
|---|---|
| `TASK.md` | Requirements and acceptance criteria |
| `CLAUDE.md` | Where things are, how to run and test, agent behavior |
| `.claude/context/PLAN.md`, `PLAN_REVIEW.md` | The plan with its changelog table, and its review |
| `.claude/context/PROGRESS.md`, `CHANGELOG.md` | Journal and its summary |
| `.claude/context/PROGRESS_PATTERNS.md` | The entry shapes every agent appends to the journal |
| `.claude/context/BLOCKED.md` | The global pause button; exists only when something is wrong |
| `.claude/context/TOOLS_USED.md`, `.current-task` | Flight recorder of a run; git-ignored |
| `.claude/context/QA_SCENARIOS.md` | QA scenarios mapped to acceptance criteria |
| `.claude/context/BUGS.md`, `QA_REPORT.md`, `evidence/` | Created by a QA pass: bugs, results, screenshots and GIFs |

## 2. Key ideas

- **One plan, vertical increments.** Each increment is backend + frontend + tests.
- **Agents hold judgment, skills hold procedure.**
- **Author and checker are separate.** Planner vs. plan-reviewer; implementation vs. code-review and test-runner; refactor vs. qa.
- **The orchestrator only coordinates.** It has no Write or Edit tool, and a hook limits its shell.
- **Counters live in files.** Retries, escalations and the validation cadence are computed from `PROGRESS.md`, so a halted run resumes where it stopped.
- **Scripted access.** Agents read one increment, one bug or the last journal entries through scripts; hooks block direct access, so big files are never read or rewritten whole.
- **Scripted writes in `.claude/`.** Write and Edit on `.claude/` always prompt, and no allow rule pre-approves them; a script does not, so the run does not stall.
- **QA tests like a user.** The qa agent drives a real browser through Playwright MCP and writes only QA files and evidence.
- **Small fixes.** One bug per `/implement-fix`, root cause plus a regression test.
- **Statuses.** Increments: `pending` → `completed` | `blocked` | `superseded`. Bugs: `Open` → `In Progress` → `Fixed` → `Verified` (back to `Open` if reproduced).

## 3. Flow

```
Plan       generate-plan → review-plan → revise-plan   (repeat until READY)
Build      /run-orchestrator                           (or /implement-increment <n> by hand)
QA         /qa-env-healthcheck → /plan-qa → /run-qa    (bugs into BUGS.md)
Fix        /implement-fix BUG-xxx → /verify-fix BUG-xxx (one bug at a time)
```

### Inside `/run-orchestrator`

```
preflight-orchestrator.py ── BLOCKED → stop
        │ OK
        ▼
┌─► --next ── DONE ─► test-runner (final, 80% line + branch) → generate-changelog → commit → Workflow Complete
│      │ NEXT <id>
│      ▼
│   implementation ◄──── feedback, failures 1–2
│      ▼                      ▲
│   code-review ‖ test-runner ─┤ FAIL
│      │ PASS                  │ 3rd failure, or BLOCKED.md
│      ▼                       ▼
│   commit <id>            stash → /escalate → planner Mode 3 ── split or new approach ──┐
│      ▼                       │ NEEDS HUMAN, or 2 escalations already                   │
│   deviations, or 3 passed?   ▼                                                         │
│      │ yes → planner Mode 2  Workflow Halted (BLOCKED.md stays)                        │
│      ▼                                                                                 │
│   same Medium finding twice? yes → refactor (cleanup run) → test-runner → commit       │
└──────┴─────────────────────────────────────────────────────────────────────────────────┘
```

Every prompt, tool call and finished subagent lands in `TOOLS_USED.md`, tagged with the agent that made the call; Write and Edit rows also list the rules whose paths matched.
