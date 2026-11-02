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

## [2026-11-01] — Validation — after I2
- agent: planner
- increment: I2
- summary: Folded open I1/I2 findings into I3 and I4; I5 still matches the code.
- trigger: deviations
- rewritten: [I3, I4]

## [2026-11-01] — Increment — I3 attempt 1
- agent: implementation
- increment: I3
- summary: Appointment and payment use cases implemented as services with unit, scope and concurrency tests.
- files_created: [AvailabilityService, BookingService, AppointmentActionService, AppointmentQueryService, ReservationExpiryJob, SchedulingConfig, appointment dto (SlotResponse, CreateAppointmentRequest, AppointmentResponse, AppointmentFilter, CancelRequest, ObservationsRequest, RescheduleRequest), PaymentService, payment dto (PaymentRequest, PaymentResponse), tests (Availability, Booking, AppointmentAction, AppointmentQuery, AppointmentQueryScope, BookingConcurrencyIntegration, PaymentService, Fixtures)]
- files_modified: [application.yml, AppointmentRepository, AppointmentLifecycle]
- interfaces_created: [AvailabilityService.slots/isBookable, BookingService.book/createScheduled, AppointmentActionService.confirm/cancel/start/finish/close/expireDue/updateObservations/reschedule, AppointmentQueryService.list/get/requireScoped/historyForPet/toResponse, PaymentService.pay]
- decisions: [reschedule and pay check allowedActions before mutating so rejected requests leave status unchanged; list uses JPA Specification with owner pet ids; AppointmentQueryScopeTests (DataJpaTest) added to prove real scoping and debt filter; updateObservations enforces canEditObservations before setObservations; createScheduled rejects negative prices with 400 VALIDATION_FAILED]
- deviations: [AppointmentLifecycle now stores ctx.reason as cancelReason for EXPIRE so expired reservations carry "Reservation expired"; AppointmentRepository extends JpaSpecificationExecutor and gains findByPetId; extra test class AppointmentQueryScopeTests and Fixtures]
- tests: 188 passed / 0 failed
- blockers: none

### Added
- Added availability, booking (walk-in and online), lifecycle action, scoped query and payment services with reservation expiry job.
- Added booking properties reservation-ttl and expiry-sweep to application.yml.
### Changed
- Changed the lifecycle to record the reason when a reservation expires.

## [2026-11-01 23:53] — Gate — I3 attempt 1
- agent: orchestrator
- increment: I3
- summary: Review FAIL (history sort not asserted), tests PASS
- result: FAIL
- review: FAIL
- tests: 188 passed / 0 failed
- coverage: 94.6% lines, 91.6% branches
- medium_findings: n-plus-one-list-mapping, unindexed-lookup, payment-race-duplicate-key
- failures: [AppointmentQueryServiceTests.historyKeepsOrderAndFlagsUpcoming never asserts startAt DESC sort]

## [2026-11-01] — Increment — I3 attempt 2
- agent: implementation
- increment: I3
- summary: Fixed code-review findings from attempt 1; verify passes (189 tests), frontend lint and build pass.
- deviations: none; the allowedActions guard in AppointmentActionService.reschedule was kept because it is not redundant (it rejects before the new appointment is created; the later apply performs the mutation).

### Changed
- Strengthened the history test to assert the startAt descending sort passed to the repository.
- Added an index on the appointment rescheduled-from column.
- Mapped a payment unique-constraint race to 409 ALREADY_PAID, with a unit test.

## [2026-11-01 23:56] — Gate — I3 attempt 2
- agent: orchestrator
- increment: I3
- summary: Review PASS, tests PASS
- result: PASS
- review: PASS
- tests: 189 passed / 0 failed
- coverage: 94.6% lines, 91.6% branches
- medium_findings: none
- failures: none
