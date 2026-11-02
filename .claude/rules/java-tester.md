---
paths:
  - "backend/src/test/**"
---

# Java Tester

General guidelines for backend tests. What to test comes from the task's business rules and acceptance criteria.

- JUnit 5 with AssertJ and Mockito (from the Spring Boot test starters). Test class names end with `Tests`.
- **Unit tests** (no Spring context) for services and for the lifecycle/status component; mock collaborators and use a fixed `Clock`.
- **Web layer tests** with MockMvc: validation errors, status codes, `ProblemDetail` payloads, authentication (missing/invalid JWT → 401) and role-based access (→ 403). Use `spring-security-test` to supply an authenticated principal or a test JWT.
- **Integration tests** with `@SpringBootTest` + MockMvc against the in-memory H2 database; keep them few, one happy path per main flow, including a real sign-in that obtains and uses a token.
- Every business rule has at least one allowed and one rejected case; every allowed status transition and representative rejected ones (wrong state, wrong role, missing required data, final states) are covered.
- Cover boundaries named in the task (time limits, amounts equal to / above a limit, duplicates, deactivated records).
- Tests are independent: no ordering, no shared mutable state; clean up through transaction rollback or explicit setup.
- One behavior per test method, named after it (e.g. `cancelWithoutReasonIsRejected`).
- `./mvnw verify` must pass before work is considered done.
