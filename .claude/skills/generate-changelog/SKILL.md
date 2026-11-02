---
name: generate-changelog
description: Write the final CHANGELOG entry that summarises a completed orchestrator run. Use once, after the last increment passes its gates.
---

# Generate Changelog

1. Run `python .claude/scripts/read-progress.py --last 0` and `git log --oneline`. Read `.claude/context/CHANGELOG.md`.
2. Keep what this run built: `Increment` entries whose gate passed, plus `Refactor` and `Escalation` entries. Ignore failed attempts.
3. Promote `[Unreleased]` and those entries into one dated section at the top, under `# Changelog`, in [Keep a Changelog](https://keepachangelog.com) form:

```markdown
# Changelog

## [<YYYY-MM-DD>] — Orchestrated build

### Added
- Added online booking, where an owner reserves a free slot and confirms it before the reservation expires.

### Changed
- Split the appointment workflow into smaller steps after repeated failures.

### Fixed
- Fixed the second booking of a slot, which is now rejected with fresh availability.
```

4. Save the whole file with `python .claude/scripts/context-write.py write CHANGELOG.md`, keeping earlier sections unchanged.

- One sentence per change, past tense, describing behavior a user of the app could observe; readable without opening the plan.
- Describe only what the code really does. Merge duplicates and drop anything a later entry undid.
- No status, retry counts or verdict lines; those belong in `PROGRESS.md`.
