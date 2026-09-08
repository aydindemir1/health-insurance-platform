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
            Search["Search Service :8084<br/>Temurin 21 JRE, non-root"]
            Worker["Notification Worker<br/>Temurin 21 JRE, non-root"]
            Kafka{{"Apache Kafka :9092<br/>single-node KRaft"}}
            Rabbit{{"RabbitMQ :5672 / :15672<br/>delivery queue + DLQ"}}
            Redis[("Redis :6379")]
            Elastic[("Elasticsearch :9200")]
            Kibana["Kibana :5601"]
            APM["APM Server :8200"]
            AuthDb[("PostgreSQL :5433")]
            PolicyDb[("PostgreSQL :5434")]
            ClaimsDb[("PostgreSQL :5435")]
            WorkerDb[("PostgreSQL :5436")]
        end
    end

    Browser --> Vite
    Browser --> Keycloak
    Vite -.->|"Browser-issued REST calls"| Auth
    Vite -.->|"Browser-issued search calls"| Search
    Auth --> Policy
    Auth --> Kafka
    Auth --> Rabbit
    Kafka --> Claims
    Kafka --> Search
    Rabbit --> Worker
    Claims -.-> Auth
    Auth --> AuthDb
    Policy --> PolicyDb
    Policy --> Redis
    Search --> Elastic
    Elastic --> Kibana
    APM --> Elastic
    Auth -.-> APM
    Policy -.-> APM
    Claims -.-> APM
    Search -.-> APM
    Worker -.-> APM
    Claims --> ClaimsDb
    Worker --> WorkerDb
```

Docker images use a Java 21 JDK build stage and a smaller Java 21 JRE runtime
stage. Services run as the unprivileged `spring` user. Credentials are supplied
through an ignored `.env`; `.env.example` contains placeholders only.

Compose health-gates the four PostgreSQL databases, Kafka, RabbitMQ, Redis, and
Elasticsearch before
starting their dependants. RabbitMQ Management at `http://localhost:15672`
provides local queue/DLQ inspection. The worker deliberately exposes no HTTP
business API; its observable outputs are broker acknowledgement/dead-lettering,
safe structured task metadata in logs, and its private delivery table. Elastic
ports bind to localhost for development only. Production deployments must enable
TLS, authentication, authorization, retention, and secret management.
