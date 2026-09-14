# Authorization Service local verification

This guide is a focused learning and verification path for Authorization
Service. It uses synthetic data and does not require the complete platform.

## What this proves

- Java 21/Spring Boot application wiring and PostgreSQL persistence
- Keycloak authentication, role checks, and provider ownership
- synchronous Policy coverage validation before submission
- legal `PENDING -> APPROVED|REJECTED` transitions and optimistic concurrency
- aggregate, audit, Kafka outbox, and RabbitMQ task-outbox transaction boundaries
- asynchronous broker publication without placing a broker call in the HTTP transaction
- RFC 9457 errors for unauthenticated, invalid, forbidden, and conflicting operations

## Architecture learning map

| Java/Spring element | Responsibility | Familiar .NET equivalent |
| --- | --- | --- |
| REST controller | HTTP mapping and validation | ASP.NET Core Controller |
| input port/use case | application capability and orchestration | application service/command handler |
| aggregate | protects lifecycle invariants | rich domain entity/aggregate |
| output port | framework-free dependency contract | application interface |
| JPA adapter | PostgreSQL mapping | EF Core repository adapter |
| transaction decorator | one transaction around application writes | transactional decorator/unit of work |
| Spring Security resource server | validates JWT and authorities | ASP.NET Core JWT bearer authorization |
| Liquibase changeset | versioned database evolution | EF Core migration/DbUp script |

## Fast automated verification

From `services/authorization-service`:

```powershell
.\mvnw.cmd --batch-mode test
```

The current checkpoint contains 78 passing tests. The suite covers domain,
application authorization/ownership, controller contracts, Spring bean wiring,
PostgreSQL/Testcontainers persistence and concurrency, transactional outbox,
relay behavior, and ArchUnit dependency rules. A future JDK Mockito agent
warning is informational; it is not a failed test.

## Focused local runtime

Start only the dependencies needed by this bounded context, using the existing
local images:

```powershell
docker compose up -d authorization-db kafka rabbitmq keycloak
```

Prepare local identities without seeding Claims/Search data:

```powershell
$env:DEMO_KEYCLOAK_ADMIN_USERNAME = "<local-admin>"
$env:DEMO_KEYCLOAK_ADMIN_PASSWORD = "<local-admin-password>"
$env:DEMO_USER_PASSWORD = "<temporary-demo-password>"
.\demo\prepare-and-seed-local-demo.ps1 -SkipDataSeed
```

Start Policy Service on `8082`, then Authorization Service on `8081`. Keep
database credentials and JWTs only in process environment variables. Submit as
`HOSPITAL_USER`, read the created `PENDING` request, and decide it as
`INSURANCE_SPECIALIST`. Repeating the decision must return `409`.

The verified synthetic checkpoint produced:

- unauthenticated collection request: `401 application/problem+json`
- submitted request: `PENDING`, trusted token provider scope
- specialist decision: `APPROVED`
- repeated decision: `409 Conflict`
- persisted aggregate version: `1`
- applied Authorization migrations: `8`
- Kafka event outbox: broker acknowledged, one attempt
- RabbitMQ task outbox: broker acknowledged, one attempt
- audit actions: `PRE_AUTHORIZATION_SUBMITTED`, `PRE_AUTHORIZATION_APPROVED`

## Inspect safe evidence

Replace the UUID with the synthetic request reported by the API. Do not render
payload, member, diagnosis, policy, token, or credential values in evidence.

```powershell
docker compose exec -T authorization-db psql -U authorization_local -d authorization -c "select id,status,version,provider_id,created_at,decided_at from pre_authorizations where id='<synthetic-uuid>';"
docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages consumers durable
$env:AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID = "<synthetic-uuid>"
Set-Location apps/operations-portal
npm run screenshots:authorization
```

The Kafka native runtime image intentionally contains the broker executable,
not the classic `kafka-topics.sh` toolbox. Broker health plus the outbox relay's
acknowledged `published_at` is the local publication evidence. A separate CLI
container would add download/runtime cost without improving this bounded-context
checkpoint.

## Interpretation and limits

PostgreSQL is authoritative. `published_at` proves the relay received a broker
acknowledgement; it does not prove every downstream consumer completed. Kafka
provides durable integration events, while RabbitMQ distributes notification
work—these channels do not solve the same problem. Provider ownership is taken
from the verified token, not client input. The test and screenshot checkpoint
is portfolio evidence, not a production capacity, penetration, or availability
claim.

For design details, see the
[Authorization architecture](../architecture/authorization-service.md),
[business analysis](../business/authorization-service-business-analysis.md),
and [demo scenario](../demo/demo-scenario.md).
