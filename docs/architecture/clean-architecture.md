# Clean Architecture Bileşen Modeli

Üç backend servisin tamamı, yalnızca use case'lerinin ihtiyaç duyduğu
abstraction'ları koruyarak aynı dependency rule'u kullanır.

```mermaid
flowchart LR
    subgraph Presentation["presentation/rest"]
        Controller["REST controllers"]
        Request["Validated request records"]
        Response["Response records"]
        Advice["RFC 9457 exception advice"]
        ActorMapper["JWT → ActorContext"]
    end

    subgraph Application["application"]
        InPorts["Input ports / use-case contracts"]
        Commands["Commands and queries"]
        UseCases["Application services"]
        OutPorts["Repository and integration output ports"]
        Results["Result DTOs and mappers"]
    end

    subgraph Domain["domain — framework independent"]
        Aggregates["Aggregate roots<br/>PreAuthorization, Policy,<br/>Claim, Invoice"]
        Values["Value objects<br/>Money, ServiceCode"]
        Rules["Invariants, state transitions,<br/>domain exceptions"]
    end

    subgraph Infrastructure["infrastructure"]
        Config["Spring bean configuration"]
        Tx["Transactional decorators"]
        Jpa["JPA entities and repository adapters"]
        Rest["Outbound REST adapters"]
        Security["OAuth2 resource-server configuration"]
    end

    Controller --> InPorts
    Controller --> Commands
    Controller --> Response
    ActorMapper --> Commands
    InPorts --> UseCases
    UseCases --> Aggregates
    UseCases --> Values
    UseCases --> OutPorts
    UseCases --> Results
    Aggregates --> Values
    Aggregates --> Rules
    Tx -.->|"implements"| InPorts
    Tx --> UseCases
    Jpa -.->|"implements"| OutPorts
    Rest -.->|"implements"| OutPorts
    Config --> Tx
    Config --> Jpa
```

Düz oklar compile-time dependency'leri gösterir. Kesik `implements` okları
Dependency Inversion'ı gösterir: interface'lerin sahibi application'dır,
implementation'ları infrastructure sağlar. ArchUnit testleri domain-to-framework,
application-to-infrastructure, presentation-to-domain ve
infrastructure-to-presentation dependency'lerini reddeder.
