---
paths:
  - "frontend/app/**"
  - "frontend/components/**"
  - "frontend/lib/**"
  - "frontend/package.json"
  - "frontend/next.config.ts"
---

# React Coder

General guidelines for frontend production code and configuration. Screens, flows and rules come from the task document and the approved plan. Tests follow `react-tester.md`.

## Build and configuration
- Next.js 16 (App Router), React 19, TypeScript (strict) and Tailwind CSS, managed with npm. Do not change versions unless explicitly requested.
- Add dependencies with `npm install` so `package-lock.json` stays consistent; no second UI framework without an approved increment.
- The backend base URL comes from configuration (environment variable with a local default), never hard-coded in components.

## Authentication
- The JWT is never exposed to browser scripts: it is stored by Next.js in an `HttpOnly`, `Secure`, `SameSite` cookie set on the server side (route handler or server action) after sign-in.
- Requests to the backend that need authentication go through server-side code (route handlers, server actions or server components) that reads the cookie and forwards the token; no token in `localStorage`, `sessionStorage` or client state.
- Sign-out clears the cookie. Protected routes redirect to sign-in when there is no valid session; role-specific screens check the role server-side.

## Code
- Routes in `app/`, reusable components in `components/`, API access and helpers in `lib/`.
- All backend calls go through one typed API layer; components never call the backend directly.
- API types live in one place and mirror the backend DTOs.
- Use `"use client"` only where interactivity is needed.
- Style with Tailwind utilities. Repeated UI elements (badges, dialogs, empty states, pagination, form fields with errors) are shared components.
- Show only the actions the current user may perform; the backend still enforces every rule.
- Forms mirror backend validation and show backend field errors next to their fields; server errors are shown as readable messages, never raw JSON.
- Lists have loading, empty and error states. Destructive actions ask for confirmation.
- Accessible (labels, roles, keyboard) and responsive.

## Done
- `npm run lint` and `npm run build` pass (and `npm test` if a test suite exists).
