# C4 Level 2 — Container Diyagramı

```mermaid
flowchart TB
    User["Operations user"]
    Portal["Operations Portal<br/>React 19 + TypeScript + Vite<br/>Workflow, search and audit view"]
    Keycloak["Keycloak 26<br/>OIDC, PKCE, realm roles,<br/>provider_id claim"]

    subgraph Platform["Health Insurance Platform"]
        Gateway["APISIX :9080<br/>OIDC/JWKS, routing, limits,<br/>correlation and edge errors"]
        Auth["Authorization Service :8081<br/>Java 21 / Spring Boot<br/>Owns pre-authorization lifecycle"]
        Policy["Policy Service :8082<br/>Java 21 / Spring Boot<br/>Owns policy and coverage rules"]
        Claims["Claims & Billing Service :8083<br/>Java 21 / Spring Boot<br/>Owns claims, invoices and payments"]
        Kafka{{"Apache Kafka :9092<br/>Durable integration-event stream"}}
        Rabbit{{"RabbitMQ :5672 / :15672<br/>Operational notification work"}}
        Notification["Notification Worker<br/>Java 21 / Spring Boot<br/>Idempotent delivery lifecycle"]
        Search["Search Service :8084<br/>Java 21 / Spring Boot<br/>Provider-scoped operations projection"]
        Redis[("Redis :6379<br/>Ephemeral coverage cache")]
        Elastic[("Elasticsearch :9200<br/>Search + APM storage")]
        Kibana["Kibana :5601<br/>Search/APM visualization"]
        APM["APM Server :8200<br/>Telemetry ingestion"]

        AuthDb[("Authorization PostgreSQL :5433<br/>Aggregate, outboxes, local audit")]
        PolicyDb[("Policy PostgreSQL :5434<br/>Aggregate, coverage, local audit")]
        ClaimsDb[("Claims/Billing PostgreSQL :5435<br/>Aggregates, inbox/outbox, local audit")]
        NotificationDb[("Notification PostgreSQL :5436")]
    end

    User -->|"HTTPS"| Portal
    Portal -->|"Authorization Code + PKCE"| Keycloak
    Portal -->|"REST + bearer JWT"| Gateway
    Gateway --> Auth
    Gateway --> Policy
    Gateway --> Claims
    Gateway --> Search
    Gateway -.->|"OIDC discovery + JWKS"| Keycloak
    Auth -->|"Synchronous coverage query<br/>REST + relayed bearer JWT"| Policy
    Auth -->|"Pre-authorization decisions<br/>at-least-once"| Kafka
    Auth -->|"Persistent notification tasks<br/>confirm-aware outbox relay"| Rabbit
    Kafka -->|"Approved decisions<br/>idempotent consumer"| Claims
    Kafka -->|"Decision and claim projections<br/>deterministic IDs + source revisions"| Search
    Rabbit -->|"Competing consumer<br/>bounded retry + DLQ"| Notification
    Claims -.->|"Manual claim compatibility<br/>REST + relayed bearer JWT"| Auth

    Auth -->|"JPA/Hibernate + Liquibase"| AuthDb
    Policy -->|"JPA/Hibernate + Liquibase"| PolicyDb
    Policy -->|"Cache-aside; 30-second TTL"| Redis
    Claims -->|"JPA/Hibernate + Liquibase"| ClaimsDb
    Notification -->|"JPA/Hibernate + Liquibase"| NotificationDb

    Auth -.->|"JWT signature and issuer validation"| Keycloak
    Policy -.->|"JWT signature and issuer validation"| Keycloak
    Claims -.->|"JWT signature and issuer validation"| Keycloak
    Search -.->|"JWT signature and issuer validation"| Keycloak
    Search -->|"Java client"| Elastic
    APM --> Elastic
    Elastic --> Kibana
    Auth -.->|"Java agent telemetry"| APM
    Policy -.->|"Java agent telemetry"| APM
    Claims -.->|"Java agent telemetry"| APM
    Search -.->|"Java agent telemetry"| APM
    Notification -.->|"Java agent telemetry"| APM
```

## İletişim kararları

| Caller | Callee | Mevcut amaç | Failure davranışı |
| --- | --- | --- | --- |
| Portal | APISIX | Tek external business API, authentication ve traffic governance | Correlation ID içeren RFC 9457 gateway hatası |
| APISIX | Spring API'leri | Authenticated request'leri private Compose network üzerinden route etmek | Bounded connect/send/read timeout; upstream unavailable response |
| Authorization | Policy | Request kabulünden önce coverage eligibility | 503 ile fail-closed; hiçbir şey persist edilmez |
| Claims/Billing | Authorization | Current approved, provider-owned authorization'ı doğrulamak | 503 ile fail-closed; claim veya invoice persist edilmez |
| Authorization | Kafka | Commit edilmiş decision'ları outbox'tan publish etmek | Row unpublished kalır, sonraki poll retry eder |
| Kafka | Claims/Billing | Approval'dan claim/invoice başlatmak | Duplicate'te idempotent no-op; üç deneme sonrası DLT |
| Authorization | RabbitMQ | Commit edilmiş provider-notification command'larını publish etmek | Positive confirm ve mandatory return olmaması outbox row'u published yapar |
| RabbitMQ | Notification Worker | Operational delivery work dağıtmak | Classified transient failure retry edilir; permanent/exhausted task DLQ'ya gider |
| Policy | Redis | Tekrarlanan immutable coverage evaluation'ları cache'lemek | Authoritative PostgreSQL evaluation'a fail-open |
| Claims/Billing | Kafka/Search | Transactionally recorded operational projection publish etmek | Outbox row acknowledge edilene kadar pending kalır |
| Portal | Search | Provider-authorized full-text/filter query | Empty/error state; source transaction'lara etkisi yoktur |
| Portal | Authorization/Policy/Claims audit API'leri | `SYSTEM_ADMIN` service-local evidence query | Controller ve use-case authorization; bounded filter/page; cross-database join yok |

## Kubernetes deployment görünümü

```mermaid
flowchart LR
    User[Operations user] --> Portal[Operations Portal<br/>2+ replicas]
    Portal --> Gateway[APISIX<br/>2+ replicas]
    Gateway --> Apps[Spring API deployments]
    Apps --> External[Externally operated<br/>data, messaging, IAM and APM]
    Worker[Notification Worker<br/>competing consumers] --> External

    PSS[Restricted Pod Security] -.-> Portal
    PSS -.-> Gateway
    PSS -.-> Apps
    PSS -.-> Worker
    Net[Default-deny NetworkPolicies] -.-> Gateway
    Net -.-> Apps
    Health[Startup + readiness + liveness] -.-> Apps
    Scale[PDB + topology spread + HPA] -.-> Apps
```

Kubernetes stateless rollout ve isolation'ın sahibidir. Stateful dependency'ler
ayrı işletilen contract'lar olarak kalır; bu diyagram application repository'nin
bunlar için production high availability sağladığını ima etmez.
