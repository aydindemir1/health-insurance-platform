# Local Deployment Diagram

```mermaid
flowchart TB
    Browser["Desktop browser<br/>localhost:5173"]

    subgraph Host["Developer workstation"]
        Vite["Vite dev server :5173"]
        subgraph Docker["Docker Desktop / Compose network"]
            Keycloak["Keycloak :8080"]
            Auth["Authorization :8081<br/>Temurin 21 JRE, non-root"]
            Policy["Policy :8082<br/>Temurin 21 JRE, non-root"]
            Claims["Claims/Billing :8083<br/>Temurin 21 JRE, non-root"]
            Worker["Notification Worker<br/>Temurin 21 JRE, non-root"]
            Kafka{{"Apache Kafka :9092<br/>single-node KRaft"}}
            Rabbit{{"RabbitMQ :5672 / :15672<br/>delivery queue + DLQ"}}
            AuthDb[("PostgreSQL :5433")]
            PolicyDb[("PostgreSQL :5434")]
            ClaimsDb[("PostgreSQL :5435")]
            WorkerDb[("PostgreSQL :5436")]
        end
    end

    Browser --> Vite
    Browser --> Keycloak
    Vite -.->|"Browser-issued REST calls"| Auth
    Auth --> Policy
    Auth --> Kafka
    Auth --> Rabbit
    Kafka --> Claims
    Rabbit --> Worker
    Claims -.-> Auth
    Auth --> AuthDb
    Policy --> PolicyDb
    Claims --> ClaimsDb
    Worker --> WorkerDb
```

Docker images use a Java 21 JDK build stage and a smaller Java 21 JRE runtime
stage. Services run as the unprivileged `spring` user. Credentials are supplied
through an ignored `.env`; `.env.example` contains placeholders only.

Compose health-gates the four PostgreSQL databases, Kafka, and RabbitMQ before
starting their dependants. RabbitMQ Management at `http://localhost:15672`
provides local queue/DLQ inspection. The worker deliberately exposes no HTTP
business API; its observable outputs are broker acknowledgement/dead-lettering,
safe structured task metadata in logs, and its private delivery table.
