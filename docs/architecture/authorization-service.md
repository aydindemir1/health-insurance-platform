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
    Persistence --> Database[(Authorization PostgreSQL)]
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
- `infrastructure.security`: OAuth2 resource-server configuration.
- `infrastructure.configuration`: dependency wiring and transaction boundaries.
- `presentation.rest`: HTTP requests, responses, validation, and Problem Details.

## Submit flow

```mermaid
sequenceDiagram
    participant Client
    participant REST as REST controller
    participant App as Submit use case
    participant Domain as PreAuthorization
    participant Repo as Repository port

    Client->>REST: POST request + bearer token
    REST->>REST: Map verified provider_id and roles
    REST->>App: Command with ActorContext
    App->>App: Require HOSPITAL_USER and provider identity
    App->>Domain: Submit using trusted provider UUID
    App->>Repo: Save aggregate
    Repo-->>App: Persisted aggregate
    App-->>REST: Application result
    REST-->>Client: 201 Created
```
