# C4 Level 2 — Container Diagram

```mermaid
flowchart TB
    User["Operations user"]
    Portal["Operations Portal<br/>React 19 + TypeScript + Vite<br/>Pre-authorization work queue"]
    Keycloak["Keycloak 26<br/>OIDC, PKCE, realm roles,<br/>provider_id claim"]

    subgraph Platform["Health Insurance Platform"]
        Auth["Authorization Service :8081<br/>Java 21 / Spring Boot<br/>Owns pre-authorization lifecycle"]
        Policy["Policy Service :8082<br/>Java 21 / Spring Boot<br/>Owns policy and coverage rules"]
        Claims["Claims & Billing Service :8083<br/>Java 21 / Spring Boot<br/>Owns claims, invoices and payments"]
        Kafka{{"Apache Kafka :9092<br/>Durable integration-event stream"}}
        Rabbit{{"RabbitMQ :5672 / :15672<br/>Operational notification work"}}
        Notification["Notification Worker<br/>Java 21 / Spring Boot<br/>Idempotent delivery lifecycle"]

        AuthDb[("Authorization PostgreSQL :5433")]
        PolicyDb[("Policy PostgreSQL :5434")]
        ClaimsDb[("Claims/Billing PostgreSQL :5435")]
        NotificationDb[("Notification PostgreSQL :5436")]
    end

    User -->|"HTTPS"| Portal
    Portal -->|"Authorization Code + PKCE"| Keycloak
    Portal -->|"REST + bearer JWT"| Auth
    Auth -->|"Synchronous coverage query<br/>REST + relayed bearer JWT"| Policy
    Auth -->|"Pre-authorization decisions<br/>at-least-once"| Kafka
    Auth -->|"Persistent notification tasks<br/>confirm-aware outbox relay"| Rabbit
    Kafka -->|"Approved decisions<br/>idempotent consumer"| Claims
    Rabbit -->|"Competing consumer<br/>bounded retry + DLQ"| Notification
    Claims -.->|"Manual claim compatibility<br/>REST + relayed bearer JWT"| Auth

    Auth -->|"JPA/Hibernate + Liquibase"| AuthDb
    Policy -->|"JPA/Hibernate + Liquibase"| PolicyDb
    Claims -->|"JPA/Hibernate + Liquibase"| ClaimsDb
    Notification -->|"JPA/Hibernate + Liquibase"| NotificationDb

    Auth -.->|"JWT signature and issuer validation"| Keycloak
    Policy -.->|"JWT signature and issuer validation"| Keycloak
    Claims -.->|"JWT signature and issuer validation"| Keycloak
```

## Communication decisions

| Caller | Callee | Current purpose | Failure behavior |
| --- | --- | --- | --- |
| Portal | Authorization | Work queue, detail, submission and decision | UI error state; no local copy of server state |
| Authorization | Policy | Coverage eligibility before accepting a request | Fail closed with 503; nothing persisted |
| Claims/Billing | Authorization | Confirm current approved, provider-owned authorization | Fail closed with 503; no claim or invoice persisted |
| Authorization | Kafka | Publish committed decisions from the outbox | Row remains unpublished and the next poll retries |
| Kafka | Claims/Billing | Start claim/invoice from approval | Idempotent no-op on duplicate; three attempts then DLT |
| Authorization | RabbitMQ | Publish committed provider-notification commands | Positive confirm and no mandatory return mark outbox row published |
| RabbitMQ | Notification Worker | Distribute operational delivery work | Retry classified transient failures; permanent/exhausted tasks go to DLQ |

Redis, Elasticsearch, APISIX and Kubernetes are not shown because they remain
roadmap items rather than current runtime components.
