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
            AuthDb[("PostgreSQL :5433")]
            PolicyDb[("PostgreSQL :5434")]
            ClaimsDb[("PostgreSQL :5435")]
        end
    end

    Browser --> Vite
    Browser --> Keycloak
    Vite -.->|"Browser-issued REST calls"| Auth
    Auth --> Policy
    Claims --> Auth
    Auth --> AuthDb
    Policy --> PolicyDb
    Claims --> ClaimsDb
```

Docker images use a Java 21 JDK build stage and a smaller Java 21 JRE runtime
stage. Services run as the unprivileged `spring` user. Credentials are supplied
through an ignored `.env`; `.env.example` contains placeholders only.
