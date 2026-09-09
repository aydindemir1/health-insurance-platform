# Search, cache, and observability architecture

## Runtime responsibilities

```mermaid
flowchart LR
    Portal[Operations Portal] -->|JWT + X-Correlation-ID| SearchAPI[Search Service API]
    SearchAPI --> ES[(Elasticsearch operations projection)]
    Auth[Authorization Service] -->|coverage request| Policy[Policy Service]
    Policy -->|cache-aside| Redis[(Redis 30-second cache)]
    Policy -->|authoritative fallback| PolicyDB[(Policy PostgreSQL)]
    Auth -->|decision outbox| Kafka{{Kafka}}
    Claims[Claims and Billing] -->|projection outbox| Kafka
    Kafka -->|at-least-once projection events| SearchAPI
    Admin[SYSTEM_ADMIN recovery tool] -->|bounded owner snapshots| Auth
    Admin -->|bounded owner snapshots| Claims
    Admin -->|candidate ingestion and activation| SearchAPI
    Services[Five Java runtimes + worker] -->|traces and metrics| APM[APM Server]
    Services -->|ECS JSON with correlationId| Logs[Container log stream]
    APM --> Elastic[(Elastic Stack)]
    Logs -. ingest-ready .-> Elastic
    Elastic --> Kibana[Kibana]
```

## Cache-aside sequence and failure behavior

```mermaid
sequenceDiagram
    participant A as Authorization
    participant P as Policy use case
    participant R as Redis
    participant D as Policy PostgreSQL
    A->>P: Evaluate coverage
    P->>R: Read hashed input key
    alt cache hit
        R-->>P: Coverage decision
    else miss or Redis unavailable
        R-->>P: Miss or adapter logs safe warning
        P->>D: Load authoritative policy
        D-->>P: Policy aggregate
        P->>P: Evaluate domain rules
        P->>R: Store with 30-second TTL
        Note over P,R: Store failure does not change decision
    end
    P-->>A: Covered or denied
```

The digest prevents policy/member identifiers from appearing in Redis keys. The
cached value is an evaluation result, not a mutable Policy aggregate. Invalidation
uses a per-policy Redis set so a successful policy creation removes known entries.

## Search projection sequence

```mermaid
sequenceDiagram
    participant C as Claims application
    participant DB as Claims PostgreSQL
    participant O as Search outbox relay
    participant K as Kafka
    participant S as Search consumer
    participant E as Elasticsearch
    C->>DB: Save Claim/Invoice + projection outbox
    Note over C,DB: One local transaction
    O->>DB: Lock unpublished batch
    O->>K: Versioned projection, key = claim ID
    K-->>O: Broker acknowledgement
    O->>DB: Mark published
    K->>S: At-least-once delivery
    S->>E: Conditional upsert by deterministic ID + sourceRevision
    Note right of S: Equal or newer replaces and stale revisions are ignored
```

Search documents contain only operational identifiers and financial workflow
fields required by the portal. Search does not authorize commands and cannot be
used to reconstruct an aggregate. A rebuild uses current, bounded snapshots
from the Authorization and Claims/Billing owner APIs; it never reads their
databases or assumes Kafka retention contains a complete history. Consumer failures receive three total
fixed-backoff attempts by default and then the original record is sent to the
source topic's `.DLT`; invalid versions and malformed projections therefore do
not block a partition forever.

## Versioned rebuild and rollback

```mermaid
sequenceDiagram
    actor Operator as SYSTEM_ADMIN operator
    participant Tool as Recovery orchestrator
    participant Auth as Authorization export API
    participant Claims as Claims export API
    participant Search as Search rebuild API
    participant ES as Elasticsearch

    Operator->>Tool: Start with short-lived token
    Tool->>Search: Create(schemaVersion)
    Search->>ES: Create isolated candidate
    Search-->>Tool: runId, candidate, expected predecessor
    loop Stable pages, maximum 200 records
        Tool->>Auth: Export page ordered by createdAt + id
        Auth-->>Tool: Current projections + sourceRevision
        Tool->>Claims: Export page ordered by claim id
        Claims-->>Tool: Joined financial projections + sourceRevision
        Tool->>Search: Ingest validated distinct records
        Search->>ES: Conditional upsert into candidate
    end
    Tool->>Search: Activate(distinctDocumentCount)
    Search->>ES: Refresh and count candidate
    Search->>ES: Compare alias with expected predecessor
    Search->>ES: Atomic remove-old/add-new alias update
    Search-->>Tool: ACTIVE with predecessor retained
    opt Explicit rollback in same process lifetime
        Tool->>Search: Rollback(runId)
        Search->>ES: Compare-and-swap alias to predecessor
    end
```

Normal queries and event writes target the stable `healthcare-operations`
alias. Physical candidates use `healthcare-operations-v{schema}-{opaqueRunId}`.
Activation never deletes an index. Authorization uses its aggregate revision;
Claims/Billing combines Claim and Invoice revisions so either transition advances
the projection. Legacy documents without the new field map to revision 1 until
a rebuild replaces them.

Candidate count mismatch or concurrent alias movement returns RFC 9457 `409`
and leaves the current read path untouched. The initial run registry is
in-memory and therefore supports a local rehearsal, not a restart-resumable
production job. See the
[recovery runbook](../operations/search-and-messaging-recovery.md).

## Correlation propagation

```mermaid
flowchart LR
    Browser -->|new X-Correlation-ID| FilterA[Authorization HTTP filter]
    FilterA -->|MDC + same header| PolicyClient[Policy REST client]
    FilterA -->|response header| Browser
    KafkaEvent[Kafka eventId] --> KafkaMDC[Consumer MDC correlationId]
    RabbitTask[Rabbit causationId/taskId] --> RabbitMDC[Worker MDC correlationId]
    PolicyClient --> ECS[ECS JSON logs]
    KafkaMDC --> ECS
    RabbitMDC --> ECS
    ECS --> Operator[Log/APM investigation]
```

The filter removes MDC in `finally`, preventing thread-pool leakage. Incoming IDs
are limited to 64 alphanumeric, dot, underscore or hyphen characters. Logs use
safe IDs and state; member health data, access tokens and message payloads must
not be logged.

## Consistency matrix

| Concern | Source of truth | Consistency | Failure behavior |
| --- | --- | --- | --- |
| Policy and coverage | Policy PostgreSQL | Strong in local transaction | Redis bypass; database failure denies submission |
| Claim/invoice state | Claims PostgreSQL | Strong in local transaction | Search intent remains in outbox |
| Operations search | Elasticsearch alias over a derived index | Eventual | Core writes continue; failed candidate never activates |
| Trace/metrics | APM Server/Elasticsearch | Best effort | Business processing continues |
| Console logs | Container stdout | Immediate per process | Remain available if APM is unavailable |
