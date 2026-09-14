# Notification Worker local verification

This guide isolates the Notification Worker learning and verification path.

## Reading order

1. `NotificationDelivery` — lifecycle and immutable intent comparison.
2. `NotificationDeliveryService` — idempotency lookup and sender orchestration.
3. `TransactionalDeliverNotificationUseCase` — owner transaction boundary.
4. `RabbitNotificationListener` — contract mapping, retry, ack/nack, and MDC.
5. `JpaNotificationDeliveryRepositoryAdapter` — PostgreSQL mapping and task lock.
6. `NotificationRabbitTopology` — exchange, queue, DLX, and DLQ ownership.

## Automated verification

From `services/notification-worker`:

```powershell
.\mvnw.cmd --batch-mode test
```

Verified on 14 September 2026:

- Java `21.0.8`
- tests `24`
- failures/errors/skips `0/0/0`
- PostgreSQL Testcontainer `postgres:17-alpine`
- RabbitMQ Testcontainer `rabbitmq:4.1-management-alpine`
- result `BUILD SUCCESS`

The Mockito dynamic-agent message is a future-JDK warning, not a test failure.

## Requirement-to-evidence map

| Concern | Implementation | Evidence |
| --- | --- | --- |
| delivery lifecycle | domain aggregate | domain tests and PostgreSQL round trip |
| idempotent replay | task ID plus immutable intent | application and real broker tests |
| simultaneous duplicate | transaction advisory lock | two concurrent PostgreSQL transactions, one sender call |
| bounded retry | narrow transient exception classifier | listener tests |
| poison task quarantine | nack without requeue plus DLX | real RabbitMQ DLQ test |
| commit before ack | transactional decorator and manual ack | listener ordering test |
| private schema | Liquibase/JPA adapter | PostgreSQL integration test |
| dependency direction | framework-free domain/application | ArchUnit tests |

## Live checkpoint

The source-run worker on port `8085` reported `UP`, connected to the existing
local RabbitMQ, and validated its Liquibase-owned database on port `5436`. It
then consumed a pending Authorization task and recorded it as `DELIVERED`.

A duplicate v1 publication remained one row. A synthetic v99 publication was
rejected as a permanent contract error, created no database row, and produced:

```text
health.notifications.delivery.v1      ready=0 unacked=0
health.notifications.delivery.v1.dlq  ready=1 unacked=0
```

This deliberately leaves the poison message in the DLQ as visible recovery
evidence. Do not inspect or capture message bodies in portfolio material.

## Screenshot evidence

With the worker and dependencies running:

```powershell
Set-Location apps/operations-portal
npm run screenshots:notification
```

The resulting `25-notification-worker-runtime.png` shows health, dependency
containers, owner-database lifecycle/migration/index metadata, queue counts,
and bindings. The RabbitMQ UI capture replaces the runtime username with a
non-credential label before writing the image.

## .NET comparison

| Java/Spring | .NET analogue |
| --- | --- |
| `@RabbitListener` | MassTransit/RabbitMQ consumer |
| `RetryTemplate` | Polly retry policy |
| transactional use-case decorator | application handler plus EF Core transaction |
| JPA adapter | EF Core repository |
| PostgreSQL advisory transaction lock | `pg_advisory_xact_lock` through Npgsql |
| manual `basicAck/basicNack` | explicit broker acknowledgement |
| delivery row | consumer inbox/idempotency record |
