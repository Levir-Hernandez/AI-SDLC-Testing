---
paths:
  - "frontend/**/*.test.ts"
  - "frontend/**/*.test.tsx"
  - "frontend/vitest.config.*"
  - "frontend/vitest.setup.*"
---

# React Tester

General guidelines for frontend quality and tests.

- ESLint is the required quality gate: `npm run lint` passes with no errors.
- Component tests are optional. If a test runner is introduced (e.g. Vitest + React Testing Library), it must be added through an approved increment and run with `npm test`.
- Test behavior, not implementation: query by role, label or text; test IDs only when no accessible alternative exists.
- Mock the backend at the API layer boundary; never call a real backend in unit tests.
- Worth covering when tests exist: list states (loading, empty, error, populated), role- and status-dependent actions, form validation and backend field errors, confirmation of destructive actions, and the shared components.
- Authentication behavior (redirect when signed out, no token reachable from client code) is verified at least manually before an increment is done.
