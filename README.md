## Solution Summary

`/run-orchestrator` wraps the manual flow from the earlier modules into one command that builds every pending increment. Every earlier agent and rule stays; the new piece is the coordination layer. The structure and flow are in `HARNESS_ARCHITECTURE.md`.

Build Loop: for each pending increment, pre-flight → `implementation` → `code-review` + `test-runner` in parallel → gates → one commit. Three failed attempts trigger planner Mode 3 through `/escalate`. Deviations, or every 3 increments, trigger planner Mode 2. The run ends with `generate-changelog` and `Workflow Complete`.

## What changed

| Task item | Where |
|---|---|
| 1. Orchestrator agent | `.claude/agents/orchestrator.md`, with `guard-orchestrator.py` and `check-increment-status.py --next` |
| Gate agents the orchestrator needs | `.claude/agents/code-review.md`, `.claude/agents/test-runner.md` |
| 2. Planner Modes 2 and 3, plan changelog table | `.claude/agents/planner.md`, `plan-edit.py`, `## Plan Changelog` in `PLAN.md`, `/escalate` |
| 3. Observability hooks (the four required, plus `SubagentStart` for the `(start)` rows the slides show) | `.claude/hooks/`, `hooks` in `.claude/settings.json`, `.gitignore` |
| 4. `/run-orchestrator` | `.claude/commands/run-orchestrator.md`, `preflight-orchestrator.py` |
| Shared language between agents | `.claude/context/PROGRESS_PATTERNS.md`, `BLOCKED.md`, `read-progress.py`, `context-write.py` |
| Finalise | `.claude/skills/generate-changelog/`, `coverage-report.py` |

Clean-up for the run: `context/` moved to `.claude/context/`, as the assignment names it. Every increment in `PLAN.md` was reset to `pending`, the journal was emptied, and the old bugs, QA report and evidence were removed.

Plan changes: I1 now adds JaCoCo, since nothing measured the required 80% coverage (line and branch, enforced at the final gate). A `Lessons From The Previous Build` section carries the three Module 3 bugs, because the plan alone would rebuild them. These two edits came after the plan review's `READY` verdict and have not been reviewed.

Optional extensions applied:
- **Cost control:** `code-review` and `test-runner` run on haiku; orchestrator, planner, implementation and refactor run on sonnet. Prompt caching is on by default in Claude Code.
- **Context trimming:** the orchestrator passes the increment and the last 3 PROGRESS entries inline, and `guard-plan-context.py` now also blocks direct reads of `PROGRESS.md`.
- **Automatic refactor:** the same Medium finding on 2 consecutive increments triggers a `refactor` cleanup run on a green baseline.
- **Plan quality signal:** `read-progress.py --stats` adds a warning to `Workflow Complete` when more than 40% of increments needed retries.

## How to run it

The scaffolding and the harness must be committed first, on a feature branch. To start from the original repository, with the code back at the skeleton (commit `314286f`, Increment 0):

```bash
git switch -c module-4-orchestrator
git rm -rq backend frontend context .claude
git checkout 314286f -- backend frontend
# copy this harness into the repository root, then:
git add -A && git commit -m "Module 4 harness on a clean skeleton"
claude --permission-mode acceptEdits
# accept the trust dialog if asked: an untrusted folder ignores the allow rules. Inside the session:
/run-orchestrator
```

Watch `.claude/context/TOOLS_USED.md` while it runs.

## Results of the run

> Fill in after running. Numbers come from `python .claude/scripts/read-progress.py --stats`, `/cost` and `TOOLS_USED.md`.

| Measure | Result |
|---|---|
| Increments completed autonomously (first try) | |
| Increments that required retries | |
| Mode 3 escalations | |
| Forward validations (Mode 2) | |
| Refactor cleanup runs | |
| Rough cost of the run, against the Module 2 manual flow | |
| Optimisation that helped most | |

Verification after `Workflow Complete`:
- [ ] Full test suite passes with ≥ 80% line and branch coverage (`./mvnw verify`, `coverage-report.py --min 80`).
- [ ] `./mvnw spring-boot:run` and `npm run dev` start the apps on a clean checkout.
- [ ] Behaviour matches `TASK.md`, including the plan's Manual checks, which the gates do not run.
- [ ] The `CHANGELOG.md` entry accurately summarises what was built.

## Main findings and challenges

Design findings, before the run:

- **`.claude/context/` works against autonomy.** Writes to `.claude/` always prompt, and allow rules cannot pre-approve them. A run writing there with Write or Edit would stop at the first journal entry. All routine writes therefore go through scripts. Planning and QA, which run with a person present, still write directly and prompt.
- **Retries are ambiguous in the material.** The assignment says "maximum 3 retries", but the slides show one attempt plus two retries and the assignment also says "fails three times". I implemented 3 failed attempts, then Mode 3.
- **The breaker needs its own breaker.** A split increment can fail again, so a second escalation of the same increment family is allowed and the third attempt halts the run.
- **Manual checks are not automated.** The plan's Manual section needs both apps and a browser, so the gates skip it. That remains a human or `/run-qa` step.

- **Trust is a hard prerequisite.** In an untrusted folder Claude Code ignores the allow rules and the agents' frontmatter hooks, so the guards silently do not run. Accept the trust dialog once before the run.
- **Long paths lose token counts.** Windows cannot open a subagent transcript past 260 characters, and the row then shows `—`.

Harness smoke test, on a throwaway two-increment project with a stub build (no real backend or frontend):

| Behavior | Result |
|---|---|
| `/run-orchestrator` forks the orchestrator, which spawns and waits for subagents | Confirmed |
| `code-review` + `test-runner` issued in one message, overlapping in time | Confirmed |
| Gate recorded, status updated, one commit per increment | Confirmed (I1) |
| Retry with feedback, `BLOCKED.md`, `/escalate`, planner Mode 3, `NEEDS HUMAN`, `Workflow Halted` | Confirmed (I2) |
| The four hooks, rule tagging and per-agent token rows in `TOOLS_USED.md` | Confirmed |
| `plan-edit.py` split and rewire, retry and escalation counters, 40% warning, guards | Confirmed by calling the scripts directly |
| `Workflow Complete`, `generate-changelog`, planner Mode 2, a split decided by the planner, refactor cleanup run | Not exercised |
| The `SubagentStart` row, the final line + branch gate and the post-refactor review, added after the smoke test | Not exercised in a live run (`SubagentStart` row and coverage script checked directly) |
| Agent frontmatter guards and `settings.json` allow rules inside a live session | Not exercised (the test folder was untrusted) |

Findings from it: haiku invented an absolute path and reported a blocked review as `FAIL`, so the gate agents now must use relative paths and answer `ERROR` when they cannot run. The reviewer also rejected a marker comment added only to satisfy the build, and the planner refused to re-scope around it, which is the right outcome.

Run findings:

> Fill in after running: where the autonomous flow surprised you or needed intervention.
