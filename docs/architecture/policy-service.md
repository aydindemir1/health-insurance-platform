# Policy Service components

The Policy Service owns policies, coverage definitions, validity periods, and
financial limits. No other service reads its PostgreSQL database.

```mermaid
flowchart LR
    Client[Insurer operations] --> REST[Policy REST API]
    Authorization[Authorization Service] -->|Coverage evaluation REST| REST
    REST --> Ports[Application input ports]
    Ports --> UseCases[Policy use cases]
    UseCases --> Aggregate[Policy aggregate]
    UseCases --> RepoPort[Policy repository port]
    JPA[JPA adapter] --> RepoPort
    JPA --> DB[(Policy PostgreSQL)]
    Keycloak[Keycloak] --> REST
```

## Aggregate model

`Policy` is the aggregate root. It owns its validity period, lifecycle status,
member identity, and a unique set of `Coverage` entries indexed by
`ServiceCode`. `Money` protects non-negative amounts and currency-safe
arithmetic. A policy cannot be issued without coverage, with reversed validity
dates, duplicate service codes, or used amounts above a limit.

Evaluation produces a `CoverageDecision` instead of leaking persistence or HTTP
types into the domain. Stable outcomes include member mismatch, inactive or
expired policy, uncovered service, currency mismatch, and exceeded limit.

## Pre-authorization validation

```mermaid
sequenceDiagram
    participant Portal
    participant Authorization
    participant Policy
    participant PolicyDB as Policy PostgreSQL
    participant AuthorizationDB as Authorization PostgreSQL

    Portal->>Authorization: Submit pre-authorization + bearer token
    Authorization->>Authorization: Check hospital role and provider ownership
    Authorization->>Policy: Evaluate policy/member/service/amount/date
    Policy->>PolicyDB: Load policy by number
    Policy->>Policy: Apply validity, coverage, currency, and limit rules
    Policy-->>Authorization: Eligible or stable denial code
    alt eligible
        Authorization->>AuthorizationDB: Save pending pre-authorization
        Authorization-->>Portal: 201 Created
    else business denial
        Authorization-->>Portal: 422 Problem Details
    else Policy unavailable
        Authorization-->>Portal: 503 Problem Details
    end
```

The evaluation is deliberately query-like and does not consume or reserve a
limit yet. Reservation becomes a state-changing, idempotent operation with
optimistic concurrency and compensation in the event-driven milestone.

