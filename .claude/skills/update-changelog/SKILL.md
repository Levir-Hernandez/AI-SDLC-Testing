---
name: update-changelog
description: Summarize recent progress into the project changelog. Use right after updating progress.
---

# Update Changelog

1. Run `python .claude/scripts/read-progress.py --last 0` and read `.claude/context/CHANGELOG.md`; if the changelog is missing, start it with `# Changelog`.
2. Take the PROGRESS entries not yet reflected in the changelog.
3. Collect their **Added / Changed / Fixed / Removed** items ([Keep a Changelog](https://keepachangelog.com) conventions). Merge duplicates and drop items a later entry undid.
4. Merge them into the `[Unreleased]` section and update its date. Omit empty categories. Save the whole file with `python .claude/scripts/context-write.py write CHANGELOG.md`.

```markdown
# Changelog

## [Unreleased] — YYYY-MM-DD

### Added
- Created an implementation plan with 18 ordered increments covering authentication, owners and pets, appointments and payments.

### Changed
- Reordered the payment increments so they come after the appointment lifecycle.

### Fixed
- Added the missing rejection tests for cancelling without a reason.
```

- One sentence per change, past tense, readable without opening the plan.
- No status or verdict lines; those belong in `PROGRESS.md`.
- Not used inside an orchestrator run, which ends with `generate-changelog`.
