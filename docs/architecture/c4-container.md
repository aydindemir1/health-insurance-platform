# C4 Level 2 — Container Diagram

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
    Kafka -->|"Decision and claim projections<br/>deterministic document IDs"| Search
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

## Communication decisions

| Caller | Callee | Current purpose | Failure behavior |
| --- | --- | --- | --- |
| Portal | APISIX | Single external business API, authentication and traffic governance | RFC 9457 gateway error with correlation ID |
| APISIX | Spring APIs | Route authenticated requests over the private Compose network | Bounded connect/send/read timeout; upstream unavailable response |
| Authorization | Policy | Coverage eligibility before accepting a request | Fail closed with 503; nothing persisted |
| Claims/Billing | Authorization | Confirm current approved, provider-owned authorization | Fail closed with 503; no claim or invoice persisted |
| Authorization | Kafka | Publish committed decisions from the outbox | Row remains unpublished and the next poll retries |
| Kafka | Claims/Billing | Start claim/invoice from approval | Idempotent no-op on duplicate; three attempts then DLT |
| Authorization | RabbitMQ | Publish committed provider-notification commands | Positive confirm and no mandatory return mark outbox row published |
| RabbitMQ | Notification Worker | Distribute operational delivery work | Retry classified transient failures; permanent/exhausted tasks go to DLQ |
| Policy | Redis | Cache repeated immutable coverage evaluations | Fail open to authoritative PostgreSQL evaluation |
| Claims/Billing | Kafka/Search | Publish transactionally recorded operational projections | Outbox row remains pending until acknowledged |
| Portal | Search | Provider-authorized full-text/filter query | Empty/error state; no impact on source transactions |
| Portal | Authorization/Policy/Claims audit APIs | `SYSTEM_ADMIN` service-local evidence query | Controller and use-case authorization; bounded filters/page; no cross-database join |

Kubernetes remains a roadmap item and is not shown as a current runtime
component.
