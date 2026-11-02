---
description: Bring the QA environment up (Playwright MCP, backend, frontend), starting whatever is down
disable-model-invocation: true
---

Bring the QA environment up. Start only what is down; never restart something that is up. Do not edit code or config.

1. **Check:** run `python .claude/scripts/check-playwright.py` and `python .claude/scripts/qa-env.py`.
2. **Chromium:** if the check reports it missing, run `npx -y playwright install chromium`.
3. **Backend:** if `qa-env.py` reports it `DOWN`, start it in the background (`run_in_background`) from `backend/`: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).
4. **Frontend:** if `DOWN`, start it in the background from `frontend/`: `npm install` first if `node_modules/` is missing, then `npm run dev`.
5. **Wait:** use Monitor with an until-loop on `python .claude/scripts/qa-env.py` (timeout 3 minutes) until both are up. If one never comes up, read its background output and report the error.
6. **Playwright MCP:** if the `mcp__playwright__browser_*` tools are not available, the server cannot be started from here: ask the user to run `/mcp` and reconnect `playwright` (or restart Claude Code), then rerun `/setup-qa-env`.
7. **Confirm:** finish with `/qa-env-healthcheck`.

The apps keep running in the background after this command ends; restarting the backend resets the seed data.
