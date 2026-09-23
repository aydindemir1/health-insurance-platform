# Notification Worker lokal doğrulaması

Bu rehber Notification Worker öğrenme ve doğrulama yolunu izole eder.

## Okuma sırası

1. `NotificationDelivery` — lifecycle ve immutable intent karşılaştırması.
2. `NotificationDeliveryService` — idempotency lookup ve sender orchestration.
3. `TransactionalDeliverNotificationUseCase` — owner transaction boundary.
4. `RabbitNotificationListener` — contract mapping, retry, ack/nack ve MDC.
5. `JpaNotificationDeliveryRepositoryAdapter` — PostgreSQL mapping ve task lock.
6. `NotificationRabbitTopology` — exchange, queue, DLX ve DLQ ownership.

## Otomatik doğrulama

`services/notification-worker` dizininden:

```powershell
.\mvnw.cmd --batch-mode test
```

14 Eylül 2026 tarihinde doğrulandı:

- Java `21.0.8`
- test `24`
- failure/error/skip `0/0/0`
- PostgreSQL Testcontainer `postgres:17-alpine`
- RabbitMQ Testcontainer `rabbitmq:4.1-management-alpine`
- sonuç `BUILD SUCCESS`

Mockito dynamic-agent mesajı future-JDK warning'dir; test failure değildir.

## Requirement-to-evidence haritası

| Concern | Implementation | Evidence |
| --- | --- | --- |
| delivery lifecycle | domain aggregate | domain testleri + PostgreSQL round trip |
| idempotent replay | task ID + immutable intent | application + real broker testleri |
| simultaneous duplicate | transaction advisory lock | iki concurrent PostgreSQL transaction, bir sender call |
| bounded retry | dar transient exception classifier | listener testleri |
| poison task quarantine | requeue olmadan nack + DLX | gerçek RabbitMQ DLQ testi |
| commit before ack | transactional decorator + manual ack | listener ordering testi |
| private schema | Liquibase/JPA adapter | PostgreSQL integration testi |
| dependency direction | framework bağımsız domain/application | ArchUnit testleri |

## Live checkpoint

Port `8085` üzerindeki source-run worker `UP` raporladı, mevcut local RabbitMQ'ya bağlandı ve `5436` portundaki Liquibase-owned database'i validate etti. Ardından pending Authorization task consume etti ve `DELIVERED` olarak kaydetti.

Duplicate v1 publication tek row olarak kaldı. Synthetic v99 publication permanent contract error olarak reddedildi, database row oluşturmadı ve şu sonucu üretti:

```text
health.notifications.delivery.v1      ready=0 unacked=0
health.notifications.delivery.v1.dlq  ready=1 unacked=0
```

Bu poison message'ı visible recovery evidence olarak bilinçli şekilde DLQ'da bırakır. Portfolio material içinde message body incelemeyin veya capture etmeyin.

## Screenshot evidence

Worker ve dependency'ler çalışırken:

```powershell
Set-Location apps/operations-portal
npm run screenshots:notification
```

Oluşan `25-notification-worker-runtime.png`; health, dependency container'ları, owner-database lifecycle/migration/index metadata, queue count ve binding'leri gösterir. RabbitMQ UI capture image yazılmadan önce runtime username'i non-credential label ile değiştirir.

## .NET karşılaştırması

| Java/Spring | .NET karşılığı |
| --- | --- |
| `@RabbitListener` | MassTransit/RabbitMQ consumer |
| `RetryTemplate` | Polly retry policy |
| transactional use-case decorator | application handler + EF Core transaction |
| JPA adapter | EF Core repository |
| PostgreSQL advisory transaction lock | Npgsql üzerinden `pg_advisory_xact_lock` |
| manual `basicAck/basicNack` | explicit broker acknowledgement |
| delivery row | consumer inbox/idempotency record |
