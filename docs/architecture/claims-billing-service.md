# Claims and Billing Service

## Responsibility and ownership

The service owns claim adjudication and the financial records derived from it:
claims, invoices, payment entries, reconciliation state, and settlement state.
It never reads the Authorization or Policy databases. Authorization remains the
source of truth for treatment approval; Policy remains the source of truth for
coverage rules and limits.

## Aggregate boundaries

`Claim` and `Invoice` are separate aggregate roots. Claim protects adjudication
rules and decision transitions. Invoice protects payable amount, reconciliation,
unique payment references, overpayment prevention, and settlement. They share a
local transaction because both are owned by this bounded context; approving or
rejecting a claim must update its invoice atomically.

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED: create from approved authorization
    SUBMITTED --> UNDER_REVIEW
    UNDER_REVIEW --> APPROVED
    UNDER_REVIEW --> REJECTED
```

```mermaid
stateDiagram-v2
    [*] --> ISSUED
    ISSUED --> MATCHED: full claim approval
    ISSUED --> DISPUTED: partial claim approval
    ISSUED --> VOID: claim rejected
    DISPUTED --> MATCHED: agree payable amount
    DISPUTED --> VOID: claim rejected
    MATCHED --> MATCHED: partial payment
    MATCHED --> SETTLED: paid amount equals payable amount
```

## Event-driven creation flow

```mermaid
sequenceDiagram
    participant K as Kafka
    participant C as Claims/Billing API
    participant DB as Claims/Billing PostgreSQL
    K->>C: PreAuthorizationApproved v1
    C->>DB: Check processed message ID
    alt First delivery
        C->>DB: Save Claim, Invoice, processed marker and search projection atomically
    else Duplicate delivery
        C-->>K: Successful no-op
    end
```

Application input/output ports keep the use cases framework independent. JPA,
REST, OAuth/JWT mapping, and transaction annotations live in infrastructure or
presentation. ArchUnit verifies these dependencies. PostgreSQL uniqueness
constraints prevent duplicate claims per pre-authorization and duplicate
invoice/payment references under concurrency; `@Version` protects updates.
The existing authenticated `POST /claims` path remains available for manual
submission compatibility and still verifies Authorization synchronously. New
approvals normally enter through Kafka and can be observed through the
provider-scoped `/claims/by-pre-authorization/{id}` query.

Every create/review/decision/reconciliation/payment transition also appends a
complete `ClaimSearchProjection` to `claim_search_outbox` inside the same local
transaction. A scheduled relay publishes version 1 to
`health.claims.search-projection.v1`. Search availability therefore cannot roll
back a financial command, while a committed command cannot lose its indexing
intent.
