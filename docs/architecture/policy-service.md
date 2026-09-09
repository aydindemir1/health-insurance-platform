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
    UseCases --> CachePort[Coverage evaluation cache port]
    UseCases --> AuditPort[Audit write and query ports]
    Redis[Redis adapter] --> CachePort
    Redis --> Cache[(Redis)]
    JPA[JPA adapter] --> RepoPort
    JPA --> DB[(Policy PostgreSQL)]
    Audit[Insert-only JDBC audit adapters] --> AuditPort
    Audit --> DB
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
    participant Redis
    participant AuthorizationDB as Authorization PostgreSQL

    Portal->>Authorization: Submit pre-authorization + bearer token
    Authorization->>Authorization: Check hospital role and provider ownership
    Authorization->>Policy: Evaluate policy/member/service/amount/date
    Policy->>Redis: Read hashed evaluation key
    alt cache hit
        Redis-->>Policy: Cached immutable decision
    else miss or Redis unavailable
        Policy->>PolicyDB: Load policy by number
        Policy->>Policy: Apply validity, coverage, currency, and limit rules
        Policy->>Redis: Store for 30 seconds
    end
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

Redis is an acceleration adapter, not policy storage. It hashes the full lookup
identity, tracks keys per policy for invalidation, and fails open on every cache
operation. PostgreSQL remains authoritative and its failure is never converted
into an assumed eligible response.

## Transactional policy audit

Issuing a policy appends `POLICY_ISSUED` evidence in the same local transaction
as the aggregate. The typed record contains actor subject/roles, correlation ID,
controlled reason/status values, and retention class; it cannot carry member ID,
policy number, coverage/service codes, limits, or other business snapshots. A
Liquibase migration installs JSON-shape checks and triggers rejecting update,
delete, and truncate. Audit failure rolls back policy issuance.

`GET /api/v1/policies/audit-records` is independently protected in the controller
and application use case with `SYSTEM_ADMIN`. It accepts an optional aggregate
UUID, the service-local action allowlist, and bounded pagination up to 100 rows.
The JDBC query uses `occurred_at DESC, audit_id DESC`. It does not expose Policy
tables or provide a database join to another service.

```mermaid
sequenceDiagram
    participant Admin as SYSTEM_ADMIN portal
    participant API as Policy audit API
    participant App as Audit query use case
    participant DB as Policy PostgreSQL
    Admin->>API: GET audit records + bounded filters
    API->>API: Require SYSTEM_ADMIN
    API->>App: query + verified actor
    App->>App: Require SYSTEM_ADMIN
    App->>DB: paged read of local audit_records
    DB-->>Admin: minimized deterministic page
```
