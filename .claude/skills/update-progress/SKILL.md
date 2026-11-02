---
name: update-progress
description: Record the step just completed in the project's progress journal. Use at the end of every planning, build or fix step (not for QA, plan-qa or verify steps).
---

# Update Progress

1. Read `.claude/context/PROGRESS_PATTERNS.md` once, and take the entry shape of your step's Type.
2. Append one entry per action, never reading or rewriting the journal:

   ```bash
   python .claude/scripts/context-write.py append PROGRESS.md <<'ENTRY'
   ## [{now}] — Increment — I5 attempt 1
   - agent: implementation
   - increment: I5
   - summary: Reception can register veterinarians with a weekly schedule.
   - deviations: none

   ### Added
   - Added veterinarian registration with a weekly schedule.
   ENTRY
   ```

3. Change sections follow the `update-changelog` rules: one past-tense sentence per change, readable without opening the plan, the bugs or the QA report; omit empty sections.
