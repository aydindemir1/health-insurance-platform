# ADR-004: Organize the operations portal by business capability

- Status: Accepted
- Date: 2026-09-03

## Context

The portal will grow from pre-authorization operations into policy, claims,
billing, and reporting workflows. A folder structure based only on technical
types such as `components`, `hooks`, and `services` would mix unrelated domain
capabilities and make ownership unclear.

## Decision

The React application uses a pragmatic Feature-Sliced dependency direction:

```text
app -> pages -> widgets -> features -> entities -> shared
```

- `app` composes providers, routing, error handling, and global styles.
- `pages` compose complete routes without owning reusable business behavior.
- `widgets` provide larger layout compositions such as the application shell.
- `features` implement user actions such as authentication, submission, and
  specialist decisions.
- `entities` contain domain-facing types, API functions, and visual primitives.
- `shared` contains domain-agnostic HTTP, configuration, state, and UI helpers.

A Vitest architecture test scans alias imports and rejects dependencies that
point upward through these layers. TanStack Query owns server state; API data is
not copied into a global client-state store.

## Consequences

- Business capabilities remain discoverable as the portal grows.
- Lower layers can be reused without depending on route or application setup.
- Some small features require more files than a flat starter application.
- Cross-feature imports are avoided; shared behavior must be deliberately
  promoted to an entity or shared module.
- No global state library is introduced until a concrete client-state problem
  requires one.
