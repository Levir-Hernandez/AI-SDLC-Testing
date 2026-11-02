# Implementation Plan — Vet Clinic

## Overview
Build the Vet Clinic Service from `TASK.md`: owners book pet services online against real-time veterinarian availability; reception registers owners, pets and veterinarians, books walk-ins, runs the appointment lifecycle and records payments; veterinarians start and finish their appointments.
Stack: Java 21 + Spring Boot 4.1 (`backend/`, H2 in-memory, JWT) and Next.js 16 + React 19 + TypeScript + Tailwind (`frontend/`). 16 increments (I1–I16) on top of the skeleton I0, in three phases: core domain model (I1), business rules as application services (I2–I3), then vertical slices of API + security + UI over those services (I4–I16).
Run: `cd backend && ./mvnw spring-boot:run` (http://localhost:8080), then `cd frontend && npm install && npm run dev` (http://localhost:3000).

## Repository Findings
- **Backend (I0):** `pom.xml` (Boot 4.1.1, webmvc, data-jpa, security, validation, actuator, springdoc 3.1.0, H2, matching `-test` starters); `VetClinicBackendApplication`; `config/SecurityConfig` permits everything, CSRF off, CORS from `app.cors.allowed-origins` on `/api/**`; `health/HealthController` (`GET /api/health`) with `HealthControllerTests` (`@WebMvcTest` + `@Import(SecurityConfig.class)`); `VetClinicBackendApplicationTests` context load; `application.yml` with H2 `create-drop`, `open-in-view: false`.
- **Frontend (I0):** `app/layout.tsx`, `app/page.tsx` (skeleton home), `app/backend-status.tsx` (client component calling `/api/health`), `lib/api.ts` (`API_BASE_URL` from `NEXT_PUBLIC_API_BASE_URL`, `apiGet`). No test runner; ESLint configured.
- **Conventions every increment follows:** package by feature under `com.vetclinic` (entity, repository, service, controller, DTO records together); thin controllers; DTO `record`s, never entities; constructor injection; `@Transactional` services; injected `Clock`; `BigDecimal` scale 2 for money; errors as `ProblemDetail` with `code` and per-field `errors`; paginated collections; test classes end in `Tests`. Frontend: routes in `app/`, shared UI in `components/`, typed API layer and types in `lib/`; all authenticated backend calls are server-side; `"use client"` only where interactive; Tailwind only.

## Architecture Decisions
- **Phasing (layers before slices):** I1 creates every entity, enum, repository and the `AppointmentLifecycle` table; I2 creates the registration use cases; I3 creates all appointment and payment use cases (`AvailabilityService`, `BookingService`, `AppointmentActionService`, `PaymentService`, `AppointmentQueryService`) in their final shape. I4–I16 add only controllers, security wiring, seed data and screens on top, plus integration tests; they do not reshape services except for additive read filters.
- **Backend packages:** `auth` (users, login, JWT, `CurrentUser`), `config` (security, clock, password encoder, scheduling, OpenAPI), `shared` (errors, paging), `catalog` (services), `veterinarian`, `owner` (owners + pets), `appointment` (entity, lifecycle, availability, booking, actions, queries), `payment`, `seed`.
- **Use-case inputs:** services take a `CurrentUser(userId, role, ownerId, veterinarianId)` record (plain record from I2; `from(Authentication)` factory added in I4), so scoping and role checks live in services and are unit-testable without HTTP.
- **Auth flow:** `POST /api/auth/login` returns an HS256 JWT (`sub` = user id, `role`, `ownerId`/`veterinarianId` claims, 8 h expiry) signed with `app.jwt.secret` (marked dev default, overridable by env `APP_JWT_SECRET`). Validation via `spring-boot-starter-oauth2-resource-server` (`JwtDecoder` from the shared secret); roles `OWNER`, `RECEPTIONIST`, `VETERINARIAN` mapped to `ROLE_*`. Public: `/api/health`, `/api/auth/login`, Swagger/OpenAPI, actuator health. Everything else authenticated; roles via `@PreAuthorize` plus ownership checks in services.
- **Frontend session:** the login server action stores the token in cookie `vc_session` (`HttpOnly`, `Secure`, `SameSite=Lax`, `path=/`, max-age = token expiry); `lib/api.ts` becomes `server-only` and forwards `Authorization: Bearer`. `proxy.ts` redirects to `/login` without the cookie; each role area's `layout.tsx` calls `requireRole()` (server-side `GET /api/auth/me`). Sign-out deletes the cookie.
- **Lifecycle:** one `AppointmentLifecycle` component owns the `TASK.md` transition table (from, action, to, allowed roles, required data); every status change and every `allowedActions` list goes through it. Any other transition → `409 INVALID_TRANSITION`; wrong role → `403`.
- **Availability and concurrency:** slots are generated on a 15-minute grid inside schedule blocks, must start after `now`, fit the service duration, and not overlap appointments in `RESERVED` (not expired), `SCHEDULED` or `IN_PROGRESS`. Booking locks the veterinarian row (`PESSIMISTIC_WRITE`) and re-checks overlap inside the transaction, so the second booking gets `409 SLOT_TAKEN`.
- **Reservation expiry:** `RESERVED` carries `reservedUntil = now + app.booking.reservation-ttl` (default `PT10M`); a `@Scheduled` sweeper cancels expired reservations (reason `Reservation expired`, actor `SYSTEM`); confirm after expiry is rejected as `409 RESERVATION_EXPIRED`.
- **API conventions:** prefix `/api`, plural nouns; `201` + `Location` on create; action endpoints `POST /api/appointments/{id}/<action>`; lists return `PageResponse<T>(content, page, size, totalElements)`.
- **Assumptions (for the reviewer):**
  - A1: Reception creates an owner's sign-in account at registration; the owner signs in with phone + password chosen by reception (`TASK.md` lists no self sign-up).
  - A2: Reception creates a veterinarian's account (username + password) at registration; both seeded veterinarians get accounts so the Veterinarian role is usable. No demo owner is seeded (`TASK.md` seed list); the manual checks register one.
  - A3: Seed: receptionist `reception` / `reception123`; veterinarians `vet.ana` / `vet123` (General medicine, cost 20.00) and `vet.luis` / `vet123` (Surgery, cost 30.00), both Mon–Fri 09:00–13:00 and 14:00–18:00; services General consultation 30 min 40.00, Grooming 60 min 35.00, Vaccination 15 min 25.00, Hospitalization 240 min 150.00.
  - A4: Times are `LocalDateTime` in the server's zone (from the injected `Clock`); slot grid 15 minutes; the date filter defaults to today when omitted.
  - A5: "Debt" = `CLOSED` with no payment; exposed as `debt: true` on the appointment and filterable with `?debt=true`.
  - A6: Rescheduling an `IN_PROGRESS` appointment ("reassign") may change veterinarian and/or time; the new appointment keeps pet, service and total recomputed at the new booking (base price + new vet cost), with `rescheduledFromId` linking it.
  - A7: Browsers accept `Secure` cookies on `http://localhost`, so the `Secure` flag stays on in development.
  - A8: I1–I3 are backend-only and expose no endpoints; their "manual" check is the build and test run, and the apps still start (no visible frontend change). This is the cost of the layers-first ordering requested by review M1.
  - A9: Owners may also view the service history of their own pets (user decision on review m2; `TASK.md` Follow up lists only reception). `/owner/pets` gets a "History" link per pet opening `/owner/pets/[id]/history`, which uses the same `GET /api/pets/{id}/history`; another owner's pet → `404`. The receptionist history screen is unchanged.

## Backend API Summary
| Method | Path | Roles | Increment |
|---|---|---|---|
| POST | `/api/auth/login` | Public | I4 |
| GET | `/api/auth/me` | Any authenticated | I4 |
| GET | `/api/services` | Any authenticated | I5 |
| GET | `/api/veterinarians` | Any authenticated | I5 |
| GET | `/api/veterinarians/{id}` | Any authenticated | I5 |
| POST | `/api/veterinarians` | Receptionist | I5 |
| POST | `/api/owners` | Receptionist | I6 |
| GET | `/api/owners` | Receptionist | I6 |
| GET | `/api/owners/{id}` | Receptionist | I6 |
| POST | `/api/owners/{id}/pets` | Receptionist | I6 |
| GET | `/api/pets` | Owner (own), Receptionist (`?ownerId=`) | I6 |
| GET | `/api/availability` | Owner, Receptionist | I7 |
| POST | `/api/appointments` | Receptionist (walk-in → Scheduled) | I7 |
| POST | `/api/appointments` | Owner (→ Reserved) | I8 |
| GET | `/api/appointments` (query: `page`, `size` I7; `status` I10; `ongoing`, `date`, `veterinarianId` I11; `debt` I13) | Owner (own), Veterinarian (assigned), Receptionist (all) | I7 |
| GET | `/api/appointments/{id}` | Owner (own), Veterinarian (assigned), Receptionist | I7 |
| POST | `/api/appointments/{id}/confirm` | Owner, Receptionist | I8 |
| POST | `/api/appointments/{id}/cancel` | Owner (own Reserved/Scheduled), Receptionist | I9 |
| POST | `/api/appointments/{id}/start` | Veterinarian (assigned), Receptionist | I10 |
| POST | `/api/appointments/{id}/finish` | Veterinarian (assigned), Receptionist | I10 |
| PATCH | `/api/appointments/{id}/observations` | Veterinarian (assigned), Receptionist | I10 |
| POST | `/api/appointments/{id}/reschedule` | Receptionist | I12 |
| POST | `/api/appointments/{id}/payments` | Receptionist | I13 |
| POST | `/api/appointments/{id}/close` | Receptionist | I13 |
| GET | `/api/pets/{id}/history` | Receptionist, Owner (own pet, A9) | I14 |

## Frontend Views Summary
| Component | Route | Roles | Increment |
|---|---|---|---|
| Sign-in page | `/login` | Public | I4 |
| Role home redirect | `/` | Any signed in | I4 |
| Veterinarians list + register form | `/reception/veterinarians` | Receptionist | I5 |
| Owners list + register form | `/reception/owners` | Receptionist | I6 |
| Owner detail + add pet (breed picker) | `/reception/owners/[id]` | Receptionist | I6 |
| My pets | `/owner/pets` | Owner | I6 |
| Walk-in booking | `/reception/book` | Receptionist | I7 |
| Appointment detail with allowed actions | `/appointments/[id]` | All roles (scoped) | I7 |
| Online booking (slot picker, reserve, confirm) | `/owner/book` | Owner | I8 |
| My appointments | `/owner/appointments` | Owner | I9 |
| My assigned appointments | `/vet/appointments` | Veterinarian | I10 |
| Ongoing appointments board | `/reception/appointments` | Receptionist | I11 |
| Reschedule / reassign form | `/appointments/[id]/reschedule` | Receptionist | I12 |
| Debts list | `/reception/debts` | Receptionist | I13 |
| Pet service history | `/reception/pets/[id]/history` | Receptionist | I14 |
| My pet's service history (A9) | `/owner/pets/[id]/history` | Owner (own pets) | I14 |

## Lessons From The Previous Build
Module 3 QA found these defects in the previous build of this plan. Every increment that touches these screens must avoid them:
- A registration form rejected by the server keeps the entered values, and every schedule Day select shows the day that is submitted.
- Registration forms show clear per-field messages (`Enter the owner's name.`), never raw Bean Validation messages; schedule errors name the day in normal case.
- The reception ongoing list offers Confirm alongside Cancel for a Reserved appointment, matching its detail page.

## Increments

### I1 — Core domain model and appointment lifecycle

- **Status:** `completed`
- **Goal:** Every entity, enum and repository of the domain exists with its invariants, and the centralized `AppointmentLifecycle` enforces the full `TASK.md` transition table, all proven by unit and JPA slice tests.
- **Covers:** Entities (Owner, Pet, Veterinarian, Service, Appointment, Payment, Service history as appointments per pet); Pets species/breeds list; Services fixed list; Appointment lifecycle (full transition table, final states, "any other transition is rejected"); rules "schedule blocks on the same weekday do not overlap, each starts before it ends", "breed must belong to species", "one appointment covers one service, one pet, one veterinarian", "prices and veterinarian costs are zero or more", "observations can be edited until closed or cancelled"; Technology "money uses exact decimal types"; acceptance "each status transition works for listed roles, every other rejected" (rules at unit level).
- **Depends on:** I0

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/shared/error/DomainException.java`: base runtime exception (status, code, message); subclasses `NotFoundException`, `ConflictException`, `ForbiddenException`, `BadRequestException`.
  - `backend/src/main/java/com/vetclinic/shared/PageResponse.java`: record `(List<T> content, int page, int size, long totalElements)` + `from(Page<T>)`.
  - `backend/src/main/java/com/vetclinic/config/ClockConfig.java`: `Clock systemDefaultZone()` bean.
  - `backend/src/main/java/com/vetclinic/auth/Role.java`: enum `OWNER`, `RECEPTIONIST`, `VETERINARIAN`.
  - `backend/src/main/java/com/vetclinic/auth/UserAccount.java`: entity (id, unique indexed `username`, `passwordHash`, `role`, `displayName`, nullable `ownerId`, `veterinarianId`).
  - `backend/src/main/java/com/vetclinic/auth/UserAccountRepository.java`: `findByUsername`, `existsByUsername`.
  - `backend/src/main/java/com/vetclinic/catalog/ServiceCode.java`: `GENERAL_CONSULTATION|GROOMING|VACCINATION|HOSPITALIZATION`.
  - `backend/src/main/java/com/vetclinic/catalog/ClinicService.java`: entity (id, unique `code`, name, `durationMinutes` > 0, `basePrice` `BigDecimal(10,2)` ≥ 0).
  - `backend/src/main/java/com/vetclinic/catalog/ClinicServiceRepository.java`: `findByCode`.
  - `backend/src/main/java/com/vetclinic/veterinarian/ScheduleBlock.java`: `@Embeddable` (`DayOfWeek day`, `LocalTime start`, `LocalTime end`).
  - `backend/src/main/java/com/vetclinic/veterinarian/Veterinarian.java`: entity (id, name, specialization, `costPerService` `BigDecimal(10,2)` ≥ 0, `@ElementCollection` schedule).
  - `backend/src/main/java/com/vetclinic/veterinarian/VeterinarianRepository.java`: includes `@Lock(PESSIMISTIC_WRITE) findLockedById`.
  - `backend/src/main/java/com/vetclinic/veterinarian/ScheduleValidator.java`: block rules (pure component).
  - `backend/src/main/java/com/vetclinic/owner/Species.java`: `DOG`, `CAT`.
  - `backend/src/main/java/com/vetclinic/owner/Breed.java`: enum of the ten breeds with their `Species` (`DOG_MIXED`, `CAT_MIXED` for the two "Mixed breed"); `species()`, `belongsTo(Species)`.
  - `backend/src/main/java/com/vetclinic/owner/Owner.java`: entity (id, name, unique indexed `phone`).
  - `backend/src/main/java/com/vetclinic/owner/Pet.java`: entity (id, name, `Species`, `Breed`, `@ManyToOne owner`, index on `owner_id`); constructor rejects breed not of species.
  - `backend/src/main/java/com/vetclinic/owner/OwnerRepository.java`, `PetRepository.java`.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentStatus.java`: `RESERVED, SCHEDULED, IN_PROGRESS, FINISHED, PAID, CLOSED, RESCHEDULED, CANCELLED`; `isFinal()`.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentAction.java`: `CONFIRM, CANCEL, EXPIRE, START, FINISH, RESCHEDULE, PAY, CLOSE, EDIT_OBSERVATIONS`.
  - `backend/src/main/java/com/vetclinic/appointment/Actor.java`: `OWNER, RECEPTIONIST, VETERINARIAN, SYSTEM`; `from(Role)`.
  - `backend/src/main/java/com/vetclinic/appointment/Appointment.java`: entity (pet, service, veterinarian, `startAt`, `endAt`, `status`, `bookedBy` `Actor`, `total` `BigDecimal(10,2)`, `observations` (≤2000), `reservedUntil`, `cancelReason`, `rescheduledFrom` (`@ManyToOne` nullable), `createdAt`); indexes on `(veterinarian_id, start_at)`, `pet_id`, `status`; `status` changed only by package-private `applyStatus` called from the lifecycle.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentRepository.java`: `existsOverlap(vetId, start, end, now, excludeId)` (statuses SCHEDULED/IN_PROGRESS, or RESERVED with `reservedUntil > now`; `excludeId` nullable), `findBlocking(vetId, dayStart, dayEnd, now)`, `findByStatusAndReservedUntilLessThanEqual`, `findByRescheduledFromId`.
  - `backend/src/main/java/com/vetclinic/appointment/TransitionContext.java`: record `(String reason, LocalDateTime now)`.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentLifecycle.java`: the transition table.
  - `backend/src/main/java/com/vetclinic/payment/Payment.java`: entity (id, `@OneToOne` appointment unique, `amount` `BigDecimal(10,2)`, `paidAt`).
  - `backend/src/main/java/com/vetclinic/payment/PaymentRepository.java`: `existsByAppointmentId`.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentLifecycleTests.java`, `backend/src/test/java/com/vetclinic/appointment/AppointmentRepositoryTests.java`, `backend/src/test/java/com/vetclinic/veterinarian/ScheduleValidatorTests.java`, `backend/src/test/java/com/vetclinic/owner/BreedTests.java`.
- **Modify:**
  - `backend/pom.xml`: add `jacoco-maven-plugin` (`prepare-agent`, and `report` bound to `verify`) so `./mvnw verify` writes `target/site/jacoco/jacoco.csv`; the final gate requires 80% line and branch coverage.

**Frontend**
- None.

#### Implementation Tasks

**Backend**
1. `DomainException` family, `PageResponse`, `ClockConfig`: as listed.
2. `UserAccount` / `Role` / `UserAccountRepository`: as listed; `username` unique index.
3. `ClinicService`, `Veterinarian`, `ScheduleBlock`, `Owner`, `Pet`, `Payment` and repositories: as listed; money columns `precision 10, scale 2`.
4. `Breed`: `belongsTo(Species)`; `Pet` constructor throws `BadRequestException("BREED_SPECIES_MISMATCH", "Breed SIAMESE is not a DOG breed")`.
5. `ScheduleValidator`:
   - `validate(List<ScheduleBlock>)`: each `start < end` else `400 INVALID_SCHEDULE` "Block on MONDAY must start before it ends"; same-day blocks sorted and checked for overlap else `400 INVALID_SCHEDULE` "Blocks on MONDAY 09:00–13:00 and 12:00–15:00 overlap"; at least one block.
6. `Appointment` and enums: as listed.
7. `AppointmentLifecycle` (documented table mirroring `TASK.md`):
   - `rules`: `RESERVED→SCHEDULED` CONFIRM {OWNER, RECEPTIONIST} requires `reservedUntil > now`; `RESERVED→CANCELLED` CANCEL {OWNER, RECEPTIONIST} reason required, EXPIRE {SYSTEM}; `SCHEDULED→IN_PROGRESS` START {VETERINARIAN, RECEPTIONIST}; `SCHEDULED→RESCHEDULED` RESCHEDULE {RECEPTIONIST}; `SCHEDULED→CANCELLED` CANCEL {OWNER, RECEPTIONIST} reason; `IN_PROGRESS→FINISHED` FINISH {VETERINARIAN, RECEPTIONIST}; `IN_PROGRESS→RESCHEDULED` RESCHEDULE {RECEPTIONIST}; `IN_PROGRESS→CANCELLED` CANCEL {RECEPTIONIST} reason; `FINISHED→PAID` PAY {RECEPTIONIST}; `FINISHED→CLOSED` CLOSE {RECEPTIONIST} (debt); `PAID→CLOSED` CLOSE {RECEPTIONIST}.
   - `apply(Appointment, AppointmentAction, Actor, TransitionContext)`: no rule for (status, action) → `409 INVALID_TRANSITION` "Cannot START an appointment in status CLOSED"; actor not allowed → `403 ACTION_NOT_ALLOWED`; CANCEL with blank reason → `400 REASON_REQUIRED`; CONFIRM past `reservedUntil` → `409 RESERVATION_EXPIRED`; sets status (and `cancelReason`).
   - `canEditObservations(status)`: false for `CLOSED`, `CANCELLED` (and `RESCHEDULED`, being final).
   - `allowedActions(Appointment, Actor) → Set<AppointmentAction>`: rules whose from = status and actor allowed (+ EDIT_OBSERVATIONS for vet/reception when editable); excludes EXPIRE.

**Frontend**
- None in this increment.

#### Expected Interfaces and Outputs

**Backend**
- No endpoints; H2 schema created from the new entities on start (`./mvnw spring-boot:run` starts cleanly, `/api/health` still `200`).
- `AppointmentLifecycle.allowedActions(SCHEDULED appt, RECEPTIONIST)` → `{START, CANCEL, RESCHEDULE, EDIT_OBSERVATIONS}`; `(SCHEDULED, OWNER)` → `{CANCEL}`.
- `new Pet("Tom", DOG, SIAMESE, owner)` → `BREED_SPECIES_MISMATCH`.

**Frontend**
- No visible change.

#### Automated Tests
The tests prove the full transition table, the observation window, schedule and breed invariants, and the overlap queries against a real H2 schema.

**Backend**
- `AppointmentLifecycleTests` (unit, fixed `Clock`, parameterized)
  - **Success:** each of the 11 table rows for each listed role; EXPIRE by SYSTEM; `allowedActions` per status/role.
  - **Rejections:** every (status, action) pair not in the table → `INVALID_TRANSITION` (including all actions from RESCHEDULED, CANCELLED, CLOSED); OWNER START → `ACTION_NOT_ALLOWED`; OWNER CANCEL on IN_PROGRESS → `ACTION_NOT_ALLOWED`; VETERINARIAN PAY → not allowed; CANCEL with blank reason → `REASON_REQUIRED`; CONFIRM after `reservedUntil` → `RESERVATION_EXPIRED`; observations not editable on CLOSED/CANCELLED.
- `ScheduleValidatorTests` (unit)
  - **Success:** non-overlapping blocks on the same day; adjacent blocks (13:00 end, 13:00 start).
  - **Rejections:** overlapping same-day blocks → `INVALID_SCHEDULE`; start equals end → rejected; start after end → rejected; empty list → rejected.
- `BreedTests` (unit)
  - **Success:** every breed belongs to its species; `Pet` with `LABRADOR_RETRIEVER` dog created.
  - **Rejections:** dog with `PERSIAN` → `BREED_SPECIES_MISMATCH`.
- `AppointmentRepositoryTests` (`@DataJpaTest`)
  - **Success:** `existsOverlap` true for overlapping SCHEDULED/IN_PROGRESS/live RESERVED; adjacent appointment (end = start) does not overlap; `excludeId` ignores that appointment.
  - **Rejections:** expired RESERVED, CANCELLED, RESCHEDULED, FINISHED do not count as overlap; duplicate phone / duplicate payment per appointment → constraint violation.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the domain invariants and the lifecycle table independently of any API. The live check confirms the backend still starts with the new schema and the frontend is unaffected (A8).

**Automated**

Backend:
- `cd backend && ./mvnw verify` → BUILD SUCCESS, all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- `curl http://localhost:8080/api/health` → `200`; http://localhost:3000 still renders the skeleton home.

### I2 — Registration use cases

- **Status:** `completed`
- **Goal:** Application services register veterinarians (with schedule and account), owners (with account and optional pets) and pets, list them paginated, and enforce owner data scoping, all unit tested.
- **Covers:** Flow "Register" (service layer); rules "owner name and phone required; phone unique", "pet belongs to one owner, has name, species and breed; breed must belong to species", "schedule blocks do not overlap", "veterinarian costs are zero or more", "owners see only their own pets"; Roles (account per owner and veterinarian, A1, A2).
- **Depends on:** I1

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/auth/CurrentUser.java`: record `(Long userId, Role role, Long ownerId, Long veterinarianId)`; `actor()`.
  - `backend/src/main/java/com/vetclinic/config/PasswordConfig.java`: `PasswordEncoder` (BCrypt) bean.
  - `backend/src/main/java/com/vetclinic/auth/AccountService.java`: creates `UserAccount`s.
  - `backend/src/main/java/com/vetclinic/catalog/CatalogService.java`: `list()`, `require(id)`.
  - `backend/src/main/java/com/vetclinic/catalog/dto/ServiceResponse.java`.
  - `backend/src/main/java/com/vetclinic/veterinarian/VeterinarianService.java`.
  - `backend/src/main/java/com/vetclinic/veterinarian/dto/CreateVeterinarianRequest.java`, `ScheduleBlockDto.java`, `VeterinarianResponse.java`.
  - `backend/src/main/java/com/vetclinic/owner/OwnerService.java`, `PetService.java`.
  - `backend/src/main/java/com/vetclinic/owner/dto/CreateOwnerRequest.java`, `OwnerResponse.java`, `CreatePetRequest.java`, `PetResponse.java`.
  - `backend/src/test/java/com/vetclinic/veterinarian/VeterinarianServiceTests.java`, `backend/src/test/java/com/vetclinic/owner/OwnerServiceTests.java`, `PetServiceTests.java`.

**Frontend**
- None.

#### Implementation Tasks

**Backend**
1. `AccountService.create(username, rawPassword, role, displayName, ownerId?, vetId?)`: `existsByUsername` → `409 USERNAME_TAKEN`; BCrypt hash.
2. `CatalogService`: `list() → List<ServiceResponse>`; `require(id)` → `404 SERVICE_NOT_FOUND`.
3. `VeterinarianService`:
   - `create(CreateVeterinarianRequest) → VeterinarianResponse`: `ScheduleValidator.validate`; save vet + `AccountService.create(VETERINARIAN)`; one transaction.
   - `list(Pageable) → PageResponse<VeterinarianResponse>`, `get(id)`, `require(id)` (`404 VETERINARIAN_NOT_FOUND`).
4. `OwnerService`:
   - `create(CreateOwnerRequest) → OwnerResponse`: phone uniqueness → `409 PHONE_TAKEN`; `AccountService.create(OWNER, username = phone)` (A1); optional `pets` added via `PetService`, all in one transaction.
   - `list(String query, Pageable)`: name/phone contains, paginated; `get(id)` → `404 OWNER_NOT_FOUND`, includes pets.
5. `PetService`:
   - `add(ownerId, CreatePetRequest) → PetResponse`: mismatch surfaces as `400 BREED_SPECIES_MISMATCH`.
   - `listFor(CurrentUser, Long ownerId, Pageable)`: owner → own pets only (ignores `ownerId`); receptionist → by `ownerId` (required, else 400); vet → `403`.
   - `requireAccessible(CurrentUser, petId) → Pet`: owner accessing another owner's pet → `404 PET_NOT_FOUND` (no data leak); vet → `403`.
6. DTOs: `CreateVeterinarianRequest(@NotBlank name, @NotBlank specialization, @NotNull @DecimalMin("0.00") costPerService, @NotBlank username, @Size(min=6) password, @NotEmpty @Valid List<ScheduleBlockDto> schedule)`; `CreateOwnerRequest(@NotBlank name, @NotBlank @Pattern phone, @Size(min=6) password, @Valid List<CreatePetRequest> pets)`; `CreatePetRequest(@NotBlank name, @NotNull species, @NotNull breed)`.

**Frontend**
- None in this increment.

#### Expected Interfaces and Outputs

**Backend**
- `VeterinarianService.create` with overlapping blocks → `INVALID_SCHEDULE`; valid → vet and linked `VETERINARIAN` account with hashed password.
- `OwnerService.create` duplicate phone → `PHONE_TAKEN`; `PetService.listFor(owner A, ownerId=B)` → only A's pets.

**Frontend**
- No visible change.

#### Automated Tests
The tests prove registration rules, unique phone and username, transactional rollback on invalid pets, and owner scoping.

**Backend**
- `VeterinarianServiceTests` (unit, mocked repositories and encoder)
  - **Success:** creates vet and linked account with hashed password.
  - **Rejections:** overlapping schedule → `INVALID_SCHEDULE`; duplicate username → `USERNAME_TAKEN`; unknown id → `VETERINARIAN_NOT_FOUND`.
- `OwnerServiceTests` (unit)
  - **Success:** creates owner, owner account and initial pets.
  - **Rejections:** duplicate phone → `PHONE_TAKEN`; initial pet with mismatched breed → `BREED_SPECIES_MISMATCH` and nothing saved.
- `PetServiceTests` (unit)
  - **Success:** dog with `LABRADOR_RETRIEVER`; owner lists own pets.
  - **Rejections:** dog with `PERSIAN` → mismatch; owner reading another owner's pet → `PET_NOT_FOUND`; vet listing → forbidden; receptionist without `ownerId` → 400.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the registration use cases and scoping at service level. The live check confirms both apps still start unchanged (A8).

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- `curl http://localhost:8080/api/health` → `200`; frontend home unchanged.

### I3 — Appointment and payment use cases

- **Status:** `pending`
- **Goal:** All appointment use cases (availability, walk-in and online booking, confirm, expire, cancel, start, finish, observations, reschedule/reassign, pay, close, scoped queries, debt and pet history) exist as application services in their final shape, unit tested with a fixed `Clock`, with concurrency proven at service level.
- **Covers:** Flows "Book online", "Book a walk-in", "Visit", "Reschedule or reassign", "Cancel", "Pay and close", "Follow up" (service layer); every lifecycle row (via `AppointmentLifecycle`); rules "slot available when duration fits schedule and does not overlap reserved, scheduled or in-progress appointments", "appointments are booked in the future", "two bookings cannot take the same slot", "total = base price + veterinarian cost, fixed at booking", "one exact payment per finished appointment", "Rescheduled and Closed are final", "owners see only their own appointments"; acceptance "two bookings for same vet and overlapping time cannot both succeed" (service level), "unconfirmed reservation expires and slot becomes free" (service level).
- **Depends on:** I1, I2

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/appointment/AvailabilityService.java`: slot computation.
  - `backend/src/main/java/com/vetclinic/appointment/BookingService.java`: creation logic.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentActionService.java`: lifecycle-driven actions.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentQueryService.java`: scoped reads, response mapping.
  - `backend/src/main/java/com/vetclinic/appointment/ReservationExpiryJob.java`: `@Scheduled(fixedDelayString = "${app.booking.expiry-sweep:PT30S}")` calling `expireDue()`.
  - `backend/src/main/java/com/vetclinic/config/SchedulingConfig.java`: `@EnableScheduling`.
  - `backend/src/main/java/com/vetclinic/appointment/dto/SlotResponse.java`, `CreateAppointmentRequest.java`, `AppointmentResponse.java`, `AppointmentFilter.java`, `CancelRequest.java` (`@NotBlank @Size(max=500) String reason`; HTTP blank reason → `400 VALIDATION_FAILED`, lifecycle `REASON_REQUIRED` stays as the service-level guard), `ObservationsRequest.java`, `RescheduleRequest.java`.
  - `backend/src/main/java/com/vetclinic/payment/PaymentService.java`.
  - `backend/src/main/java/com/vetclinic/payment/dto/PaymentRequest.java`, `PaymentResponse.java`.
  - `backend/src/test/java/com/vetclinic/appointment/AvailabilityServiceTests.java`, `BookingServiceTests.java`, `AppointmentActionServiceTests.java`, `AppointmentQueryServiceTests.java`, `BookingConcurrencyIntegrationTests.java`; `backend/src/test/java/com/vetclinic/payment/PaymentServiceTests.java`.
- **Modify:**
  - `backend/src/main/resources/application.yml`: `app.booking.reservation-ttl: PT10M`, `app.booking.expiry-sweep: PT30S`.

**Frontend**
- None.

#### Implementation Tasks

**Backend**
1. `AvailabilityService`:
   - `slots(serviceId, Optional vetId, LocalDate date) → List<SlotResponse(veterinarianId, veterinarianName, startAt, endAt)>`: for each vet (or the one), each schedule block on `date.getDayOfWeek()`, step 15 min while `start + duration <= block.end`; drop `start <= now(clock)`; drop overlaps with `findBlocking`; past date → empty; unknown service/vet → 404.
   - `isBookable(vet, service, start) → boolean`: fits a block, future, grid-aligned.
2. `BookingService`:
   - `book(CurrentUser, CreateAppointmentRequest(petId, serviceId, veterinarianId, startAt)) → AppointmentResponse`: pet via `PetService.requireAccessible`; RECEPTIONIST → `createScheduled(..., bookedBy RECEPTIONIST)`; OWNER → same checks, status `RESERVED`, `bookedBy = OWNER`, `reservedUntil = now + ttl`; VETERINARIAN → `403`.
   - `createScheduled(pet, service, vet, start, bookedBy, rescheduledFrom, excludeId) → Appointment`: `start <= now` → `400 START_IN_PAST`; lock vet with `findLockedById`; `!isBookable` → `409 SLOT_UNAVAILABLE`; `existsOverlap(..., excludeId)` → `409 SLOT_TAKEN`; `total = basePrice + costPerService` (scale 2). Shared by booking and reschedule; `@Transactional`.
3. `AppointmentActionService` (each `@Transactional`, scoped load through `AppointmentQueryService.requireScoped`, status change only via `lifecycle.apply`):
   - `confirm(CurrentUser, id)`; `expireDue()`: RESERVED with `reservedUntil <= now` → EXPIRE by SYSTEM, reason "Reservation expired".
   - `cancel(CurrentUser, id, reason)`.
   - `start`, `finish(CurrentUser, id)`: vet must be the assigned one (else `404`).
   - `updateObservations(CurrentUser, id, text)`: owners → `403`; `!canEditObservations` → `409 OBSERVATIONS_LOCKED`.
   - `reschedule(CurrentUser, id, RescheduleRequest(veterinarianId, startAt))`: `lifecycle.apply(RESCHEDULE)`; `bookingService.createScheduled(..., RECEPTIONIST, original, excludeId = original.id)` (A6); conflict rolls back so original stays unchanged; returns new appointment.
   - `close(CurrentUser, id)`: `lifecycle.apply(CLOSE)` from FINISHED or PAID.
4. `PaymentService.pay(CurrentUser, appointmentId, PaymentRequest(@NotNull @DecimalMin("0.00") amount)) → PaymentResponse`: existing payment → `409 ALREADY_PAID`; `lifecycle.apply(PAY, actor)` (non-finished → `409 INVALID_TRANSITION`, non-receptionist → 403); `amount.compareTo(total) != 0` → `409 AMOUNT_MISMATCH` "Payment must equal total 60.00"; save payment and PAID in one transaction.
5. `AppointmentQueryService`:
   - `list(CurrentUser, AppointmentFilter(statuses, date, veterinarianId, debt), Pageable)`: OWNER → pets of `ownerId`; VETERINARIAN → `veterinarianId`; RECEPTIONIST → all; sort `startAt`.
   - `get(CurrentUser, id)` / `requireScoped`: out-of-scope → `404 APPOINTMENT_NOT_FOUND`.
   - `historyForPet(CurrentUser, petId, Pageable)`: `PetService.requireAccessible`; all statuses, `startAt` desc; `upcoming = startAt > now`.
   - `toResponse(Appointment, Actor)`: `AppointmentResponse(id, pet{id,name,species,breed}, ownerName, service{id,name,durationMinutes}, veterinarian{id,name}, startAt, endAt, status, bookedBy, total, observations, cancelReason, reservedUntil, rescheduledFromId, rescheduledToId, debt, upcoming, allowedActions)`; `debt` = CLOSED and no payment (A5).
6. `ReservationExpiryJob`, `SchedulingConfig`, `application.yml`: as listed.

**Frontend**
- None in this increment.

#### Expected Interfaces and Outputs

**Backend**
- `BookingService.book(receptionist, free future slot)` → SCHEDULED, `bookedBy` RECEPTIONIST, total 40.00 + 20.00 = 60.00; `book(owner, own pet)` → RESERVED with `reservedUntil = now + 10m`.
- `AppointmentActionService.reschedule` on SCHEDULED → new SCHEDULED with `rescheduledFromId`; original RESCHEDULED.
- `PaymentService.pay(60.01)` on total 60.00 → `AMOUNT_MISMATCH`.
- `application.yml` contains `app.booking.reservation-ttl: PT10M`.

**Frontend**
- No visible change.

#### Automated Tests
The tests prove every use case applies the lifecycle and business rules with correct scoping, and that two concurrent bookings of the same slot cannot both succeed.

**Backend**
- `AvailabilityServiceTests` (unit, fixed `Clock`, mocked repositories)
  - **Success:** slots on grid inside blocks; slot ending exactly at block end included; expired RESERVED does not block; CANCELLED/RESCHEDULED/FINISHED do not block.
  - **Rejections:** slot overlapping SCHEDULED, IN_PROGRESS or live RESERVED excluded; slots at or before now excluded; day without blocks → empty; duration exceeding block → no slot; unknown service → `SERVICE_NOT_FOUND`.
- `BookingServiceTests` (unit, fixed `Clock`)
  - **Success:** receptionist → SCHEDULED, total 60.00; owner own pet → RESERVED with `reservedUntil`.
  - **Rejections:** overlap → `SLOT_TAKEN`; past → `START_IN_PAST`; off-schedule → `SLOT_UNAVAILABLE`; owner with another owner's pet → `PET_NOT_FOUND`; vet booking → forbidden.
- `AppointmentActionServiceTests` (unit, mutable fixed `Clock`)
  - **Success:** confirm before `reservedUntil`; `expireDue` cancels only expired reservations; owner cancels own RESERVED/SCHEDULED; receptionist cancels RESERVED/SCHEDULED/IN_PROGRESS; vet starts/finishes own; receptionist starts/finishes any; observations saved on SCHEDULED, IN_PROGRESS, FINISHED, PAID; SCHEDULED rescheduled to new time; IN_PROGRESS reassigned to other vet; close from PAID and FINISHED.
  - **Rejections:** confirm at/after `reservedUntil` → `RESERVATION_EXPIRED`; another owner's appointment → not found; blank reason → `REASON_REQUIRED`; owner cancel IN_PROGRESS → `ACTION_NOT_ALLOWED`; receptionist cancel FINISHED → `INVALID_TRANSITION`; other vet start → not found; finish SCHEDULED → `INVALID_TRANSITION`; observations on CLOSED/CANCELLED → `OBSERVATIONS_LOCKED`; reschedule FINISHED → `INVALID_TRANSITION`; reschedule by owner → `ACTION_NOT_ALLOWED`; reschedule into occupied slot → `SLOT_TAKEN` and original stays SCHEDULED; close CLOSED → `INVALID_TRANSITION`.
- `PaymentServiceTests` (unit)
  - **Success:** exact total on FINISHED → PAID; `60.0` equals `60.00`.
  - **Rejections:** below/above total → `AMOUNT_MISMATCH`; SCHEDULED/IN_PROGRESS → `INVALID_TRANSITION`; second payment → `ALREADY_PAID`; vet → forbidden.
- `AppointmentQueryServiceTests` (unit)
  - **Success:** owner list scoped to own pets; vet list scoped to assignment; `debt` true only for CLOSED unpaid; history ordered desc with `upcoming` flags; `allowedActions` from the lifecycle.
  - **Rejections:** owner get other owner's appointment → `APPOINTMENT_NOT_FOUND`; owner history of other pet → `PET_NOT_FOUND`; vet history → forbidden.
- `BookingConcurrencyIntegrationTests` (integration, `@SpringBootTest` without web layer, test data inserted via repositories, two threads calling `BookingService.book` for the same vet/slot)
  - **Success:** exactly one booking saved.
  - **Rejections:** the other throws `SLOT_TAKEN`.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove every appointment and payment use case, the expiry sweep and concurrency safety without any HTTP layer. The live check confirms the scheduler starts with the app and nothing else changes (A8).

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Backend starts without errors (expiry sweeper runs silently on an empty database); `curl http://localhost:8080/api/health` → `200`.

### I4 — Authentication, roles and demo seed

- **Status:** `pending`
- **Goal:** The seeded receptionist and veterinarians sign in on the frontend, the JWT lives only in an HttpOnly cookie, protected API calls require a valid token, errors are `ProblemDetail`, and sign-out clears the session.
- **Covers:** Technology Constraints (stateless JWT, roles in token, HttpOnly/Secure/SameSite cookie, sign-out, OpenAPI, seeded demo data); Roles; seed "receptionist account, two veterinarians with weekly schedules, and the four services"; acceptance "backend builds and tests pass; frontend builds and lints".
- **Depends on:** I3

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/auth/TokenService.java`: issues signed JWTs.
  - `backend/src/main/java/com/vetclinic/auth/AuthService.java`: credential check and current-user lookup.
  - `backend/src/main/java/com/vetclinic/auth/AuthController.java`: `/api/auth/login`, `/api/auth/me`.
  - `backend/src/main/java/com/vetclinic/auth/dto/LoginRequest.java`, `LoginResponse.java`, `CurrentUserResponse.java`: records.
  - `backend/src/main/java/com/vetclinic/config/OpenApiConfig.java`: bearer-JWT security scheme.
  - `backend/src/main/java/com/vetclinic/shared/error/GlobalExceptionHandler.java`: single `@RestControllerAdvice`.
  - `backend/src/main/java/com/vetclinic/shared/error/JsonAuthHandlers.java`: `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) writing `ProblemDetail`.
  - `backend/src/main/java/com/vetclinic/seed/DemoDataSeeder.java`: `CommandLineRunner` seeding per A3 through `VeterinarianService` and `AccountService`.
  - `backend/src/test/java/com/vetclinic/auth/AuthControllerTests.java`, `backend/src/test/java/com/vetclinic/auth/AuthFlowIntegrationTests.java`, `backend/src/test/java/com/vetclinic/seed/DemoDataSeederTests.java`, `backend/src/test/java/com/vetclinic/support/TestJwt.java` (helper issuing test tokens per role).
- **Modify:**
  - `backend/pom.xml`: add `spring-boot-starter-oauth2-resource-server` (Boot-managed).
  - `backend/src/main/java/com/vetclinic/auth/CurrentUser.java`: add `from(Authentication)` reading `JwtAuthenticationToken` claims.
  - `backend/src/main/java/com/vetclinic/config/SecurityConfig.java`: stateless JWT resource server, public matchers, `@EnableMethodSecurity`, `JwtDecoder`/`JwtEncoder` beans, role converter, JSON 401/403 handlers (uses `PasswordConfig` from I2).
  - `backend/src/main/resources/application.yml`: `app.jwt.secret: ${APP_JWT_SECRET:dev-only-change-me-...}` (marked dev default), `app.jwt.ttl: PT8H`.
  - `backend/src/test/java/com/vetclinic/health/HealthControllerTests.java`: import the beans the new `SecurityConfig` needs so `/api/health` still passes unauthenticated.

**Frontend**
- **Create:**
  - `frontend/lib/session.ts`: `server-only` cookie helpers.
  - `frontend/lib/types.ts`: API types (`Role`, `CurrentUser`, `LoginResponse`, `ProblemDetail`, `PageResponse<T>`).
  - `frontend/lib/auth.ts`: `getCurrentUser()`, `requireRole(...roles)`.
  - `frontend/app/login/page.tsx` and `frontend/app/login/login-form.tsx`: sign-in page and client form.
  - `frontend/app/actions/auth.ts`: server actions `signIn`, `signOut`.
  - `frontend/proxy.ts`: redirect to `/login` when `vc_session` is missing (except `/login`).
  - `frontend/components/AppHeader.tsx`: user role, role-specific nav links, sign-out button.
  - `frontend/components/FormField.tsx`: label + input + field error.
  - `frontend/components/ErrorMessage.tsx`: readable server error block.
- **Modify:**
  - `frontend/lib/api.ts`: `server-only`; `apiFetch<T>(path, init)` adds `Authorization` from cookie, `cache: "no-store"`, parses `ProblemDetail` into `ApiError(status, code, message, fieldErrors)`; `apiGet`, `apiPost`, `apiPatch` wrappers.
  - `frontend/app/page.tsx`: server component redirecting by role (`/reception/appointments`, `/owner/appointments`, `/vet/appointments`); until those exist, render a role welcome with the header.
  - `frontend/app/layout.tsx`: render `AppHeader` when signed in.
- **Delete:**
  - `frontend/app/backend-status.tsx`: client-side backend call violates the server-only API layer; health stays reachable at `/api/health`.

#### Implementation Tasks

**Backend**
1. `TokenService.issue(UserAccount) → String`: HS256 via `JwtEncoder`, claims `sub`, `role`, `ownerId`, `veterinarianId`, `exp = now(clock) + ttl`.
2. `AuthService`:
   - `login(LoginRequest) → LoginResponse(token, expiresAt, role, displayName)`: BCrypt match; unknown user or bad password → `401 BAD_CREDENTIALS` (same message for both).
   - `me(CurrentUser) → CurrentUserResponse`.
3. `AuthController`: `POST /login` (`@Valid`), `GET /me`.
4. `CurrentUser.from(Authentication)`: as listed.
5. `SecurityConfig`: `STATELESS`; permit `/api/health`, `/api/auth/login`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/health`; `anyRequest().authenticated()`; `JwtAuthenticationConverter` mapping `role` → `ROLE_<role>`; keep CORS bean.
6. `GlobalExceptionHandler`: `DomainException` → its status/code; `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` with `errors: [{field, message}]`; `AccessDeniedException` → 403 `FORBIDDEN`; fallback 500 without stack trace.
7. `JsonAuthHandlers`: 401 `UNAUTHORIZED`, 403 `FORBIDDEN` as `application/problem+json`.
8. `DemoDataSeeder`: idempotent; receptionist, four services, two vets with accounts and schedules (A2, A3).
9. `OpenApiConfig`, `application.yml`, `pom.xml`, `HealthControllerTests`: as listed.

**Frontend**
1. `lib/session.ts`:
   - `setSession(token, expiresAt)`: `cookies().set("vc_session", token, { httpOnly: true, secure: true, sameSite: "lax", path: "/", expires })`.
   - `getToken()`, `clearSession()`.
2. `lib/api.ts`: as listed; 401 from backend → `clearSession()` + `redirect("/login")`.
3. `lib/auth.ts`: `getCurrentUser()` (calls `/api/auth/me`, `null` on 401); `requireRole(...roles)` redirects to `/login` or `/` when role mismatches.
4. `app/actions/auth.ts`: `signIn(prevState, formData)` posts to `/api/auth/login`, sets cookie, redirects `/`; returns `{ error }` on 401/400. `signOut()` clears cookie and redirects `/login`.
5. `app/login/login-form.tsx`: client form with `useActionState(signIn)`, labelled username/password fields, error message.
6. `proxy.ts`: matcher excludes `/login`, `_next`, static assets.
7. `AppHeader`, `FormField`, `ErrorMessage`: shared UI.
8. `app/page.tsx`, `app/layout.tsx`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/auth/login` `{username:"reception",password:"reception123"}` → `200 {token, expiresAt, role:"RECEPTIONIST", displayName}`; wrong password → `401` `code: BAD_CREDENTIALS`; blank username → `400` `VALIDATION_FAILED` with `errors[0].field = "username"`.
- `GET /api/auth/me` with token → `200 {userId, username, role}`; without token → `401` `UNAUTHORIZED`; tampered or expired token → `401`.
- `GET /api/health` without token → `200`.
- `application.yml` contains `app.jwt.secret` with env override and `app.jwt.ttl: PT8H`.

**Frontend**
- `/` without cookie → redirect to `/login`.
- `/login` with valid credentials → redirect to `/`, header shows role and sign-out.
- Cookie `vc_session` is `HttpOnly`, `Secure`, `SameSite=Lax`; `document.cookie` in DevTools does not contain it.
- Sign-out → cookie removed, redirect `/login`.

#### Automated Tests
The tests prove sign-in issues a role-bearing token, protected endpoints reject missing/invalid tokens with JSON 401, errors use `ProblemDetail`, and seeding is complete and idempotent.

**Backend**
- `AuthControllerTests` (slice, `@WebMvcTest(AuthController)` + `SecurityConfig`, mocked `AuthService`)
  - **Success:** valid login returns token payload; `/me` with `TestJwt` receptionist token returns the user.
  - **Rejections:** blank fields → 400 with field errors; bad credentials → 401 `BAD_CREDENTIALS`; `/me` without token → 401 `UNAUTHORIZED`; `/me` with token signed by another key → 401.
- `AuthFlowIntegrationTests` (integration, `@SpringBootTest` + MockMvc, seeded data)
  - **Success:** real login as `reception` and `vet.ana`, then `/api/auth/me` → 200 with the right role.
  - **Rejections:** expired token (fixed `Clock` past `exp`) → 401.
- `DemoDataSeederTests` (integration)
  - **Success:** one receptionist, two vets with schedules and accounts, four services.
  - **Rejections:** running the seeder twice creates no duplicates.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the token issuance, validation, JSON error contract and seed data. Live checks confirm the cookie is HttpOnly and invisible to scripts, protected routes redirect, and sign-out clears the session.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → BUILD SUCCESS, all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → build succeeds.

**Manual** (both apps running)
- Open http://localhost:3000 → redirected to `/login`.
- Sign in as `reception` / `reception123` → home shows Receptionist; DevTools Application tab shows `vc_session` with HttpOnly, Secure, SameSite=Lax; `document.cookie` in the console omits it.
- `curl -i http://localhost:8080/api/auth/me` → `401` with `application/problem+json`.
- Click Sign out → back on `/login`; `vc_session` gone; sign in as `vet.ana` / `vet123` → Veterinarian home.

### I5 — Services catalog and veterinarian registration

- **Status:** `pending`
- **Goal:** Reception lists the four seeded services and two seeded veterinarians, and registers a new veterinarian with a weekly schedule; invalid schedules and negative costs are rejected with clear messages.
- **Covers:** Flow "Register" (veterinarians, UI + API); Services fixed list; acceptance "register a veterinarian with a weekly schedule; overlapping schedule blocks rejected with a clear message".
- **Depends on:** I4

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/catalog/CatalogController.java`: `GET /api/services`.
  - `backend/src/main/java/com/vetclinic/veterinarian/VeterinarianController.java`.
  - `backend/src/test/java/com/vetclinic/veterinarian/VeterinarianControllerTests.java`, `backend/src/test/java/com/vetclinic/catalog/CatalogControllerTests.java`.

**Frontend**
- **Create:**
  - `frontend/app/reception/layout.tsx`: `requireRole("RECEPTIONIST")`.
  - `frontend/app/reception/veterinarians/page.tsx`: server list of veterinarians (name, specialization, cost, schedule summary) with empty/error states.
  - `frontend/app/reception/veterinarians/veterinarian-form.tsx`: client form with dynamic schedule rows.
  - `frontend/app/actions/veterinarians.ts`: `createVeterinarian` server action.
  - `frontend/components/EmptyState.tsx`, `frontend/components/Pagination.tsx`: shared list UI.
- **Modify:**
  - `frontend/lib/types.ts`: `Service`, `Veterinarian`, `ScheduleBlock`, `CreateVeterinarianRequest`.
  - `frontend/components/AppHeader.tsx`: receptionist link "Veterinarians".

#### Implementation Tasks

**Backend**
1. `CatalogController`: `GET /api/services` → `CatalogService.list()` (fixed four, not paginated: closed catalog).
2. `VeterinarianController`: `GET` (any auth, paginated), `GET /{id}`, `POST` `@PreAuthorize("hasRole('RECEPTIONIST')")` `@Valid` → 201 + `Location`; delegates to `VeterinarianService`.

**Frontend**
1. `reception/layout.tsx`: role guard.
2. `reception/veterinarians/page.tsx`: fetch `GET /api/veterinarians?page=`; table; `Pagination`.
3. `veterinarian-form.tsx`: fields mirror DTO; add/remove schedule rows (weekday select, start/end `type="time"`); client check start < end; shows backend field and schedule errors via `useActionState`.
4. `actions/veterinarians.ts`: `createVeterinarian(prev, formData)` → `apiPost`; maps `ApiError.fieldErrors`; `revalidatePath`.
5. `EmptyState`, `Pagination`, `types.ts`, `AppHeader`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `GET /api/services` → `200 [{id, code, name, durationMinutes, basePrice}]` with four items.
- `POST /api/veterinarians` (receptionist) valid → `201` `Location: /api/veterinarians/{id}`; overlapping blocks → `400 INVALID_SCHEDULE` naming the day; `end <= start` → `400 INVALID_SCHEDULE`; `costPerService: -1` → `400 VALIDATION_FAILED` field `costPerService`; duplicate username → `409 USERNAME_TAKEN`; owner/vet token → `403`.
- `GET /api/veterinarians` → `200 PageResponse` with the two seeded vets.

**Frontend**
- `/reception/veterinarians` as receptionist → list with two seeded vets and a register form.
- Submitting overlapping blocks → message shown under the schedule section; no raw JSON.
- `/reception/veterinarians` as non-receptionist → redirect to `/`.

#### Automated Tests
The tests prove the HTTP contract: validation errors, receptionist-only registration and the catalog endpoint.

**Backend**
- `VeterinarianControllerTests` (slice, `TestJwt`, mocked `VeterinarianService`)
  - **Success:** receptionist POST → 201 + Location; any role GET list → 200.
  - **Rejections:** missing name → 400 field error; negative cost → 400; service throws `INVALID_SCHEDULE` → 400 with code; owner POST → 403; no token → 401.
- `CatalogControllerTests` (slice)
  - **Success:** returns services from `CatalogService`.
  - **Rejections:** no token → 401.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the endpoint contract and role enforcement. Live checks confirm reception can register a veterinarian and sees clear messages for invalid schedules.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Sign in as `reception` → open Veterinarians → two seeded vets listed.
- Register a vet with Monday 09:00–13:00 and 12:00–15:00 → overlap message shown; fix to 13:00–15:00 → vet appears in the list.
- Sign in as `vet.ana` / `vet123` → navigating to `/reception/veterinarians` redirects to `/`.

### I6 — Owners and pets registration with breed picker

- **Status:** `pending`
- **Goal:** Reception registers owners (with sign-in) and their pets using a species-filtered breed picker; owners sign in and see only their own pets.
- **Covers:** Flow "Register" (owners and pets, UI + API); rule "owners see only their own pets" (API); Pets species/breeds list (UI); acceptance "register an owner with pets; missing name or phone, breed not matching species rejected with a clear message", "breed picker only offers breeds of the selected species".
- **Depends on:** I5

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/owner/OwnerController.java`, `PetController.java`.
  - `backend/src/test/java/com/vetclinic/owner/OwnerControllerTests.java`, `PetControllerTests.java`.

**Frontend**
- **Create:**
  - `frontend/lib/breeds.ts`: `BREEDS_BY_SPECIES` constant mirroring backend `Breed` with labels.
  - `frontend/app/reception/owners/page.tsx`: paginated owners list with phone/name search and register form.
  - `frontend/app/reception/owners/owner-form.tsx`: client form (name, phone, password, optional first pet).
  - `frontend/app/reception/owners/[id]/page.tsx`: owner detail with pets and add-pet form.
  - `frontend/components/PetForm.tsx`: client pet form with species select driving the breed select.
  - `frontend/app/actions/owners.ts`: `createOwner`, `addPet` server actions.
  - `frontend/app/owner/layout.tsx`: `requireRole("OWNER")`.
  - `frontend/app/owner/pets/page.tsx`: owner's own pets.
- **Modify:**
  - `frontend/lib/types.ts`: `Owner`, `Pet`, `Species`, `Breed`, request types.
  - `frontend/components/AppHeader.tsx`: receptionist "Owners"; owner "My pets".

#### Implementation Tasks

**Backend**
1. `OwnerController`: receptionist-only `POST` (201 + Location), `GET ?q=&page=`, `GET /{id}`, `POST /{id}/pets` → 201; delegates to `OwnerService` / `PetService`.
2. `PetController`: `GET /api/pets?ownerId=` → `PetService.listFor(CurrentUser.from(auth), ...)`.

**Frontend**
1. `lib/breeds.ts`: `breedsFor(species)`.
2. `PetForm`: species select; on change resets breed and shows only `breedsFor(species)`; breed disabled until species chosen.
3. `owner-form.tsx`, `owners/page.tsx`, `owners/[id]/page.tsx`: as listed, field errors from backend.
4. `actions/owners.ts`: post and map errors; `revalidatePath`.
5. `owner/layout.tsx`, `owner/pets/page.tsx`: guard and own pets list with empty state.
6. `types.ts`, `AppHeader`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/owners` `{name, phone, password, pets:[...]}` → `201 OwnerResponse`; missing name → `400` field `name`; missing phone → `400` field `phone`; duplicate phone → `409 PHONE_TAKEN`.
- `POST /api/owners/{id}/pets` `{species:"DOG", breed:"SIAMESE"}` → `400 BREED_SPECIES_MISMATCH`.
- `GET /api/pets` as owner → only own pets; `GET /api/pets?ownerId=<other>` as owner → still only own pets.
- Owner token on `/api/owners` → `403`.

**Frontend**
- Pet form with species Cat → breed options exactly Siamese, Persian, Maine Coon, Bengal, Mixed breed.
- Owner signed in with phone/password → `/owner/pets` lists only their pets.

#### Automated Tests
The tests prove the HTTP contract for owner/pet validation and role enforcement.

**Backend**
- `OwnerControllerTests` (slice, mocked services)
  - **Success:** receptionist POST → 201 + Location.
  - **Rejections:** missing name, missing phone → 400 with field errors; `PHONE_TAKEN` → 409; owner token → 403; no token → 401.
- `PetControllerTests` (slice)
  - **Success:** owner GET → 200 page.
  - **Rejections:** vet GET → 403.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the endpoint contract and role enforcement. Live checks confirm the breed picker filters by species and an owner sees only their own pets.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- As reception, register owner "Maria" phone `5550001` password `maria123` with a dog; switch species to Cat → breed list changes to cat breeds only.
- Register a second owner with the same phone → "phone already registered" message.
- Register a second owner "Juan" `5550002` with a cat; sign in as `5550001` → `/owner/pets` shows only Maria's dog.

### I7 — Availability, walk-in booking and appointment detail

- **Status:** `pending`
- **Goal:** Reception picks a pet and a free slot and books a walk-in that is immediately `SCHEDULED` with reception as booker; the detail page shows a status badge and only allowed actions; concurrent bookings of the same slot cannot both succeed over HTTP.
- **Covers:** Flow "Book a walk-in" (UI + API); rule "two bookings cannot take the same slot; second rejected and user sees fresh availability"; acceptance "walk-in scheduled immediately and records reception as booker", "two bookings for same vet and overlapping time cannot both succeed", "owner sees only free slots for chosen service, veterinarian and date" (API), "each status shows a consistent badge, only allowed actions offered" (badge + action bar introduced).
- **Depends on:** I6

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/appointment/AvailabilityController.java`: `GET /api/availability`.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `POST /api/appointments`, `GET /api/appointments`, `GET /api/appointments/{id}`.
  - `backend/src/test/java/com/vetclinic/appointment/AvailabilityControllerTests.java`, `AppointmentControllerTests.java`, `WalkInBookingIntegrationTests.java`.

**Frontend**
- **Create:**
  - `frontend/components/StatusBadge.tsx`: one colour/label per `AppointmentStatus`.
  - `frontend/components/SlotPicker.tsx`: client component — service, optional vet, date selectors; loads slots via server action on every change; selectable slot list with loading/empty/error states.
  - `frontend/components/AppointmentActions.tsx`: renders buttons only for `allowedActions` that have a wired handler.
  - `frontend/app/actions/availability.ts`: `fetchSlots(serviceId, vetId?, date)` server action.
  - `frontend/app/actions/appointments.ts`: `bookAppointment` server action.
  - `frontend/app/reception/book/page.tsx` + `walk-in-form.tsx`: owner search → pet select → `SlotPicker` → Book.
  - `frontend/app/appointments/[id]/page.tsx`: detail (pet, service, vet, time, duration, total, booker, status badge, observations, `AppointmentActions`).
- **Modify:**
  - `frontend/lib/types.ts`: `AppointmentStatus`, `AppointmentAction`, `Slot`, `Appointment`, `CreateAppointmentRequest`.
  - `frontend/components/AppHeader.tsx`: receptionist "Book walk-in".

#### Implementation Tasks

**Backend**
1. `AvailabilityController`: `GET /api/availability?serviceId&veterinarianId?&date?` `@PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")`; date defaults to `LocalDate.now(clock)` (A4); not paginated (bounded by one day).
2. `AppointmentController`: `POST` (roles OWNER, RECEPTIONIST; `BookingService` rejects vets) → 201 + Location; `GET` paginated via `AppointmentQueryService.list`; `GET /{id}`.

**Frontend**
1. `StatusBadge`: map of eight statuses → label + Tailwind colour classes; `role="status"`.
2. `SlotPicker`: `useTransition` to call `fetchSlots` whenever service/vet/date changes; clears selection on change; exposes `onSelect(slot)`.
3. `walk-in-form.tsx`: on submit `bookAppointment`; on `SLOT_TAKEN`/`SLOT_UNAVAILABLE` shows "That slot was just taken" and re-fetches slots; success → redirect `/appointments/{id}`.
4. `appointments/[id]/page.tsx`: server fetch; 404 → `notFound()`.
5. `AppointmentActions`: maps action → button; actions without a wired handler are not rendered.
6. `actions/*.ts`, `types.ts`, `AppHeader`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `GET /api/availability?serviceId=1&date=<next Monday>` → `200 [{veterinarianId, veterinarianName, startAt, endAt}]` for both vets; with `veterinarianId=1` → only that vet; past date → `200 []`; unknown service → `404 SERVICE_NOT_FOUND`; vet token → `403`.
- `POST /api/appointments` (receptionist) `{petId, serviceId, veterinarianId, startAt}` free future slot → `201 {status:"SCHEDULED", bookedBy:"RECEPTIONIST", total: basePrice+cost}`; same slot again → `409 SLOT_TAKEN`; past start → `400 START_IN_PAST`; outside schedule → `409 SLOT_UNAVAILABLE`; vet → `403`.
- Booked slot disappears from `GET /api/availability`.
- `GET /api/appointments/{id}` → includes `allowedActions` (receptionist on SCHEDULED: `START, CANCEL, RESCHEDULE, EDIT_OBSERVATIONS`).

**Frontend**
- `/reception/book`: changing service, vet or date refreshes slots; booking redirects to the detail page showing a "Scheduled" badge and booker "Reception".
- Booking a slot taken meanwhile → readable message and refreshed slot list.

#### Automated Tests
The tests prove the availability and booking HTTP contract and that two overlapping HTTP bookings cannot both succeed.

**Backend**
- `AvailabilityControllerTests` (slice)
  - **Success:** owner and receptionist → 200.
  - **Rejections:** vet → 403; no token → 401; missing `serviceId` → 400.
- `AppointmentControllerTests` (slice, mocked services)
  - **Success:** receptionist POST → 201 + Location; GET list paginated; GET detail.
  - **Rejections:** missing `petId` → 400; `SLOT_TAKEN` → 409; no token → 401.
- `WalkInBookingIntegrationTests` (integration, `@SpringBootTest` + MockMvc, real receptionist sign-in, seeded vets)
  - **Success:** walk-in → 201 SCHEDULED, slot gone from availability; two concurrent POSTs for the same slot → exactly one 201.
  - **Rejections:** the other → 409 `SLOT_TAKEN`.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the booking endpoints and HTTP-level concurrency safety. Live checks confirm reception books a walk-in that is immediately scheduled and the detail page shows a badge and only allowed actions.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- As reception (after registering Maria per I6), book Maria's dog for General consultation with `vet.ana` tomorrow 10:00 → detail shows Scheduled badge, booker Reception, total 60.00.
- Open `/reception/book` in two tabs, select the same slot, book in both → second shows "slot was just taken" and the slot is gone from the refreshed list.

### I8 — Online booking: reserve, confirm and reservation expiry

- **Status:** `pending`
- **Goal:** A signed-in owner picks a pet, service, optional vet and date, reserves a free slot, confirms it to schedule the appointment, and unconfirmed reservations expire and free the slot.
- **Covers:** Flow "Book online" (UI + API); lifecycle rows Reserved→Scheduled (owner, reception), Reserved→Cancelled (system, expired); acceptance "owner sees only free slots for chosen service, veterinarian and date and slots refresh when any change", "owner can reserve and confirm; unconfirmed reservation expires and slot becomes free".
- **Depends on:** I7

#### Files

**Backend**
- **Create:**
  - `backend/src/test/java/com/vetclinic/appointment/OnlineBookingIntegrationTests.java`.
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `POST /{id}/confirm`.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentControllerTests.java`: confirm cases.

**Frontend**
- **Create:**
  - `frontend/app/owner/book/page.tsx` + `online-booking-form.tsx`: own pet select → `SlotPicker` → Reserve → confirmation panel with countdown to `reservedUntil` and Confirm button.
- **Modify:**
  - `frontend/app/actions/appointments.ts`: `confirmAppointment(id)`.
  - `frontend/components/AppointmentActions.tsx`: wire `CONFIRM`.
  - `frontend/components/AppHeader.tsx`: owner "Book".

#### Implementation Tasks

**Backend**
1. `AppointmentController`: `POST /{id}/confirm` `@PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")` → `AppointmentActionService.confirm`.
2. Tests: as listed.

**Frontend**
1. `online-booking-form.tsx`: Reserve → `bookAppointment`; shows reservation panel; Confirm → `confirmAppointment` → redirect to detail; `RESERVATION_EXPIRED` → message "Reservation expired, pick a slot again" and refresh slots; `SLOT_TAKEN` → refresh slots.
2. `owner/book/page.tsx`: loads own pets, services, vets server-side.
3. `AppointmentActions`, `actions/appointments.ts`, `AppHeader`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/appointments` (owner, own pet) → `201 {status:"RESERVED", bookedBy:"OWNER", reservedUntil: now+10m}`; owner with other owner's pet → `404 PET_NOT_FOUND`.
- `POST /api/appointments/{id}/confirm` before expiry → `200 SCHEDULED`; after expiry → `409 RESERVATION_EXPIRED`; on SCHEDULED → `409 INVALID_TRANSITION`; other owner → `404`.
- After the sweep, the expired appointment is `CANCELLED` with `cancelReason: "Reservation expired"` and its slot reappears in availability.

**Frontend**
- `/owner/book`: changing service, vet or date refreshes slots; Reserve shows countdown; Confirm → detail with Scheduled badge.

#### Automated Tests
The tests prove owner reservation and confirmation over HTTP and that expiry frees the slot end to end.

**Backend**
- `AppointmentControllerTests` (slice, added cases)
  - **Success:** owner confirm → 200.
  - **Rejections:** vet confirm → 403; `RESERVATION_EXPIRED` → 409.
- `OnlineBookingIntegrationTests` (integration, real owner sign-in, test `Clock` bean advanced past TTL)
  - **Success:** owner reserves, slot leaves availability, confirm → SCHEDULED.
  - **Rejections:** second reservation, clock advanced, `expireDue()` → CANCELLED and slot back in availability; confirm → 409.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove reserve/confirm/expiry and slot release through the API. Live checks confirm the owner flow end to end, using a short TTL to observe expiry.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Sign in as owner `5550001` (registered per I6 after restart), Book: pick dog, Vaccination, no vet, tomorrow → slots for both vets; change date → list refreshes; Reserve then Confirm → Scheduled.
- Restart backend with `APP_BOOKING_RESERVATION_TTL=PT1M`, reserve without confirming, wait ~90 s → slot visible again in availability; Confirm → "Reservation expired" message.

### I9 — Owner's appointments and cancellation

- **Status:** `pending`
- **Goal:** Owners see all their appointments across pets and days with service, veterinarian, time and estimated duration, and cancel their own reserved or scheduled appointments with a reason.
- **Covers:** Flows "Follow up" (owner part), "Cancel" (owner part, endpoint shared with reception); lifecycle rows Reserved→Cancelled and Scheduled→Cancelled (owner); rule "owners see only their own appointments"; acceptance "owner sees all their appointments across pets and days with estimated duration and cannot see other owners' data", "cancelling without a reason is rejected".
- **Depends on:** I8

#### Files

**Backend**
- **Create:**
  - `backend/src/test/java/com/vetclinic/appointment/OwnerScopeIntegrationTests.java`.
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `POST /{id}/cancel`.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentControllerTests.java`: cancel cases.

**Frontend**
- **Create:**
  - `frontend/app/owner/appointments/page.tsx`: paginated list (pet, service, vet, date/time, estimated duration, status badge, link to detail) with loading/empty/error states.
  - `frontend/components/CancelDialog.tsx`: confirmation dialog with required reason textarea.
- **Modify:**
  - `frontend/app/actions/appointments.ts`: `cancelAppointment(id, reason)`.
  - `frontend/components/AppointmentActions.tsx`: wire `CANCEL` through `CancelDialog`.
  - `frontend/components/AppHeader.tsx`: owner "My appointments"; `frontend/app/page.tsx` owner redirect now targets it.

#### Implementation Tasks

**Backend**
1. `AppointmentController`: `POST /{id}/cancel` `@Valid CancelRequest`, roles OWNER, RECEPTIONIST → `AppointmentActionService.cancel`.
2. Tests: as listed.

**Frontend**
1. `owner/appointments/page.tsx`: `GET /api/appointments?page=` (backend scopes to owner).
2. `CancelDialog`: client; `role="dialog"`, focus trap basics, Confirm disabled while reason blank; shows the backend `VALIDATION_FAILED` message for field `reason`.
3. `AppointmentActions`, `actions/appointments.ts`, `AppHeader`, `app/page.tsx`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/appointments/{id}/cancel` `{reason:"Pet is ill"}` by owner on own SCHEDULED → `200 CANCELLED`; `{reason:""}` → `400 VALIDATION_FAILED` field `reason`; owner on IN_PROGRESS → `403 ACTION_NOT_ALLOWED`; on CANCELLED → `409 INVALID_TRANSITION`; other owner's id → `404`.
- `GET /api/appointments` as owner → only appointments of own pets, each with `service.durationMinutes`.

**Frontend**
- `/owner/appointments` lists appointments for all own pets across days with duration and badge.
- Cancel button appears only on Reserved/Scheduled; dialog refuses empty reason.

#### Automated Tests
The tests prove the cancel endpoint contract and that owners cannot read or act on other owners' appointments over HTTP.

**Backend**
- `AppointmentControllerTests` (slice, added cases)
  - **Success:** cancel with reason → 200.
  - **Rejections:** missing reason → 400; vet cancel → 403.
- `OwnerScopeIntegrationTests` (integration, two owners with real sign-in)
  - **Success:** owner A lists only A's appointments across two pets and two days.
  - **Rejections:** A GET/cancel B's appointment → 404; A `GET /api/pets?ownerId=B` → only A's pets.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove owner scoping and the cancellation contract. Live checks confirm the owner list and the cancel dialog.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- As owner, book two appointments for two pets on different days → both listed with duration.
- Cancel one with an empty reason → blocked; with a reason → Cancelled badge; slot available again.
- Sign in as the other owner → none of the first owner's appointments visible; opening its `/appointments/<id>` URL → not found.

### I10 — Visit: start, finish and observations

- **Status:** `pending`
- **Goal:** Veterinarians see their assigned appointments and, like reception, start and finish them and edit observations until closed or cancelled.
- **Covers:** Flow "Visit" (UI + API); Roles (Veterinarian); lifecycle rows Scheduled→In progress, In progress→Finished (veterinarian, reception); rule "observations can be edited until closed or cancelled".
- **Depends on:** I9

#### Files

**Backend**
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `POST /{id}/start`, `POST /{id}/finish`, `PATCH /{id}/observations`; bind `status` (comma-separated `AppointmentStatus` list) on `GET /api/appointments` into `AppointmentFilter.statuses`.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentControllerTests.java`: visit cases and `status` filter binding.

**Frontend**
- **Create:**
  - `frontend/app/vet/layout.tsx`: `requireRole("VETERINARIAN")`.
  - `frontend/app/vet/appointments/page.tsx`: assigned non-final appointments (status filter only, sorted by `startAt` ascending; no date filter in I10) with badges.
  - `frontend/components/ObservationsEditor.tsx`: client textarea + Save, shown only when `EDIT_OBSERVATIONS` allowed.
- **Modify:**
  - `frontend/app/actions/appointments.ts`: `startAppointment`, `finishAppointment`, `saveObservations`.
  - `frontend/components/AppointmentActions.tsx`: wire `START`, `FINISH`.
  - `frontend/app/appointments/[id]/page.tsx`: render `ObservationsEditor`.
  - `frontend/components/AppHeader.tsx`, `frontend/app/page.tsx`: vet nav and redirect.

#### Implementation Tasks

**Backend**
1. `AppointmentController`: endpoints with `@PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")`; `@Valid ObservationsRequest`; delegate to `AppointmentActionService`.
   - `list`: add `@RequestParam(required=false) List<AppointmentStatus> status` → `AppointmentFilter.statuses`; unknown value → `400 VALIDATION_FAILED`.

**Frontend**
1. `vet/appointments/page.tsx`: `GET /api/appointments?status=SCHEDULED,IN_PROGRESS,FINISHED`, rendered in the backend's `startAt` ascending order.
2. `ObservationsEditor`: `useActionState(saveObservations)`, shows saved/error messages.
3. Remaining files: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/appointments/{id}/start` by assigned vet on SCHEDULED → `200 IN_PROGRESS`; by another vet → `404`; by owner → `403`; on RESERVED → `409 INVALID_TRANSITION`.
- `POST /{id}/finish` on IN_PROGRESS → `200 FINISHED`; on SCHEDULED → `409`.
- `PATCH /{id}/observations` on FINISHED → `200`; on CANCELLED → `409 OBSERVATIONS_LOCKED`; >2000 chars → `400`.
- `GET /api/appointments?status=SCHEDULED,IN_PROGRESS` → only those statuses within the caller's scope; `?status=FOO` → `400 VALIDATION_FAILED`.

**Frontend**
- `/vet/appointments` as `vet.ana` shows only Ana's appointments; detail offers Start on Scheduled, Finish on In progress, observations editor until closed/cancelled.

#### Automated Tests
The tests prove the visit endpoint contract and role enforcement.

**Backend**
- `AppointmentControllerTests` (slice, added cases)
  - **Success:** vet start → 200; vet finish → 200; observations PATCH → 200; `status=SCHEDULED,IN_PROGRESS` reaches the service as a two-element `statuses` set.
  - **Rejections:** owner start → 403; observations too long → 400; `OBSERVATIONS_LOCKED` → 409; unknown `status` value → 400.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the visit endpoints. Live checks confirm the veterinarian's screen offers only the right actions.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- As reception, book a walk-in with `vet.ana`; sign in as `vet.ana` → it appears; Start → In progress badge; add observations; Finish → Finished.
- Sign in as `vet.luis` → Ana's appointment absent; its URL → not found.

### I11 — Reception ongoing appointments board

- **Status:** `pending`
- **Goal:** Reception sees all ongoing appointments in one screen with status badges and cancel actions, and can cancel any active appointment with a reason.
- **Covers:** Flow "Follow up" (reception part), "Cancel" (reception part); lifecycle rows Reserved/Scheduled/In progress→Cancelled (reception); acceptance "reception sees all ongoing appointments with cancel and payment actions" (cancel part), "cancelling without a reason is rejected".
- **Depends on:** I10

#### Files

**Backend**
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/dto/AppointmentFilter.java`: `ongoing` flag = statuses `RESERVED, SCHEDULED, IN_PROGRESS, FINISHED, PAID`.
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentQueryService.java`: apply `ongoing` (additive read filter).
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: bind `ongoing`, `date`, `veterinarianId` params.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentControllerTests.java`, `AppointmentQueryServiceTests.java`: board cases.

**Frontend**
- **Create:**
  - `frontend/app/reception/appointments/page.tsx`: board table (time, pet, owner, service, vet, badge, inline `AppointmentActions`), filters (date, vet), pagination, loading/empty/error.
- **Modify:**
  - `frontend/components/AppHeader.tsx`, `frontend/app/page.tsx`: receptionist "Appointments" link and redirect.

#### Implementation Tasks

**Backend**
1. `AppointmentFilter`, `AppointmentQueryService.list`: ongoing filter as listed; owners/vets keep their scope.
2. `AppointmentController`: query params.

**Frontend**
1. `reception/appointments/page.tsx`: `GET /api/appointments?ongoing=true&date=&veterinarianId=&page=`; inline actions use `AppointmentActions` (Cancel via `CancelDialog`, Start/Finish already wired).
2. `AppHeader`, `app/page.tsx`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `GET /api/appointments?ongoing=true` (receptionist) → all non-final appointments across owners/vets, paginated, each with `allowedActions`.
- `POST /{id}/cancel` by receptionist on IN_PROGRESS with reason → `200 CANCELLED`; without reason → `400`.

**Frontend**
- `/reception/appointments` shows every ongoing appointment with a badge; Cancel shown on Reserved/Scheduled/In progress only.

#### Automated Tests
The tests prove the ongoing filter returns all non-final appointments for reception and reception cancellation over HTTP.

**Backend**
- `AppointmentControllerTests` (slice, added cases)
  - **Success:** `ongoing=true` returns page from service.
  - **Rejections:** receptionist cancel without reason → 400.
- `AppointmentQueryServiceTests` (unit, added cases)
  - **Success:** `ongoing` returns RESERVED…PAID across owners and vets.
  - **Rejections:** CLOSED, CANCELLED, RESCHEDULED excluded.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the board query and reception cancellation. Live checks confirm the board shows all ongoing appointments with only allowed actions.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- As reception with appointments in Reserved, Scheduled and In progress → all appear; filter by vet narrows the list.
- Cancel the In progress one with a reason → disappears from the board; detail shows Cancelled with the reason.

### I12 — Reschedule and reassign

- **Status:** `pending`
- **Goal:** Reception moves a scheduled or in-progress appointment to another free time and/or veterinarian; the original becomes `RESCHEDULED` and a new linked `SCHEDULED` appointment is created.
- **Covers:** Flow "Reschedule or reassign" (UI + API); lifecycle rows Scheduled→Rescheduled, In progress→Rescheduled (reception, new slot available); rule "Rescheduled is final"; acceptance "rescheduling or reassigning marks the original as rescheduled and creates a linked scheduled appointment".
- **Depends on:** I11

#### Files

**Backend**
- **Create:**
  - `backend/src/test/java/com/vetclinic/appointment/RescheduleIntegrationTests.java`.
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `POST /{id}/reschedule`.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentControllerTests.java`: reschedule cases.

**Frontend**
- **Create:**
  - `frontend/app/appointments/[id]/reschedule/page.tsx` + `reschedule-form.tsx`: receptionist-only (`page.tsx` calls `requireRole("RECEPTIONIST")` server-side); `SlotPicker` preset with the appointment's service (service locked), vet selectable; Submit.
- **Modify:**
  - `frontend/app/actions/appointments.ts`: `rescheduleAppointment(id, vetId, startAt)`.
  - `frontend/components/AppointmentActions.tsx`: `RESCHEDULE` links to the reschedule page.
  - `frontend/app/appointments/[id]/page.tsx`: show "Rescheduled from #id" / "Rescheduled to #id" links.

#### Implementation Tasks

**Backend**
1. `AppointmentController`: `POST /{id}/reschedule` `@PreAuthorize("hasRole('RECEPTIONIST')")` `@Valid RescheduleRequest` → `AppointmentActionService.reschedule` → 201 + `Location` of the new appointment.
2. Tests: as listed.

**Frontend**
1. `appointments/[id]/reschedule/page.tsx`: `await requireRole("RECEPTIONIST")` before loading the appointment; other roles redirected to `/`.
2. `reschedule-form.tsx`: on success redirect to the new appointment; on `SLOT_TAKEN` refresh slots.
3. Remaining files: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/appointments/{id}/reschedule` `{veterinarianId, startAt}` on SCHEDULED → `201` new `{status:"SCHEDULED", rescheduledFromId:id}`, `Location` of new; original now `RESCHEDULED` with `rescheduledToId`; on IN_PROGRESS with other vet → same; on FINISHED → `409 INVALID_TRANSITION`; owner → `403`; taken slot → `409 SLOT_TAKEN` and original unchanged.
- Any action on the RESCHEDULED original → `409 INVALID_TRANSITION`.

**Frontend**
- Reschedule button only on Scheduled/In progress for reception; after submit, the new detail shows "Rescheduled from #id" and the original shows a Rescheduled badge.

#### Automated Tests
The tests prove the reschedule endpoint creates a linked appointment, conflicts leave the original untouched, and the original becomes final.

**Backend**
- `AppointmentControllerTests` (slice, added cases)
  - **Success:** receptionist reschedule → 201 + Location.
  - **Rejections:** missing `startAt` → 400; owner → 403.
- `RescheduleIntegrationTests` (integration, real receptionist sign-in)
  - **Success:** reschedule → original RESCHEDULED, new SCHEDULED linked, old slot free in availability.
  - **Rejections:** start on the RESCHEDULED original → 409; reschedule into a taken slot → 409 and original still SCHEDULED.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove linking, finality and rollback on conflict over HTTP. Live checks confirm reception can move and reassign appointments from the UI.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Reschedule a scheduled appointment to another day → redirected to new Scheduled appointment linked to the original (Rescheduled badge, no actions).
- Start an appointment, then reassign it to `vet.luis` → original Rescheduled, new Scheduled for Luis.

### I13 — Payments, closing and debts

- **Status:** `pending`
- **Goal:** Reception records one exact payment on a finished appointment, closes paid appointments, or closes finished unpaid ones as debts listed on a debts screen.
- **Covers:** Flow "Pay and close" (UI + API); lifecycle rows Finished→Paid, Finished→Closed (debt), Paid→Closed; rule "Closed is final"; acceptance "payment accepted only for a finished appointment and only for its exact total; second payment rejected", "finished appointment closed without payment appears as a debt", "reception sees all ongoing appointments with cancel and payment actions" (payment part).
- **Depends on:** I12

#### Files

**Backend**
- **Create:**
  - `backend/src/main/java/com/vetclinic/payment/PaymentController.java`: `POST /api/appointments/{id}/payments`.
  - `backend/src/test/java/com/vetclinic/payment/PaymentControllerTests.java`, `PaymentFlowIntegrationTests.java`.
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `POST /{id}/close`; bind `debt` query param.
  - `backend/src/test/java/com/vetclinic/appointment/AppointmentControllerTests.java`: close and debt cases.

**Frontend**
- **Create:**
  - `frontend/components/PaymentDialog.tsx`: confirm dialog showing the total, amount prefilled and editable, submit.
  - `frontend/app/reception/debts/page.tsx`: paginated debts list.
- **Modify:**
  - `frontend/app/actions/appointments.ts`: `payAppointment(id, amount)`, `closeAppointment(id)`.
  - `frontend/components/AppointmentActions.tsx`: wire `PAY` (dialog) and `CLOSE` (confirm; label "Close unpaid (debt)" on Finished, "Close" on Paid).
  - `frontend/components/StatusBadge.tsx`: "Debt" badge variant for closed unpaid.
  - `frontend/components/AppHeader.tsx`: receptionist "Debts".

#### Implementation Tasks

**Backend**
1. `PaymentController`: receptionist only, `@Valid PaymentRequest` → `PaymentService.pay` → 201.
2. `AppointmentController`: `POST /{id}/close` receptionist → `AppointmentActionService.close`; `debt` param into `AppointmentFilter`.

**Frontend**
1. `PaymentDialog`: amount mirrors backend; shows `AMOUNT_MISMATCH`/`ALREADY_PAID` messages.
2. `reception/debts/page.tsx`: `GET /api/appointments?debt=true`.
3. Remaining files: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `POST /api/appointments/{id}/payments` `{amount:"60.00"}` on FINISHED total 60.00 → `201 {id, appointmentId, amount:"60.00", paidAt}`, appointment PAID; `59.99` or `60.01` → `409 AMOUNT_MISMATCH`; on SCHEDULED → `409 INVALID_TRANSITION`; second payment → `409 ALREADY_PAID`; owner → `403`.
- `POST /{id}/close` on PAID → `200 CLOSED, debt:false`; on FINISHED → `200 CLOSED, debt:true`; on CLOSED → `409`.
- `GET /api/appointments?debt=true` → only closed unpaid.

**Frontend**
- Board shows Pay on Finished; after payment, Close; Close unpaid on Finished → appears under `/reception/debts` with a Debt badge.

#### Automated Tests
The tests prove the payment and close endpoint contract and the full pay/close/debt flow over HTTP.

**Backend**
- `PaymentControllerTests` (slice)
  - **Success:** receptionist → 201.
  - **Rejections:** missing amount → 400; negative → 400; owner/vet → 403.
- `AppointmentControllerTests` (slice, added cases)
  - **Success:** close → 200; `debt=true` bound into the filter.
  - **Rejections:** vet close → 403.
- `PaymentFlowIntegrationTests` (integration)
  - **Success:** walk-in → start → finish → pay → close → CLOSED, not debt.
  - **Rejections:** another finished → close unpaid → listed in `?debt=true`; pay after close → 409.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove payment exactness, single payment, closing rules and debt flagging through the API. Live checks confirm the payment and debt flow from the reception board.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Finish an appointment, Pay with 59.99 → mismatch message; pay 60.00 → Paid; Close → Closed, no actions offered.
- Finish another, Close unpaid → listed on Debts with a Debt badge.

### I14 — Pet service history

- **Status:** `pending`
- **Goal:** Reception opens a pet's service history listing its past and upcoming appointments; owners open the same history for their own pets (A9).
- **Covers:** Entity "Service history" (UI + API); flow "Follow up" (reception opens a pet's service history); acceptance "reception can open a pet's service history"; rule "owners see only their own pets" (history scope); assumption A9 (owner history view).
- **Depends on:** I13

#### Files

**Backend**
- **Create:**
  - `backend/src/test/java/com/vetclinic/appointment/PetHistoryTests.java`.
- **Modify:**
  - `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java`: `GET /api/pets/{id}/history` (mapped here to keep the appointment package owning appointment reads).

**Frontend**
- **Create:**
  - `frontend/app/reception/pets/[id]/history/page.tsx`: pet header, "Upcoming" and "Past" sections (service, vet, time, duration, total, badge, debt flag, link to detail).
  - `frontend/app/owner/pets/[id]/history/page.tsx`: owner's view of one own pet's history (A9); same sections, no debt flag; backend `404` → `notFound()`.
- **Modify:**
  - `frontend/app/reception/owners/[id]/page.tsx`: "History" link per pet.
  - `frontend/app/reception/appointments/page.tsx`: pet name links to its history.
  - `frontend/app/owner/pets/page.tsx`: "History" link per pet to `/owner/pets/{id}/history`.

#### Implementation Tasks

**Backend**
1. `AppointmentController`: endpoint, roles RECEPTIONIST, OWNER → `AppointmentQueryService.historyForPet`.

**Frontend**
1. `reception/pets/[id]/history/page.tsx`: splits by `upcoming`; empty state per section.
2. `owner/pets/[id]/history/page.tsx`: server component; `requireRole("OWNER")` via the owner layout; `apiGet("/api/pets/{id}/history")`; `ApiError` 404 → `notFound()`; splits by `upcoming` with empty states; each item links to `/appointments/{id}`.
3. Links: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `GET /api/pets/{id}/history` (receptionist) → `200 PageResponse<AppointmentResponse>` with all of the pet's appointments incl. cancelled/rescheduled/closed; owner on other owner's pet → `404`; vet → `403`.

**Frontend**
- From the board or owner detail, "History" opens `/reception/pets/{id}/history` with upcoming and past sections.
- Owner on `/owner/pets` clicks "History" → `/owner/pets/{id}/history` shows that pet's upcoming and past appointments; owner opening `/owner/pets/{otherOwnersPetId}/history` → not-found page.

#### Automated Tests
The tests prove the history endpoint returns all of a pet's appointments in order and respects access scope.

**Backend**
- `PetHistoryTests` (slice)
  - **Success:** receptionist gets past and upcoming items ordered; owner gets own pet's history.
  - **Rejections:** owner other pet → 404; vet → 403; unknown pet → 404.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove history content and access rules. Live checks confirm reception reaches the history from the board and owner detail, and owners reach only their own pets' history.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- As reception, click a pet on the board → history shows the closed, cancelled and upcoming appointments created in earlier checks.
- Sign in as owner `5550001` → `/owner/pets` → "History" on Maria's dog → its appointments appear; edit the URL to Juan's cat id → not-found page.

### I15 — Status badges and allowed-actions consistency

- **Status:** `pending`
- **Goal:** Every screen uses the same status badge and offers exactly the actions the backend reports as allowed for the current user.
- **Covers:** Acceptance "each status shows a consistent badge, and only allowed actions are offered"; React rules on loading/empty/error states, confirmation of destructive actions, accessibility.
- **Depends on:** I14

#### Files

**Backend**
- **Create:**
  - `backend/src/test/java/com/vetclinic/appointment/AllowedActionsResponseTests.java`: `allowedActions` in responses per role and status.

**Frontend**
- **Create:**
  - `frontend/app/owner/loading.tsx`, `frontend/app/vet/loading.tsx`, `frontend/app/reception/loading.tsx`, `frontend/app/appointments/loading.tsx`: route loading states.
  - `frontend/app/owner/error.tsx`, `frontend/app/vet/error.tsx`, `frontend/app/reception/error.tsx`, `frontend/app/appointments/error.tsx`: route error states with readable message and retry.
- **Modify:**
  - `frontend/components/StatusBadge.tsx`: final label/colour table, accessible text.
  - `frontend/components/AppointmentActions.tsx`: drive purely from `allowedActions`; confirmation on Cancel, Close, Pay; disabled state while pending.
  - `frontend/app/owner/appointments/page.tsx`, `frontend/app/vet/appointments/page.tsx`, `frontend/app/reception/appointments/page.tsx`, `frontend/app/reception/debts/page.tsx`, `frontend/app/reception/pets/[id]/history/page.tsx`, `frontend/app/owner/pets/[id]/history/page.tsx`, `frontend/app/appointments/[id]/page.tsx`: use `StatusBadge`/`AppointmentActions`/`EmptyState` uniformly.

#### Implementation Tasks

**Backend**
1. `AllowedActionsResponseTests`: assert `GET /api/appointments/{id}` returns lifecycle-consistent `allowedActions` for owner, vet and receptionist across all eight statuses, as an integration test (no mocked services or repositories).

**Frontend**
1. `StatusBadge`: single source of status → label/colour.
2. `AppointmentActions`: no status checks in pages; only `allowedActions`.
3. Pages and `loading.tsx`/`error.tsx`: as listed.

#### Expected Interfaces and Outputs

**Backend**
- `allowedActions` for owner on FINISHED → `[]`; receptionist on PAID → `["CLOSE","EDIT_OBSERVATIONS"]`; any role on CLOSED → `[]`.

**Frontend**
- The same status renders identically on every list and detail; final statuses show no action buttons.

#### Automated Tests
The tests prove the backend-reported actions match the lifecycle table for each role and status.

**Backend**
- `AllowedActionsResponseTests` (integration, `@SpringBootTest` + MockMvc with `TestJwt` tokens per role; appointments in each of the eight statuses inserted through the real repositories so `AppointmentQueryService` and `AppointmentLifecycle` run unmocked)
  - **Success:** each role/status returns the expected set.
  - **Rejections:** final statuses → empty set; owner never gets START/FINISH/PAY/CLOSE/RESCHEDULE.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove action sets per role and status. Live checks confirm badge and action consistency across every screen.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Visit owner, vet and reception lists plus a detail page for one appointment in each status → identical badges; actions match the lifecycle table for that role.

### I16 — Final acceptance pass

- **Status:** `pending`
- **Goal:** Every `TASK.md` acceptance criterion is verified end to end on a freshly started system, with an end-to-end integration test.
- **Covers:** All acceptance criteria (re-verification); seeded demo data; "backend builds and tests pass; frontend builds and lints cleanly".
- **Depends on:** I15

#### Files

**Backend**
- **Create:**
  - `backend/src/test/java/com/vetclinic/AcceptanceFlowIntegrationTests.java`: one happy path through register owner → owner reserve/confirm → vet start/finish → pay → close, with real sign-ins.

**Frontend**
- None.

#### Implementation Tasks

**Backend**
1. `AcceptanceFlowIntegrationTests`: as listed, using seeded receptionist and veterinarian.

**Frontend**
1. No code changes; fix only defects found by the manual checklist, each traced to a criterion.

#### Expected Interfaces and Outputs

**Backend**
- Seeded data on fresh start: one receptionist, two veterinarians with schedules, four services.

**Frontend**
- Every acceptance criterion passes the checklist below.

#### Automated Tests
The test proves the main flow works across roles with real tokens on the seeded system.

**Backend**
- `AcceptanceFlowIntegrationTests` (integration)
  - **Success:** full flow ends CLOSED with payment.
  - **Rejections:** second payment → 409; owner reading another owner's appointment → 404.

**Frontend**
- None in this increment; covered by lint, build and manual checks in Completion Criteria.

#### Completion Criteria
Automated checks prove the full build and the cross-role flow. The manual checklist walks every `TASK.md` acceptance criterion on freshly started apps.

**Automated**

Backend:
- `cd backend && ./mvnw verify` → all tests pass.

Frontend:
- `cd frontend && npm run lint` → no errors.
- `cd frontend && npm run build` → succeeds.

**Manual** (both apps running)
- Restart backend → sign in as `reception`, `vet.ana`, `vet.luis` succeed; Veterinarians shows two; booking shows four services.
- Walk each acceptance criterion: registration validation (missing name/phone, wrong breed, overlapping blocks), breed picker, slot refresh, reserve/confirm/expiry, double booking, walk-in booker, every transition per role, cancel without reason, reschedule/reassign link, exact single payment, debt listing, owner isolation, reception board and history, badges/actions → each behaves as specified.

## Plan Changelog

| Date | Mode | Increments | Change |
|---|---|---|---|
