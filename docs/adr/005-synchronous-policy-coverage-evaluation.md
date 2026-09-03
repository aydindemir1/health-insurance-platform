# ADR-005: Synchronous policy coverage evaluation

- Status: Accepted
- Date: 2026-09-03

## Context

A hospital must know whether a member's policy covers the requested healthcare
service before a pre-authorization can be accepted. Policy validity, ownership,
benefits, currencies, and limits belong to the Policy bounded context. Copying
those rules or reading the Policy database from Authorization would create two
sources of truth and violate database-per-service ownership.

## Decision

Authorization calls the Policy Service's coverage-evaluation REST endpoint
synchronously before creating a pending pre-authorization. Its application
layer depends only on `CoverageVerificationPort`; the HTTP implementation is an
outbound infrastructure adapter. The request contains the policy number,
member, service code, amount, currency, and service date.

The caller's bearer token is relayed for this local milestone. Both services
still enforce application-level roles. Production service-to-service identity
will move to OAuth 2.0 client credentials or token exchange when the gateway and
security model are completed in Milestone 8; no client secret is committed now.

The call has short connection and response timeouts. Authorization fails closed
with `503 Service Unavailable` when Policy cannot be reached, and no
pre-authorization is persisted. A business denial returns `422 Unprocessable
Content` with a stable denial code.

Coverage evaluation is read-only in this milestone: it checks current used
amount plus the request but does not reserve the limit. Limit reservation,
release, and compensation require idempotency and a process manager and are
deferred to the event-driven milestone.

## Consequences

- Policy rules and data remain owned by one bounded context.
- The user gets an immediate, deterministic eligibility answer.
- Authorization availability now depends on Policy availability during submit.
- Timeouts bound the dependency, but retry and circuit-breaker policies remain
  future work; blindly retrying a future reservation command would be unsafe.
- Concurrent eligible evaluations can still over-admit until reservation and
  optimistic concurrency are implemented.

## Alternatives considered

- **Shared database:** simpler initially, but breaks ownership and independent
  evolution.
- **Replicated policy read model:** improves availability but introduces
  staleness and needs policy-change events and reconciliation.
- **Fully asynchronous validation:** removes the synchronous dependency but
  requires a `PENDING_VALIDATION` state and process manager before the hospital
  receives a final response.

