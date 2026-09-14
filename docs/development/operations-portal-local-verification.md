# Operations Portal local verification

This guide verifies and explains the React/TypeScript client without changing
backend domain behavior.

## Reading order

1. `src/app/router/AppRouter.tsx` — lazy routes and page authorization.
2. `src/features/authentication/model/AuthProvider.tsx` — Keycloak session and
   HTTP token integration.
3. `src/shared/api/http-client.ts` — timeout, bearer token, correlation ID,
   RFC 9457 parsing and response schema validation.
4. `src/entities/*/api` — server-state contracts and query keys.
5. `src/features/submit-pre-authorization` and
   `review-pre-authorization` — command-oriented UI behavior.
6. `src/pages` — URL-backed filters, pagination and composed screens.
7. `src/app/architecture.test.ts` — enforced FSD dependency direction.

## Automated checkpoint

From `apps/operations-portal`:

```powershell
npm run lint
npm test
npm run build
```

Verified on 2026-09-14:

```text
Oxlint:          passed
Vitest:          13 files, 22 tests passed
TypeScript/Vite: production build passed, 258 modules transformed
Playwright:      1 real Chrome workflow passed
```

The browser workflow used real Keycloak Authorization Code + PKCE and live
Authorization/Policy services. A hospital user submitted a covered request,
saw `PENDING`, signed out, and an insurance specialist approved the same request
to `APPROVED`. Passwords and tokens existed only in process memory.

When APISIX is intentionally stopped during isolated frontend learning, set
`VITE_API_BASE_URL=http://localhost:8081/api/v1` and
`VITE_SEARCH_API_BASE_URL=http://localhost:8084/api/v1`. The portfolio demo uses
the default APISIX origin and tests gateway policy separately.

## Contract defect found

Zod 4 `z.uuid()` requires RFC version/variant bits, whereas Java
`java.util.UUID` accepts canonical UUID text without that restriction. The
synthetic member identifier was therefore rejected in the browser before any
API call. `shared/lib/validation.ts` now exposes one `javaUuid()` schema used by
forms, URL filters and response schemas. A focused unit test protects both the
accepted Java-compatible form and malformed-input rejection.

## Architecture interpretation

TanStack Query owns remote state and mutation lifecycle. React Hook Form owns
short-lived form state. Zod validates both user input and untrusted API payloads.
Keycloak authentication is centralized, but navigation visibility is only a UX
control; backend authorization remains authoritative. Feature and entity layers
can depend on `shared`, while the architecture test rejects upward imports.

## .NET comparison

| React implementation | ASP.NET-oriented analogue |
| --- | --- |
| TanStack Query | typed API client plus server-state cache |
| React Hook Form + Zod | EditForm/Formik plus FluentValidation-style schema |
| Keycloak JS + PKCE | MSAL/OIDC browser client |
| Error boundary | Blazor error boundary or top-level UI exception boundary |
| `ApiError`/Problem Details | `ProblemDetails` client mapping |
| FSD architecture test | project-reference/dependency architecture test |

