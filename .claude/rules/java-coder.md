---
paths:
  - "backend/src/main/**"
  - "backend/pom.xml"
  - "backend/.mvn/**"
---

# Java Coder

General guidelines for backend production code, build and configuration. Domain decisions (entities, endpoints, statuses, rules) come from the task document and the approved plan, not from this file. Tests follow `java-tester.md`.

## Build and configuration
- Java 21 and Spring Boot 4.1.x with Web MVC, Data JPA, Validation and Security, built with the Maven Wrapper. Do not change versions unless explicitly requested.
- Add a dependency only when the task or an approved increment needs it; prefer Spring Boot managed starters and pin any version the Boot BOM does not manage.
- Database is H2 in-memory; the schema is generated from the JPA entities and data resets on restart. No migration tools, no other database engine, no Docker.
- Seed demo data on startup in code (e.g. a `CommandLineRunner`); keep it consistent with the task document.
- The API is documented with OpenAPI (springdoc).
- Configuration lives in `application.yml`; secrets such as the JWT signing key come from configuration/environment with a clearly marked development default. Never commit real secrets or machine-specific absolute paths.

## Security
- Authentication is stateless JWT: the backend issues a signed token on sign-in, carrying the user's identity and roles; no server-side session.
- Every protected endpoint validates the token and enforces roles; only the endpoints the task declares public are open.
- Passwords are stored hashed with a strong adaptive algorithm (e.g. BCrypt).
- Unauthenticated and forbidden requests return consistent JSON errors (401 / 403).
- Allow CORS only for the configured frontend origin.

## Code structure
- Package by feature under the root package; each feature keeps its entity, repository, service, controller and DTOs together. Cross-cutting code goes in shared/config packages.
- Controllers are thin: validate input, delegate to one service method, map the result. Business rules live in services.
- Expose request/response DTOs (Java `record`s), never JPA entities.
- Constructor injection only; no field injection or static mutable state.
- Any lifecycle/status logic is centralized in one component with explicit, documented rules per transition — never scattered across controllers, setters or ad-hoc conditionals.
- Business-rule violations raise domain exceptions mapped to HTTP responses in a single `@RestControllerAdvice`.
- Modifying service methods are `@Transactional`; read-only ones `@Transactional(readOnly = true)`.
- Use `java.time` and inject a `Clock` wherever the current time matters.
- Money uses `BigDecimal` with an explicit scale; never `double`/`float`.
- Index columns used for lookups and searches.
- Comment only non-obvious rules, citing the task rule they implement.

## REST conventions
- Resource-oriented URLs under a common API prefix, plural nouns, nesting only for strong ownership.
- Standard status codes: `201` (+ `Location`) on create, `200` read/update, `204` delete, `400` validation, `401`/`403` auth, `404` not found, `409` business-rule conflict.
- Errors use RFC 9457 `ProblemDetail`, with a machine-readable code and per-field errors for validation failures.
- Collections are paginated consistently.
- Validate request DTOs with Jakarta Validation (`@Valid`).

## Done
- `./mvnw verify` passes.
