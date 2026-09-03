# ADR-006: Claims and billing ownership and Authorization integration

- Status: Accepted
- Date: 2026-09-03

## Context

A claim may only start from an approved pre-authorization owned by the calling
healthcare provider. Adjudication then produces a payable amount, while invoice
reconciliation and payments continue on a different lifecycle. The design must
not duplicate Authorization rules or share another service's database.

## Decision

One Claims and Billing bounded context owns both `Claim` and `Invoice`, modeled
as separate aggregate roots in one private PostgreSQL database. Claim owns the
adjudication decision. Invoice owns billed/payable amounts, payment references,
disputes, and settlement. A transaction decorator makes claim decisions and
their invoice effect atomic inside this service.

For Milestone 4, claim creation is an explicit hospital command. The service
calls Authorization's REST query synchronously through
`ApprovedPreAuthorizationPort`, relays the caller token, and fails closed. It
requires an `APPROVED` snapshot, matches the trusted provider identity, and
rejects an invoice above the authorized amount. It never reads Authorization's
database. Unique constraints make one claim per pre-authorization and protect
invoice and payment references during races.

## Consequences

- Data ownership and audit boundaries are explicit.
- Users receive immediate validation when starting a claim.
- Claim creation temporarily depends on Authorization availability; short
  timeouts bound this dependency.
- Claim and invoice can evolve independently while local transactions preserve
  decision consistency.
- Payment is modeled as immutable entries within the invoice aggregate. A
  separate payment service is unnecessary at the current scale.

Milestone 5 will publish approved-authorization and claims lifecycle events with
transactional outboxes and idempotent consumers. That can support automatic
claim workflow initiation, but it will not remove the claim's uniqueness guard
or make Kafka a synchronous source of truth. Production service identity will
use client credentials or token exchange in Milestone 8.

## Alternatives considered

- **Shared Authorization database:** lower initial effort, but violates
  database-per-service ownership and couples schemas.
- **Claims and Billing as separate microservices now:** permits independent
  scaling but introduces distributed consistency before operational need exists.
- **Event-only creation now:** reduces runtime coupling, but requires Outbox,
  idempotency, and a process manager that belong to Milestone 5.
- **Invoice fields inside Claim:** simpler persistence, but conflates claim
  adjudication with payment and reconciliation invariants.
