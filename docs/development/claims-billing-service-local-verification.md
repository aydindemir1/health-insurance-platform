# Claims and Billing Service local verification

This guide provides a focused order for learning and testing the implemented
Claims/Billing bounded context without revalidating earlier milestones.

## Reading order

1. Read `Claim` for adjudication transitions and amount rules.
2. Read `Invoice` for reconciliation, payment, and settlement rules.
3. Read `ClaimsBillingApplicationService` for roles, ownership, transaction
   composition, audit writes, and search projection intent.
4. Read `PreAuthorizationDecisionListener` for the Kafka contract boundary.
5. Read JPA adapters and Liquibase changesets for concurrency and uniqueness.
6. Use tests and live evidence to verify—not replace—the code reading.

## Fast automated verification

From `services/claims-billing-service`:

```powershell
.\mvnw.cmd --batch-mode test
```

Verified checkpoint:

- Java: `21.0.8`
- tests: `53`
- failures/errors/skips: `0/0/0`
- PostgreSQL integration runtime: Testcontainers `postgres:17-alpine`
- Kafka integration runtime: Testcontainers `apache/kafka-native:4.1.1`
- result: `BUILD SUCCESS`

The Mockito dynamic-agent message is a future-JDK compatibility warning, not a
failed business or integration test.

## Requirement-to-evidence traceability

| Concern | Owning implementation | Evidence |
| --- | --- | --- |
| claim transitions | `Claim` aggregate | `ClaimTest` |
| invoice reconciliation/payment | `Invoice` aggregate | `InvoiceTest` |
| role and provider ownership | application service plus REST annotations | application/controller tests |
| Claim + Invoice atomicity | transaction decorator/application wiring | transaction integration tests |
| concurrent writes | JPA `@Version` | PostgreSQL repository integration test |
| duplicate Kafka delivery | processed-message inbox and unique pre-authorization | Kafka integration test creates exactly one pair |
| poison event recovery | bounded retry and DLT | Kafka integration test reads original invalid payload from DLT |
| recoverable search indexing | claim-search transactional outbox | application and transaction tests |
| append-only minimized audit | audit port/JDBC adapter/Liquibase guards | audit use-case and transaction tests |
| dependency direction | Clean Architecture package rules | four ArchUnit tests |

## What the 53-test result means

The result demonstrates deterministic domain behavior, dependency wiring, real
PostgreSQL mappings/migrations, and real Kafka consumer retry/idempotency. It
does not claim production load capacity, external bank integration, or complete
end-to-end deployment health.

The hardening checkpoint additionally proves five Liquibase migrations against
both a fresh Testcontainer and the existing local dataset, 13 owner-table
lifecycle constraints, invalid rehydration rejection, allowlist-based inner
layer ArchUnit rules, and RFC 9457 filter-level `401/403` responses.

## Next live-runtime checkpoint

The next step should start only Claims/Billing dependencies already used by the
workflow, consume one synthetic approved Authorization event, and inspect:

- one `claims` row and its `SUBMITTED` status;
- one linked `invoices` row in `ISSUED`;
- one `processed_messages` idempotency marker;
- Claim/Invoice audit rows;
- one claim-search outbox row;
- Kafka source topic and DLT metadata;
- authenticated provider-scoped reads;
- review, approval, dispute/match, payment, and settlement transitions.

Separate PostgreSQL and Kafka screenshots should be generated from that live
run. Tokens, message payloads, member/policy/service values, money, payment
references, and credentials must not be rendered.

## Verified live checkpoint

A source-run service on port `8083` consumed the previously published synthetic
Authorization approval and created exactly one `SUBMITTED` Claim with one
`ISSUED` Invoice. Real role-bearing Keycloak tokens then drove:

```text
Claim:   SUBMITTED -> UNDER_REVIEW -> APPROVED
Invoice: ISSUED -> MATCHED -> SETTLED
```

A repeated claim approval returned `409`. PostgreSQL recorded Claim and Invoice
optimistic versions `2`, one payment row, six minimized audit actions, four
successive search-projection outbox snapshots, and the processed-message inbox
marker that links the Kafka event identity to `claims-pre-authorization-approved-v1`.

Capture the safe evidence without repeating the workflow:

```powershell
$env:CLAIMS_SCREENSHOT_CLAIM_ID = "<synthetic-claim-uuid>"
Set-Location apps/operations-portal
npm run screenshots:claims
```

## .NET comparison

| Java/Spring | .NET analogue |
| --- | --- |
| aggregate methods | rich domain entity methods |
| input port/use case | application command handler/service |
| JPA/Hibernate adapter | EF Core repository implementation |
| `@Version` | optimistic concurrency token/row version |
| Spring Kafka listener | MassTransit/Kafka consumer |
| processed-message table | consumer inbox/idempotency store |
| transactional outbox | EF Core transaction plus outbox entity |
| Spring transaction decorator | Unit of Work/application decorator |

For business meaning and state diagrams, see the
[Claims/Billing business analysis](../business/claims-billing-service-business-analysis.md).
