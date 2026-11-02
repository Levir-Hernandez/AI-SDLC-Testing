# Plan Review

- **Date:** 2026-10-17
- **Review pass:** 5
- **Verdict:** READY
- **Open:** 0 blockers, 0 majors, 3 minors

## Blockers

None.

## Major Findings

None.

## Minor Findings

### m1 - Pet history endpoint mapped in AppointmentController under a different path prefix
- **Status:** open
- **Targets:** `I14` Backend Modify and Tasks, `I7` (`AppointmentController`), `Backend API Summary`
- **Problem:** I14 still puts `GET /api/pets/{id}/history` in `AppointmentController`, whose other mappings all live under `/api/appointments`. `GET /api/pets` itself is in `PetController` (I6). If the controller has a class-level `@RequestMapping("/api/appointments")`, the endpoint ends up at the wrong path. The pass 4 revision added an owner consumer for this endpoint (A9) but did not change where it is mapped. Still unchanged in pass 5.
- **Evidence:** I7 and I14 file descriptions. `java-coder.md` asks for resource-oriented URLs and thin controllers.
- **Suggested fix:** Add a separate `PetHistoryController` in the `appointment` package, or state that `AppointmentController` uses method-level full paths.

### m2 - PetHistoryTests slice cannot prove ordering and scoping
- **Status:** open
- **Targets:** `I14` Backend Create and Automated Tests
- **Problem:** `PetHistoryTests` is labelled "slice". With the services mocked, which is the slice convention in I5-I13, the success case "past and upcoming items ordered" and the rejection "owner other pet → 404" only echo the mock. They do not exercise `historyForPet` or `PetService.requireAccessible`. This is the same pattern that was fixed for `AllowedActionsResponseTests` in pass 5.
- **Evidence:** I14 Automated Tests, compared with the resolved I15 finding. `java-tester.md` expects tests to exercise the behaviour they claim to prove. The ordering and scoping are unit-tested in I3 (`AppointmentQueryServiceTests`), so the risk is low.
- **Suggested fix:** Either reword the slice to assert only the HTTP contract (roles, 404 mapping, `PageResponse` shape), or make it an integration test with real sign-ins.

### m3 - Manual TTL override uses an environment variable that may not bind
- **Status:** open
- **Targets:** `I8` Completion Criteria (Manual)
- **Problem:** The manual expiry check restarts the backend with `APP_BOOKING_RESERVATION_TTL=PT1M`. Under Spring relaxed binding, underscores in environment variables map to `.`, so this name targets `app.booking.reservation.ttl`, not `app.booking.reservation-ttl`. The documented env form is `APP_BOOKING_RESERVATIONTTL`. If the override silently fails, the manual check waits 10 minutes or reports a false failure.
- **Evidence:** I3 defines `app.booking.reservation-ttl` in `application.yml`. CLAUDE.md requires every increment's manual check to work.
- **Suggested fix:** Use `APP_BOOKING_RESERVATIONTTL=PT1M`, or `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.booking.reservation-ttl=PT1M`.

## Resolved

### Veterinarian list promises a date default that no parameter supports yet
- Verified in pass 5. The I10 Files and Tasks now say the vet list has a status filter only, sorted by `startAt` ascending, with no date filter in I10.

### AllowedActionsResponseTests described as a slice with a mocked repository
- Verified in pass 5. The I15 Tasks and Automated Tests now describe an integration test (`@SpringBootTest` + MockMvc + `TestJwt`, data inserted through the real repositories), so `AppointmentQueryService` and `AppointmentLifecycle` run unmocked.

### Owner history endpoint has no frontend consumer
- Verified in pass 4. Assumption A9 records the user decision. The Frontend Views Summary and I14 add `/owner/pets/[id]/history`, which calls `GET /api/pets/{id}/history` and shows not-found for another owner's pet. The backend roles for the endpoint match this view.

### Status filter parameter binding is never assigned to an increment
- Verified in pass 3. I10 binds the comma-separated `status` parameter into `AppointmentFilter.statuses`, with slice tests for valid and unknown values. The Backend API Summary now lists every query parameter and the increment that adds it.

### Reschedule page has no receptionist guard
- Verified in pass 3. The I12 Files and Tasks have `appointments/[id]/reschedule/page.tsx` call `requireRole("RECEPTIONIST")` server-side and redirect other roles.

### CancelRequest validation never defined but I9 expects VALIDATION_FAILED
- Verified in pass 3. I3 declares `CancelRequest(@NotBlank @Size(max=500) reason)`. I9 Expected Interfaces and the `CancelDialog` task both use `VALIDATION_FAILED` for field `reason`, and `REASON_REQUIRED` remains as the service-level guard.

### Increment order does not establish domain and use cases before vertical slices
- Verified in pass 2. I1 now creates all entities and the lifecycle, I2 the registration services and I3 all appointment and payment services. I4-I16 are vertical slices over those services, and every Depends on points backwards.

### Appointment response and filters use debt before payments exist
- Verified in pass 2. `Payment` and `PaymentRepository` now exist in I1, and `debt` is computed once in I3 (`AppointmentQueryService.toResponse`). I13 only binds the `?debt=true` parameter, so nothing refers forward.

## Decisions Log

- 2026-10-17, m2: User decided to keep the receptionist history screen unchanged and also let owners view their own pets' history through a History link on `/owner/pets` opening `/owner/pets/[id]/history` (`GET /api/pets/{id}/history`, other owner's pet → 404). Recorded as plan assumption A9.
