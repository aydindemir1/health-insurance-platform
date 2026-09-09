# Authorization Service components

The Authorization Service uses Clean Architecture inside its bounded context.

```mermaid
flowchart LR
    Client[Hospital or insurer client] --> Presentation[Presentation / REST]
    Presentation --> InputPorts[Application input ports]
    InputPorts --> UseCases[Application use cases]
    UseCases --> Domain[Domain aggregate]
    UseCases --> OutputPorts[Application output ports]
    Persistence[Infrastructure / JPA adapter] --> OutputPorts
    PolicyAdapter[Infrastructure / Policy REST adapter] --> OutputPorts
    EventOutbox[Infrastructure / Kafka event outbox adapter] --> OutputPorts
    TaskOutbox[Infrastructure / notification task outbox adapter] --> OutputPorts
    AuditAdapter[Infrastructure / insert-only audit adapter] --> OutputPorts
    AuditQueryAdapter[Infrastructure / bounded audit query adapter] --> OutputPorts
    Relay[Infrastructure / Kafka relay] --> Kafka{{Kafka}}
    NotificationRelay[Infrastructure / confirm-aware AMQP relay] --> Rabbit{{RabbitMQ}}
    Persistence --> Database[(Authorization PostgreSQL)]
    PolicyAdapter --> Policy[Policy Service]
    EventOutbox --> Database
    TaskOutbox --> Database
    AuditAdapter --> Database
    AuditQueryAdapter --> Database
    Relay --> Database
    NotificationRelay --> Database
    Configuration[Infrastructure / transaction configuration] --> InputPorts
    Keycloak[Keycloak] --> Presentation
```

## Package responsibilities

- `domain.model`: aggregate state and business invariants.
- `domain.exception`: domain-specific rule violations.
- `application.port.in`: operations exposed by the application core.
- `application.port.out`: capabilities required from infrastructure.
- `application.usecase`: orchestration, ownership, and authorization policies.
- `application.command` and `application.query`: explicit use-case inputs.
- `application.dto`: framework-independent use-case results.
- `infrastructure.persistence`: JPA entities, Spring Data, and repository adapter.
- `infrastructure.messaging`: Kafka and notification outbox persistence,
  at-least-once relays, safe wire mapping, and RabbitMQ topology.
- `infrastructure.security`: OAuth2 resource-server configuration.
- `infrastructure.configuration`: dependency wiring and transaction boundaries.
- `presentation.rest`: HTTP requests, responses, validation, and Problem Details.

The application service remains framework-free. An infrastructure decorator
wraps its input ports in Spring-managed transactions: commands use read/write
transactions and queries use read-only transactions.

## Paginated work queue

The list operation uses application-owned `SearchPreAuthorizationsQuery`,
`PreAuthorizationSearchCriteria`, and `PageResult` types. Spring Data `Page`,
`Pageable`, and `Specification` remain inside the persistence adapter.

```mermaid
sequenceDiagram
    participant Portal
    participant REST as REST controller
    participant App as Search use case
    participant Repo as Repository port
    participant JPA as JPA adapter

    Portal->>REST: GET collection + filters + bearer token
    REST->>App: Query with ActorContext
    App->>App: Resolve provider scope from roles
    App->>Repo: Framework-free search criteria
    Repo->>JPA: Filtered and stable paginated query
    JPA-->>App: PageResult of aggregates
    App-->>REST: PageResult of DTOs
    REST-->>Portal: Page response
```

Hospital users always receive a provider-scoped query. Insurance specialists
and system administrators can search across providers. Sort fields are
explicitly allow-listed, and `id` is added as a deterministic tie-breaker so
records do not move unpredictably between pages when primary sort values match.

## Concurrent decisions

The JPA entity has a version column. If two specialists load the same pending
request, the first decision increments that version and the second update no
longer matches the database row. The persistence adapter flushes inside the
transaction boundary and translates Spring's optimistic-lock exception into an
application conflict. The REST boundary returns an RFC 9457 `409 Conflict`
response with the `concurrent-update` problem type.

## Decision event flow

Approval/rejection, its Kafka event, and its minimal notification task are part
of the same transaction. Separate output ports and tables prevent the Kafka
relay from accidentally publishing a RabbitMQ command. Broker relays remain
outside the domain; the application depends only on outbox ports.
See the [event-driven messaging view](event-driven-messaging.md).

## Transactional audit evidence

Submission, approval, and rejection append a minimized `AuditRecord` through an
application output port. The application contract is framework-free; an MDC
context adapter supplies the validated correlation ID and a JDBC adapter writes
the local journal. The transaction decorator encloses the aggregate save, audit
append, Kafka outbox append, and notification-task append.

```mermaid
sequenceDiagram
    participant API as REST / verified actor
    participant App as Application use case
    participant Aggregate as PreAuthorization
    participant Repo as Aggregate repository
    participant Audit as AuditTrail port
    participant Outboxes as Event and task outboxes
    participant DB as Authorization PostgreSQL

    API->>App: submit or decide + ActorContext
    App->>Aggregate: enforce role, ownership, state rules
    App->>Repo: save state
    Repo->>DB: aggregate mutation
    App->>Audit: append controlled action and status delta
    Audit->>DB: INSERT audit_records
    opt decision transition
        App->>Outboxes: append Kafka event and notification task
        Outboxes->>DB: INSERT outbox rows
    end
    alt any write fails
        DB-->>App: rollback aggregate, audit and outboxes
    else all writes succeed
        DB-->>App: commit one local transaction
    end
```

The journal deliberately omits member/policy identifiers, diagnosis/service
codes, money, free-text reasons, tokens, and request bodies. PostgreSQL rejects
row update/delete and table truncation. This is strong application-level
append-only protection, but not cryptographic immutability against a privileged
database administrator; that residual control belongs to later backup/WORM and
operational governance work.

## Privileged audit query

`GET /api/v1/audit-records` maps HTTP parameters into the framework-independent
`SearchAuditRecordsQuery` input port. The controller rejects callers without
`SYSTEM_ADMIN`, and the application use case repeats that check so alternate
adapters cannot bypass it. The query adapter accepts only an optional aggregate
UUID and an allowlisted Authorization action, caps page size at 100, and sorts by
`occurred_at DESC, audit_id DESC`. It maps rows directly to minimized application
DTOs; business aggregates and sensitive source columns are never joined.

```mermaid
sequenceDiagram
    participant Admin as SYSTEM_ADMIN portal
    participant Gateway as APISIX
    participant API as Audit REST controller
    participant App as Search audit use case
    participant JDBC as Audit query adapter
    participant DB as Authorization PostgreSQL

    Admin->>Gateway: GET /api/v1/audit-records + JWT
    Gateway->>API: Validated bearer request
    API->>API: Require SYSTEM_ADMIN
    API->>App: bounded query + ActorContext
    App->>App: Require SYSTEM_ADMIN again
    App->>JDBC: service-local criteria
    JDBC->>DB: deterministic paged SELECT
    DB-->>JDBC: minimized rows + count
    JDBC-->>Admin: page through App, API, Gateway
```

The notification relay uses a pessimistic write lock to keep concurrent service
instances from selecting the same pending batch. It sends a persistent message
to a durable direct-exchange route, waits for the correlated broker confirm,
and also checks mandatory publisher returns. A `nack`, timeout, serialization
failure, or unroutable return increments the attempt count and leaves the task
unpublished for the next scheduled poll.

## Submit flow

```mermaid
sequenceDiagram
    participant Client
    participant REST as REST controller
    participant App as Submit use case
    participant Domain as PreAuthorization
    participant Policy as Policy Service
    participant Repo as Repository port

    Client->>REST: POST request + bearer token
    REST->>REST: Map verified provider_id and roles
    REST->>App: Command with ActorContext
    App->>App: Require HOSPITAL_USER and provider identity
    App->>Policy: Verify policy coverage through output port
    Policy-->>App: Eligible or denial code
    App->>Domain: Submit using trusted provider UUID
    App->>Repo: Save aggregate
    Repo-->>App: Persisted aggregate
    App-->>REST: Application result
    REST-->>Client: 201 Created
```
