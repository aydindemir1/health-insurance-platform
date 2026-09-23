# Lokal Deployment Diyagramı

```mermaid
flowchart TB
    Browser["Desktop browser<br/>localhost:5173"]

    subgraph Host["Developer workstation"]
        Vite["Vite dev server :5173"]
        subgraph Docker["Docker Desktop / Compose network"]
            Keycloak["Keycloak :8080"]
            Gateway["APISIX :9080<br/>external API boundary"]
            Auth["Authorization :8081<br/>Temurin 21 JRE, non-root"]
            Policy["Policy :8082<br/>Temurin 21 JRE, non-root"]
            Claims["Claims/Billing :8083<br/>Temurin 21 JRE, non-root"]
            Search["Search Service :8084<br/>Temurin 21 JRE, non-root"]
            Worker["Notification Worker<br/>Temurin 21 JRE, non-root"]
            Kafka{{"Apache Kafka :9092<br/>single-node KRaft"}}
            KafkaCli["Kafka CLI<br/>tools profile, run-on-demand"]
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
    Vite -.->|"Bearer REST calls"| Gateway
    Gateway --> Auth
    Gateway --> Policy
    Gateway --> Claims
    Gateway --> Search
    Gateway -.->|"OIDC discovery + JWKS"| Keycloak
    Auth --> Policy
    Auth --> Kafka
    Auth --> Rabbit
    Kafka --> Claims
    Kafka --> Search
    KafkaCli -.->|"bounded lag and DLT inspection"| Kafka
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

APISIX host üzerinde publish edilen tek business API portudur. Dört Spring API
portu yalnızca Compose network içinde görünür; Keycloak Authorization Code + PKCE
için browser tarafından erişilebilir kalır. APISIX etcd veya Admin API olmadan
çalışır ve Git-versioned route'ları read-only volume'dan yükler.

Docker image'ları Java 21 JDK build stage ve daha küçük Java 21 JRE runtime stage
kullanır. Servisler unprivileged `spring` user olarak çalışır. Credential'lar
ignore edilen `.env` üzerinden sağlanır; `.env.example` yalnızca placeholder içerir.

Compose, dependent servisleri başlatmadan önce dört PostgreSQL database'i, Kafka,
RabbitMQ, Redis ve Elasticsearch için health gate uygular. `http://localhost:15672`
adresindeki RabbitMQ Management local queue/DLQ inspection sağlar. Worker bilinçli
olarak HTTP business API açmaz; observable output'ları broker acknowledgement/dead-lettering,
log'lardaki güvenli structured task metadata ve private delivery table'dır. Elastic
portları yalnızca development için localhost'a bind edilir. Production deployment
TLS, authentication, authorization, retention ve secret management etkinleştirmelidir.

Runtime `apache/kafka-native` image administrative binary içermez. `kafka-cli`
servisi `tools` profile'a aittir ve yalnızca `docker compose run` ile başlatılır;
başka bir broker veya long-running production container değildir. Recovery script'leri
bunu bounded group-lag ve DLT inspection için kullanır. Elasticsearch read/write
işlemleri `healthcare-operations` alias'ını hedefler; versioned physical index'ler
ve retained predecessor'lar local derived data'dır.
