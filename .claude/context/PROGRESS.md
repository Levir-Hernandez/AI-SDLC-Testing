# Progress

## [2026-11-01] — Increment — I1 attempt 1
- agent: implementation
- increment: I1
- summary: Domain entities, repositories, schedule validator and the centralized appointment lifecycle exist with unit and JPA slice tests.
- deviations: none

### Added
- Added the domain entities, enums and repositories for owners, pets, veterinarians, services, appointments, payments and user accounts.
- Added the appointment lifecycle enforcing the full transition table, and the schedule validator.
- Added jacoco coverage reporting to the backend build.

## [2026-11-01 23:36] — Gate — I1 attempt 1
- agent: orchestrator
- increment: I1
- summary: Review PASS, tests PASS (first build hit a transient Next.js advisory notice, rerun passed)
- result: PASS
- review: PASS
- tests: 104 passed / 0 failed
- coverage: 76.8% lines, 95.5% branches
- medium_findings: observations-unguarded-setter, money-non-negative-unenforced
- failures: none

## [2026-11-01] — Increment — I2 attempt 1
- agent: implementation
- increment: I2
- summary: Registration application services (accounts, catalog, veterinarians, owners, pets) with owner data scoping and unit tests.
- deviations: Added OwnerRepository.existsByPhone/search and PetRepository.findByOwnerId (page and list) queries needed by the services; CreateOwnerRequest phone pattern chosen as optional + and 7-15 digits (TASK.md gives no format). I1 Medium findings (observations setter guard, non-negative money) not addressed; not touched by I2.

### Added
- Added services to register veterinarians with a validated schedule and login account.
- Added owner registration with login account and optional initial pets, rejecting duplicate phones and breed/species mismatches.
- Added pet listing and access rules so owners only reach their own pets.
- Added a service catalog listing and a BCrypt password encoder.

## [2026-11-01 23:42] — Gate — I2 attempt 1
- agent: orchestrator
- increment: I2
- summary: Review PASS, tests PASS
- result: PASS
- review: PASS
- tests: 119 passed / 0 failed
- coverage: 86.9% lines, 87.8% branches
- medium_findings: null-password-accepted, n-plus-one-owner-pets
- failures: none
