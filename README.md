# Health Insurance Platform

A portfolio-grade health insurance pre-authorization and claims platform built
for learning and demonstrating modern Java full-stack engineering practices.

## Why this project exists

The project models a real healthcare workflow rather than a generic CRUD demo:
a healthcare provider requests treatment authorization for an insured member,
and an insurance specialist approves or rejects the request according to the
member's policy and coverage.

The first milestone implements the **Authorization bounded context**. The
second milestone adds a React and TypeScript operations portal. Later milestones add policy, claims/billing,
messaging, caching, observability, and a GitOps delivery pipeline.

## Architecture

The authorization service follows Clean Architecture with explicit ports:

```text
HTTP/Presentation -> Input ports -> Application use cases -> Domain model
                                          |
                                          v
                                      Output ports
                                          ^
                                          |
                         Infrastructure adapters (JPA, security, transactions)
```

The domain and application layers do not depend on Spring, JPA, or web APIs.
ArchUnit tests enforce these dependency rules. Framework annotations remain in
the infrastructure and presentation boundaries.

## Implemented in milestone 1

- Java 21 and Spring Boot 4.1
- REST endpoints with Bean Validation
- Domain aggregate with explicit state transitions
- PostgreSQL persistence through Spring Data JPA
- Liquibase database migrations
- Optimistic locking
- OAuth2 resource-server integration for Keycloak
- Role-based authorization
- RFC 9457 Problem Details error responses
- Unit tests for business rules
- GitHub Actions build and test workflow
- Docker Compose development environment
- Health and readiness endpoints

## Run locally

Requirements: Java 21 for backend development and Docker with Compose support.

```bash
docker compose up --build
```

Before the first start, create a local environment file and replace its
placeholder values:

```bash
cp .env.example .env
```

The authorization service is available on `http://localhost:8081`. Keycloak is
available on `http://localhost:8080`. Sign in with the local administrator
values in `.env`, then create test users and assign either the `HOSPITAL_USER`
or `INSURANCE_SPECIALIST` realm role. The ignored `.env` file must never be
committed.

Hospital users must have a `providerId` user attribute containing the UUID of
their healthcare provider. Keycloak maps it to the trusted `provider_id` access
token claim. The API derives provider ownership from this claim and never trusts
a provider identifier supplied in a request body.

Run the operations portal in another terminal:

```bash
cd apps/operations-portal
npm install
npm run dev
```

The portal is available on `http://localhost:5173` and uses the
`health-insurance-web` public Keycloak client with Authorization Code + PKCE.
Copy `apps/operations-portal/.env.example` to `.env` only when overriding local
URLs; no client secret is used or stored in the browser application.

For backend-only development, start PostgreSQL and Keycloak, then run:

```bash
docker compose up -d authorization-db keycloak
cd services/authorization-service
./mvnw spring-boot:run
```

Run unit tests:

```bash
cd services/authorization-service
./mvnw test
```

The complete test suite uses Testcontainers for PostgreSQL integration and
optimistic-concurrency checks, so Docker must be running. Framework-free domain
and application tests, the MVC slice, and the limited transaction-wiring
context test can be run individually without Docker.

## API endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/v1/pre-authorizations` | Submit a request |
| GET | `/api/v1/pre-authorizations/{id}` | Read request details |
| POST | `/api/v1/pre-authorizations/{id}/approval` | Approve a pending request |
| POST | `/api/v1/pre-authorizations/{id}/rejection` | Reject a pending request |
| GET | `/actuator/health` | Liveness/readiness information |

## Roadmap

- [x] Authorization domain and REST API
- [ ] Keycloak realm import and executable API examples
- [ ] Policy service and coverage verification
- [ ] React + TypeScript operations portal
- [ ] Transactional Outbox and Kafka domain events
- [ ] RabbitMQ notification worker
- [ ] Redis cache-aside strategy
- [ ] Elasticsearch, Kibana, and Elastic APM
- [ ] APISIX API Gateway
- [ ] Kubernetes manifests and Argo CD
- [ ] Jenkins, SonarQube, Nexus, and Harbor pipeline

## Engineering documentation

- [System context](docs/architecture/system-context.md)
- [Authorization service components](docs/architecture/authorization-service.md)
- [ADR-001: Clean Architecture service boundaries](docs/adr/001-hexagonal-architecture.md)
- [ADR-002: Provider ownership from authenticated identity](docs/adr/002-provider-ownership-from-authenticated-identity.md)
- [ADR-003: Optimistic concurrency for decisions](docs/adr/003-optimistic-concurrency-for-decisions.md)
- [ADR-004: Feature-Sliced operations portal](docs/adr/004-feature-sliced-operations-portal.md)
