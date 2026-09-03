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

## Request flow

```mermaid
sequenceDiagram
    actor Hospital
    participant C as Claims/Billing API
    participant A as Authorization API
    participant DB as Claims/Billing PostgreSQL
    Hospital->>C: POST /api/v1/claims + bearer token
    C->>A: GET approved pre-authorization + bearer token
    A-->>C: Provider-scoped snapshot
    C->>C: Verify APPROVED, owner, currency, amount
    C->>DB: Save Claim and Invoice in one transaction
    C-->>Hospital: 201 Claim + Invoice
```

Application input/output ports keep the use cases framework independent. JPA,
REST, OAuth/JWT mapping, and transaction annotations live in infrastructure or
presentation. ArchUnit verifies these dependencies. PostgreSQL uniqueness
constraints prevent duplicate claims per pre-authorization and duplicate
invoice/payment references under concurrency; `@Version` protects updates.
