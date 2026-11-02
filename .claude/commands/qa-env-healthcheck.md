---
description: Check that Playwright MCP, the backend and the frontend are up and usable for QA.
disable-model-invocation: true
---

Check that the QA environment is up. Read-only: write no files, do not update the journal, do not start or stop the apps.

1. Run `python .claude/scripts/check-playwright.py` (MCP configuration, permissions, Node.js, Chromium).
2. Run `python .claude/scripts/qa-env.py` (backend `/api/health` and frontend answer).
3. If the `mcp__playwright__browser_*` tools are available, call `browser_navigate` to `http://localhost:3000` (or to `about:blank` if the frontend is down), then `browser_close`. Nothing else: no snapshots, screenshots or GIFs.

Reply with one line per component, `UP` or `DOWN`:

```
Playwright MCP: UP | DOWN (<reason>)
Backend:        UP | DOWN
Frontend:       UP | DOWN
```

Then, for anything `DOWN`, the `Fix:` lines from the scripts in the order to apply them (missing tools → `/mcp`; anything down → `/setup-qa-env`). If all are up: `Next: /plan-qa` (or `/run-qa` if `.claude/context/QA_SCENARIOS.md` exists).
