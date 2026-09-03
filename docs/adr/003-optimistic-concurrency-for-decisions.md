# ADR-003: Protect pre-authorization decisions with optimistic concurrency

- Status: Accepted
- Date: 2026-09-03

## Context

Two insurance specialists may open the same pending pre-authorization and
submit different decisions at nearly the same time. Checking the status only in
the aggregate is insufficient because both transactions can observe `PENDING`
before either commits.

## Decision

The persistence entity uses a JPA `@Version` column. Updates therefore include
the version read by the transaction. The repository adapter uses
`saveAndFlush` so a stale write is detected before control leaves the adapter,
then translates Spring's `OptimisticLockingFailureException` into the
framework-independent `ConcurrentPreAuthorizationUpdateException` application
exception. The REST exception handler represents it as an RFC 9457
`409 Conflict` response.

PostgreSQL Testcontainers tests verify both the Liquibase version column and a
real stale-update conflict. Unit and MVC slice tests verify exception
translation and the HTTP contract.

## Consequences

- Concurrent decisions cannot silently overwrite each other.
- Normal reads do not acquire database locks.
- A conflicting caller must reload the latest state before deciding again.
- Flushing each aggregate write adds a database round trip, but makes technical
  persistence failures translatable inside the adapter.
- Pessimistic locking was rejected because it holds database locks while a
  transaction is active and reduces throughput.
- An HTTP `ETag`/`If-Match` contract remains a useful future addition for more
  general edit workflows, but is not required for the current decision command.
