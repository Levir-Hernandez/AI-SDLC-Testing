# Progress Patterns

The entry shapes every agent appends to `PROGRESS.md`. Append with `python .claude/scripts/context-write.py append PROGRESS.md` and `{now}` as the timestamp; read with `read-progress.py`.

```markdown
## [{now}] — <Type> — <Label>
- agent: <agent-name>
- increment: <id or ->
- summary: <short delta>
- <fields of the type>
```

Then, only when something changed for the reader: `### Added`, `### Changed`, `### Fixed`, `### Removed`, one past-tense sentence per item.

| Type | Written by | Label | Extra fields |
|---|---|---|---|
| `Increment` | implementation | `<id> attempt <n>` | `files_created: [...]`, `files_modified: [...]`, `interfaces_created: [...]`, `decisions: [...]`, `deviations: none or [...]`, `tests: X passed / Y failed`, `blockers: none or [...]` |
| `Gate` | orchestrator | `<id> attempt <n>` | `result: PASS or FAIL`, `review: <verdict>`, `tests: X passed / Y failed`, `coverage: <line>, <branch>`, `medium_findings: none or key, key`, `failures: none or [...]` |
| `Validation` | planner Mode 2 | `after <id>` | `trigger: deviations or cadence`, `rewritten: none or [ids]` |
| `Escalation` | planner Mode 3 | `<id>` | `diagnosis: <one line>`, `decision: split or approach`, `resume_at: <id>` |
| `Refactor` | refactor | `<finding key>` | `files_modified: [...]`, `tests: X passed / Y failed` |
| `Plan` | planner Mode 1 | `generated` or `revision <codes>` | `status: <plan status>` |
| `Review` | plan-reviewer | `pass <n>` | `status: <verdict>` |
| `Fix` | refactor | `<BUG-id> <title>` | `status: Fixed or In Progress`, `root_cause`, `solution`, `regression_test`, `tests`, `blockers` |

`read-progress.py` counts on the exact type names and `increment:`, `result:` and `medium_findings:` fields. Keep every value on its one line.
