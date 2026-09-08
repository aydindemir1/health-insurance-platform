# Notification Worker Architecture

The Notification Worker owns operational delivery attempts. It does not own a
member, policy, pre-authorization, claim, contact address, or clinical record.
Milestone 6 implements the domain/application core, PostgreSQL adapter,
Authorization producer task outbox, confirm-aware AMQP relay, version-aware
listener, bounded retry, manual acknowledgement, and live RabbitMQ/DLQ runtime.

## Component boundaries

```mermaid
flowchart LR
    Rabbit{{"RabbitMQ direct exchange<br/>durable queue + DLQ"}}
    Producer[("Authorization<br/>notification_task_outbox")]
    Listener["AMQP listener adapter<br/>JSON v1 + bounded retry + manual ack"]
    UseCase["DeliverNotificationUseCase<br/>NotificationDeliveryService"]
    Aggregate["NotificationDelivery aggregate<br/>RECEIVED to DELIVERED"]
    RepositoryPort["NotificationDeliveryRepository<br/>output port"]
    SenderPort["NotificationSender<br/>output port"]
    Jpa["JPA repository adapter"]
    Sender["Safe local log sender<br/>external provider future"]
    Database[("Notification PostgreSQL<br/>Liquibase-owned schema")]

    Producer --> Relay["Authorization AMQP relay<br/>publisher confirm + mandatory return"]
    Relay -->|"persistent task"| Rabbit
    Rabbit -->|"competing consumer delivery"| Listener
    Listener --> UseCase
    UseCase --> Aggregate
    UseCase --> RepositoryPort
    UseCase --> SenderPort
    RepositoryPort --> Jpa
    Jpa --> Database
    SenderPort --> Sender
```

## Retry and acknowledgement boundary

```mermaid
sequenceDiagram
    autonumber
    participant Q as RabbitMQ delivery queue
    participant L as Listener / RetryTemplate
    participant T as Transaction decorator
    participant U as Delivery use case
    participant DB as Notification PostgreSQL
    participant DLQ as Dead-letter queue

    Q->>L: Deliver persistent v1 task
    loop At most 3 attempts for transient failures
        L->>T: Execute command
        T->>U: Begin new transaction
        U->>DB: Persist RECEIVED, invoke sender, mark DELIVERED
        alt Transient failure
            T->>DB: Roll back attempt
            T-->>L: TransientNotificationDeliveryException
        else Success or delivered replay
            T->>DB: Commit
            T-->>L: Return
            L->>Q: basicAck
        end
    end
    alt Attempts exhausted or permanent failure
        L->>Q: basicNack(requeue=false)
        Q->>DLQ: Dead-letter via health.notifications.dlx
    end
```

The retry classifier is deliberately narrow. Only
`TransientNotificationDeliveryException` receives bounded exponential backoff.
Unsupported message versions, malformed contracts, and invariant violations are
permanent and are rejected after one attempt. Retry wraps the transactional
proxy, so each attempt gets a separate transaction and no failed state leaks
into the next attempt.

The domain and application packages import neither Spring nor Jakarta. JPA
entities translate persistence rows at the infrastructure boundary, and
ArchUnit continuously enforces that direction.

## Delivery state and replay behavior

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: first taskId persisted
    RECEIVED --> DELIVERED: sender accepts task
    RECEIVED --> RECEIVED: transient failure / broker redelivery
    DELIVERED --> DELIVERED: duplicate task is a no-op
```

`task_id` is the primary key and the downstream sender idempotency key. Reusing
it with a different causation, business reference, type, recipient, or template
is a contract conflict rather than a duplicate. A worker
crash can still cause a task to be received more than once; the design does not
claim exactly-once messaging. A previously delivered row suppresses another
send. A received row remains retryable. The downstream provider must also honor
the idempotency key to close the crash window after external acceptance but
before the local delivered state is committed.

## Persisted data

The delivery row contains only technical routing and lifecycle information:

- task, causation, and business-reference UUIDs;
- notification type and versioned template key;
- recipient kind and opaque provider reference;
- received/delivered timestamps and status.

It deliberately excludes names, member identifiers, policy numbers, diagnosis
codes, email addresses, phone numbers, access tokens, and rendered message
content. Database check constraints keep timestamp and state combinations
consistent even if a future adapter bypasses the aggregate accidentally.

## Verification

The Java 21 suite uses PostgreSQL 17 and RabbitMQ 4.1 Testcontainers. Persistence
tests apply Liquibase, let Hibernate validate the schema, round-trip both states,
and inspect operational indexes. Application tests prove delivered replay is a
no-op and conflicting reuse of a task ID fails. Listener tests prove transient
success after retry, exhaustion after three attempts, immediate permanent
failure, unsupported-version quarantine, and commit-before-ack ordering.

The broker integration test sends real persistent messages through the declared
exchange. Two identical messages create one `DELIVERED` row and leave both
queues empty; an unsupported v99 message creates no delivery row and appears in
`health.notifications.delivery.v1.dlq`. Compose repeats the complete producer to
consumer path with independent PostgreSQL ownership. On 8 September 2026 the
worker suite passed 23/23 tests.

The local sender logs only the task identifier, notification type, recipient
kind, and opaque provider reference. It demonstrates the output port and
idempotency flow but does not claim to send email or SMS.
