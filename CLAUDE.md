# CLAUDE.md

## Project Context

This repository contains the Vet Clinic Service described in `TASK.md`.

Stack: Java 21 + Spring Boot 4.1 (Maven Wrapper) in `backend/`, Next.js 16 + React 19 + TypeScript + Tailwind CSS in `frontend/`, H2 in-memory database (schema from JPA entities, data resets on restart). No Docker.

---

## Where to Find Things

| What | Where |
|---|---|
| Requirements, rules, acceptance criteria, scope | `TASK.md` |
| Harness overview (for humans) | `HARNESS_ARCHITECTURE.md` |
| Implementation plan and increment status | `.claude/context/PLAN.md` |
| Latest plan review, verdict and decisions log | `.claude/context/PLAN_REVIEW.md` |
| QA scenarios mapped to acceptance criteria | `.claude/context/QA_SCENARIOS.md` |
| Bugs, their environment, steps, evidence and status | `.claude/context/BUGS.md` |
| Latest QA pass: acceptance and coverage | `.claude/context/QA_REPORT.md` |
| QA evidence (`.png` screenshots and `.gif` only) | `.claude/context/evidence/` |
| Journal and summary of steps | `.claude/context/PROGRESS.md`, `.claude/context/CHANGELOG.md` |
| Journal entry shapes | `.claude/context/PROGRESS_PATTERNS.md` |
| Run semaphore (exists only when something is wrong) | `.claude/context/BLOCKED.md` |
| Flight recorder of a run (git-ignored) | `.claude/context/TOOLS_USED.md` |
| Agents (`orchestrator`, `planner`, `plan-reviewer`, `implementation`, `code-review`, `test-runner`, `qa`, `refactor`) | `.claude/agents/` |
| Commands (`/run-orchestrator`, `/escalate`, `/implement-increment`, `/setup-qa-env`, `/qa-env-healthcheck`, `/plan-qa`, `/run-qa`, `/implement-fix`, `/verify-fix`) | `.claude/commands/` |
| Plan, journal, bug, QA and guard scripts | `.claude/scripts/` |
| Observability hooks | `.claude/hooks/`, wired in `.claude/settings.json` |
| Skills (plan and journal workflows) | `.claude/skills/` |
| MCP servers (Playwright) | `.mcp.json`; set up with `/setup-qa-env`, check with `/qa-env-healthcheck` |
| Path-scoped coding and testing rules | `.claude/rules/` (`java-coder`, `java-tester`, `react-coder`, `react-tester`) |
| Backend code / tests (package root `com.vetclinic`) | `backend/src/main/java/`, `backend/src/test/java/` |
| Backend configuration | `backend/src/main/resources/application.yml`, `backend/pom.xml` |
| Frontend routes / shared components / API client | `frontend/app/`, `frontend/components/`, `frontend/lib/` |
| Frontend configuration | `frontend/package.json`, `frontend/next.config.ts`, `frontend/eslint.config.mjs` |

Skip generated folders when searching: `backend/target/`, `frontend/node_modules/`, `frontend/.next/`, `.claude/context/evidence/`.

---

## Run

| App | Command | URL |
|---|---|---|
| Backend | `cd backend && ./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`) | http://localhost:8080 |
| Frontend | `cd frontend && npm install` (first time), then `npm run dev` | http://localhost:3000 |

- Start the backend first, then the frontend, then open http://localhost:3000.
- The frontend reads the backend URL from `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).
- The backend allows CORS only from the origin in `app.cors.allowed-origins` (`application.yml`).
- Data lives in memory: every backend restart starts from the seeded demo data.

## Access

| Resource | URL |
|---|---|
| Health check | `GET http://localhost:8080/api/health` |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Demo accounts come from the backend seed data; see `TASK.md`, the seeder (`DemoDataSeeder`) and `.claude/context/QA_SCENARIOS.md` for credentials.

## Test and Verify

| Check | Command |
|---|---|
| Backend build + all tests | `cd backend && ./mvnw verify` |
| Single backend test class | `cd backend && ./mvnw test -Dtest=ClassNameTests` |
| Frontend lint | `cd frontend && npm run lint` |
| Frontend production build | `cd frontend && npm run build` |
| Frontend tests (if a test suite exists) | `cd frontend && npm test` |
| Browser QA pass (both apps running) | `/run-qa` |
| Confirm one fixed bug in the browser | `/verify-fix BUG-xxx` |

An increment is done only when the checks above pass and its manual check works with both apps running. A bug is done only when `/verify-fix` sets it `Verified`.

---

## Agent Behavior

- **Understand intent first.** Read `TASK.md`, the relevant plan increment or bug, and the existing code before proposing changes.
- **Prefer the smallest sufficient solution.** Meet the acceptance criteria without speculative features, abstractions, or dependencies.
- **Minimize blast radius.** Change only the files required by the current task, increment or bug. Do not perform drive-by refactors.
- **Respect existing conventions.** Match the established package structure, naming, style, and testing patterns.
- **Make decisions traceable.** State assumptions, trade-offs, deviations, and unresolved questions explicitly, citing the relevant `TASK.md` section or criterion.
- **Do not guess through conflicts.** Stop and report when `TASK.md`, the plan, or existing code disagree.
- **Verify before claiming done.** Run the relevant build and tests and report their actual results. Claims about the UI need browser evidence from the `qa` agent.
- **Never commit secrets** or machine-specific absolute paths.

---

## Planning

From the main session:

1. `generate-plan` (planner) → `.claude/context/PLAN.md`
2. `review-plan` (plan-reviewer) → `.claude/context/PLAN_REVIEW.md`
3. While the verdict is not `READY`: `revise-plan [codes] [-- context]`, then `review-plan` again.

## Autonomous Build

`/run-orchestrator` checks the prerequisites, then the `orchestrator` builds every pending increment: pre-flight → `implementation` → `code-review` + `test-runner` in parallel → gates → one commit per increment. Three failed attempts trigger planner Mode 3 through `/escalate`. Deviations, or every 3 increments, trigger planner Mode 2. It ends with `generate-changelog` and the message `Workflow Complete`.

- Follow it live in `.claude/context/TOOLS_USED.md`. After `Workflow Halted`, read `BLOCKED.md`, run `/escalate <id>`, then `/run-orchestrator` again; it resumes from the plan and the journal.
- Writes to `.claude/` always prompt and no allow rule pre-approves them, so agents in the run change context files only through `.claude/scripts/` (`context-write`, `plan-edit`, `update-increment-status`).
- Start the session with `claude --permission-mode acceptEdits`.

## Implementation

From the main session, one increment at a time (the manual alternative to `/run-orchestrator`):

1. `/implement-increment <n>` (implementation agent) checks that the increment is pending or blocked, its dependencies are completed and the plan review allows it, then implements, tests and records the outcome in `.claude/context/PROGRESS.md`.
2. The agent reads and updates the plan only through `.claude/scripts/` (`read-increment`, `check-increment-status`, `check-plan-review-status`, `update-increment-status`); a hook (`guard-plan-context.py`) blocks direct access to `PLAN.md`, `PLAN_REVIEW.md` and `PROGRESS.md`.
3. Review the diff and test results before running the next increment. `python .claude/scripts/check-increment-status.py --list` shows every increment's status.

## QA, Fix and Verify

From the main session, with both apps running (restart the backend for clean seed data):

| Step | Command | Agent | Writes |
|---|---|---|---|
| 0. Check Playwright MCP, backend and frontend are up (`/setup-qa-env` the first time) | `/qa-env-healthcheck` | main session | Nothing |
| 1. Plan scenarios against the acceptance criteria | `/plan-qa [focus]` | `qa` | `.claude/context/QA_SCENARIOS.md` |
| 2. Test in the browser, record bugs | `/run-qa` | `qa` (Playwright MCP) | `.claude/context/BUGS.md`, `.claude/context/QA_REPORT.md`, `.claude/context/evidence/` |
| 3. Fix one bug with a regression test | `/implement-fix BUG-xxx` | `refactor` | Code, test, `.claude/context/PROGRESS.md`, bug status |
| 4. Try to provoke the bug again | `/verify-fix BUG-xxx` | `qa` (Playwright MCP) | Bug status `Verified` or `Open` |

- Bug status: `Open` → `In Progress` → `Fixed` → `Verified` (back to `Open` if reproduced; `Duplicate of BUG-XXX` closes a repeat).
- Bugs are read and changed only through `read-bug.py`, `check-bug-status.py` and `update-bug-status.py`; `BUGS.md` is never read or rewritten whole.
- `qa` writes only QA files and evidence, never code or the journal.
- `refactor` writes only `backend/`, `frontend/` and the journal; `guard-fix-scope.py` enforces this.
- Review each fix's diff, evidence and `PROGRESS.md` entry before the next bug.
