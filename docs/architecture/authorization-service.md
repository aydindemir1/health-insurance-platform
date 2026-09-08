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
    Outbox[Infrastructure / JPA outbox adapter] --> OutputPorts
    Relay[Infrastructure / Kafka relay] --> Kafka{{Kafka}}
    Persistence --> Database[(Authorization PostgreSQL)]
    PolicyAdapter --> Policy[Policy Service]
    Outbox --> Database
    Relay --> Database
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
- `infrastructure.messaging`: outbox persistence and at-least-once Kafka relay.
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

Approval/rejection and its outbox message are part of the same transaction.
The relay is deliberately outside the domain: broker delivery is an
infrastructure concern, while the application only depends on an outbox port.
See the [event-driven messaging view](event-driven-messaging.md).

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
