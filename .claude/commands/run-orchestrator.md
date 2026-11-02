---
description: Build every pending plan increment end to end with the orchestrator, after pre-flight checks
disable-model-invocation: true
context: fork
agent: orchestrator
---

Run the autonomous workflow using the `orchestrator` subagent.

## Pre-flight results

!`python .claude/scripts/preflight-orchestrator.py`

## 1. Verify prerequisites

If the results above end in `PREFLIGHT: BLOCKED`, stop. Reply with every `BLOCKED:` line and do nothing else: no subagent, no commit. Repeat any `WARN:` line in your final reply.

## 2. Run

Follow your Increment Loop from step 1 until it reports `DONE:`, then Finalise. Do not stop to ask between increments.

## 3. Reply

The final message defined by your instructions, starting with `Workflow Complete` or `Workflow Halted`.
