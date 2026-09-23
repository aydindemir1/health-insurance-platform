# Event-Driven Messaging Mimarisi

## Delivery topolojisi

```mermaid
flowchart LR
    Specialist[Insurance Specialist] --> A[Authorization Use Case]
    A -->|one DB transaction| PA[(pre_authorizations)]
    A -->|one DB transaction| O[(outbox_messages)]
    R[Scheduled Outbox Relay] -->|lock unpublished batch| O
    R -->|key = preAuthorizationId| K{{Kafka topic<br/>health.authorization.pre-authorization.v1}}
    K --> C[Claims/Billing Consumer]
    C -->|one DB transaction| CL[(claims + invoices)]
    C -->|same transaction| I[(processed_messages)]
    C -. after 3 failed attempts .-> D{{.DLT topic}}
```

## Notification command yolu (Milestone 6)

```mermaid
flowchart LR
    Decision[Authorization decision use case]
    PA[(pre_authorizations)]
    Event[(outbox_messages)]
    Task[(notification_task_outbox)]
    AmqpRelay[Confirm-aware AMQP outbox relay]
    Rabbit{{RabbitMQ direct exchange}}
    Queue[[health.notifications.delivery.v1]]
    DLQ[[health.notifications.delivery.v1.dlq]]
    Worker[Notification Worker<br/>bounded retry + manual ack]
    Delivery[(notification_deliveries)]

    Decision -->|one DB transaction| PA
    Decision -->|same transaction| Event
    Decision -->|same transaction| Task
    Task -->|lock oldest unpublished batch| AmqpRelay
    AmqpRelay -->|persistent message + confirm/return| Rabbit
    Rabbit --> Queue
    Queue -->|at-least-once| Worker
    Worker --> Delivery
    Worker -. exhausted/permanent nack .-> DLQ
```

Producer write'ları, AMQP relay, safe JSON mapping, publisher-confirm/return
kararları, durable delivery/DLQ topology, worker retry ve acknowledgement
uygulanmıştır. RabbitMQ ve worker Compose runtime wiring'e sahiptir; bir
RabbitMQ/PostgreSQL Testcontainers testi gerçek routing, duplicate delivery,
persistence, acknowledgement ve dead-lettering davranışını çalıştırır.
Adapter'lar broker-free testler ve standalone development için feature-gated
kalmaya devam eder.

### Persist edilen notification task intent v1

```json
{
  "taskId": "UUID",
  "causationId": "decision event UUID",
  "taskVersion": 1,
  "notificationType": "PRE_AUTHORIZATION_APPROVED",
  "businessReferenceId": "pre-authorization UUID",
  "recipientKind": "PROVIDER",
  "recipientReferenceId": "provider UUID",
  "templateKey": "pre-authorization-approved-v1",
  "occurredAt": "2026-09-08T12:00:00Z"
}
```

Bu field'lar artık hem producer-outbox intent hem de versioned AMQP JSON contract'ıdır.
`taskId` AMQP message ID, `causationId` correlation ID olur ve `taskVersion`
header olarak da taşınır. Contract bilinçli olarak member, policy, diagnosis,
amount, decision reason, contact address, rendered content ve security token data'yı
dışlar.

Publisher confirm, RabbitMQ'nun publish sorumluluğunu kabul ettiğini kanıtlar;
consumer'ın mesajı işlediğini değil. Direct exchange mandatory bir unroutable
message'ı kabul edip sonra return edebileceğinden relay, `published_at` set etmeden
önce hem positive confirm hem de returned message bulunmamasını gerektirir.

### RabbitMQ failure policy

| Failure | Deneme | Broker sonucu | Gerekçe |
| --- | ---: | --- | --- |
| Explicit transient delivery exception | Toplam 3, bounded exponential backoff | Başarılı committed attempt sonrası ack; aksi halde requeue olmadan nack | Geçici provider/dependency hızlıca iyileşebilir |
| Unsupported contract version | 1 | Requeue olmadan nack → DLQ | Code deployment veya contract handling gerekir |
| Malformed JSON veya invalid invariant | 1 | Requeue olmadan nack → DLQ | Aynı data'yı tekrar etmek problemi düzeltemez |
| Aynı intent ile delivered duplicate `taskId` | 1 | Idempotent no-op ardından ack | At-least-once redelivery beklenir |
| Aynı `taskId`, farklı intent | 1 | Requeue olmadan nack → DLQ | Producer/contract corruption göstergesidir |

Default retry ayarları `NOTIFICATION_RETRY_MAX_ATTEMPTS`,
`NOTIFICATION_RETRY_INITIAL_INTERVAL`, `NOTIFICATION_RETRY_MULTIPLIER` ve
`NOTIFICATION_RETRY_MAX_INTERVAL` üzerinden configure edilir. Varsayılanlar
toplam üç attempt, 250 ms, 2.0 ve 2 saniyedir.

## Event contract v1

Topic iki decision type'ını da içerir. Payload bilinçli olarak JPA entity ve HTTP
response DTO'larından bağımsızdır.

```json
{
  "eventId": "UUID",
  "eventType": "PreAuthorizationApproved",
  "eventVersion": 1,
  "preAuthorizationId": "UUID",
  "memberId": "UUID",
  "providerId": "UUID",
  "policyNumber": "POL-...",
  "serviceCode": "IMG-MRI",
  "requestedAmount": 1250.00,
  "currency": "TRY",
  "decision": "APPROVED",
  "reason": "Coverage verified",
  "sourceRevision": 2,
  "occurredAt": "2026-09-08T12:00:00Z"
}
```

`eventId` consumer idempotency key'dir. `eventType` business event'i explicit
hale getirir; `eventVersion` consumer'ların unsupported contract'ı sessizce yanlış
yorumlamak yerine reject etmesini sağlar. `preAuthorizationId` Kafka message key ve
Claims/Billing business uniqueness key'dir. `decision` Claims/Billing'in work
başlatıp başlatmayacağını belirler; hem `PreAuthorizationApproved` hem
`PreAuthorizationRejected` yayınlanır. v1 içindeki additive evolution mevcut
meaning'leri korumalıdır; breaking change yeni topic/contract version gerektirir.
`sourceRevision` owner aggregate'in monotonic state revision'ıdır; bu field olmayan
legacy v1 message'lar baseline revision 1'e map edilir.

## Failure semantics

```mermaid
sequenceDiagram
    participant DB as Authorization DB
    participant Relay as Outbox Relay
    participant Kafka
    participant Consumer as Claims Consumer
    participant CDB as Claims DB
    participant DLT

    Relay->>DB: Lock oldest unpublished batch
    Relay->>Kafka: Publish and await acknowledgement
    alt Broker unavailable
        Relay->>DB: Increment attempts, retain unpublished row
        Note over Relay,DB: Next scheduled poll retries
    else Broker acknowledged
        Relay->>DB: Set published_at
    end
    Kafka->>Consumer: Deliver event
    Consumer->>CDB: Create claim/invoice and processed marker
    alt Offset commit fails or message is redelivered
        Kafka->>Consumer: Deliver same eventId again
        Consumer->>CDB: processed_messages exists, no-op
    else Processing repeatedly fails
        Consumer->>Consumer: Fixed backoff, 3 total attempts
        Consumer->>DLT: Publish original record plus failure headers
    end
```

Relay, Kafka acknowledgement sonrasında fakat `published_at` commit edilmeden crash
olursa duplicate publish edebilir. Inbox table bu yüzden gereklidir. Outbox row'ları
delivery evidence olarak tutulur; retention/archival operational follow-up olarak
kalır. Milestone 10 automatic recovery iddiasında bulunmadan bounded DLT inspection
ve reviewed copy-replay ekler.

## Search projection yolu (Milestone 7)

```mermaid
flowchart LR
    AuthTopic{{Authorization decision topic}} --> Search[Search Service consumers]
    Claims[Claims and Billing transaction] --> CDB[(claims + invoices)]
    Claims --> SO[(claim_search_outbox)]
    Relay[Scheduled search relay] --> SO
    Relay --> SearchTopic{{health.claims.search-projection.v1}}
    SearchTopic --> Search
    Search -->|deterministic ID + monotonic revision| ES[(healthcare-operations alias)]
```

Claim ve invoice transition'ları aggregate'i update eder ve aynı local transaction
içinde complete operational projection append eder. Relay yalnızca commit sonrası
publish eder ve row'ları yalnızca Kafka acknowledgement sonrası published işaretler.
Search at-least-once consume eder; `CLAIM-{claimId}` ve
`PRE_AUTHORIZATION-{id}` document ID'leri redelivery'yi replacement'a çevirir.
Kafka partition key'leri tek aggregate için claim transition sırasını korur.
Conditional Elasticsearch upsert eski `sourceRevision` değerini de reddeder;
böylece delayed event online rebuild sırasında newer snapshot'ı geriletemez.
Elasticsearch disposable ve rebuild edilebilir read state olarak kalır.

## Kontrollü dead-letter recovery (Milestone 10)

```mermaid
flowchart LR
    Signal[Outbox age, consumer lag, queue depth] --> Inspect[Inspect bounded metadata and SHA-256]
    Inspect --> Classify{Reviewed classification}
    Classify -->|Permanent or unknown| Quarantine[Retain original in DLT or DLQ]
    Classify -->|Transient and dependency healthy| Confirm[Explicit confirmation + attempt 1..3]
    Confirm --> Copy[Copy to allowlisted original route]
    Copy --> Guard[Inbox, uniqueness, taskId or revision guard]
    Guard --> Verify[Verify state and lag before another record]
```

Kafka tooling yalnızca Authorization decision ve Claims search DLT topic'lerine izin
verir. RabbitMQ tooling yalnızca notification DLQ ve delivery route'a izin verir.
Inspection payload'ı memory içinde hash'ler ve sadece safe metadata yazdırır. Replay
on record ile sınırlandırılır, `Transient` classification ve `-ConfirmReplay`
gerektirir, recovery ID/source/attempt metadata ekler ve original dead-letter record'u
korur. Bilinçli olarak discard command yoktur.

Bu operator-assisted local control'dür; production recovery service değildir.
Kafka access tools-only Compose profile tarafından sağlanır, RabbitMQ credential'ları
runtime input'tur ve henüz central audit/workload identity yoktur. Detaylı command ve
stop condition'lar
[search and messaging recovery runbook](../operations/search-and-messaging-recovery.md)
içindedir.
