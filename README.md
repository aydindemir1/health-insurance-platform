# Health Insurance Platform

A portfolio-grade health insurance pre-authorization and claims platform built
for learning and demonstrating modern Java full-stack engineering practices.

## Why this project exists

The project models a real healthcare workflow rather than a generic CRUD demo:
a healthcare provider requests treatment authorization for an insured member,
and an insurance specialist approves or rejects the request according to the
member's policy and coverage.

The first milestone implements the **Authorization bounded context**. Later
milestones add policy, claims/billing, a React + TypeScript application,
messaging, caching, observability, and a GitOps delivery pipeline.

## Architecture

The authorization service follows a pragmatic hexagonal architecture:

```text
HTTP API -> Application use cases -> Domain model
                    |
                    v
             Repository port
                    |
                    v
       PostgreSQL persistence adapter
```

The domain model does not depend on Spring or JPA. Framework annotations remain
at the application and adapter boundaries.

## Implemented in milestone 1

- Java 17 and Spring Boot 4.1
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

Requirements: Docker with Compose support.

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
- [ADR-001: Hexagonal service boundaries](docs/adr/001-hexagonal-architecture.md)
