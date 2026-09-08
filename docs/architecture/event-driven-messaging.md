# Event-Driven Messaging Architecture

## Delivery topology

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

## Notification command path (Milestone 6)

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

The producer writes, AMQP relay, safe JSON mapping, publisher-confirm/return
decisions, durable delivery/DLQ topology, worker retry and acknowledgement are
implemented. RabbitMQ and the worker have Compose runtime wiring, and a
RabbitMQ/PostgreSQL Testcontainers test exercises real routing, duplicate
delivery, persistence, acknowledgement, and dead-lettering. The adapters remain
feature-gated for broker-free tests and standalone development.

### Persisted notification task intent v1

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

These fields are now both the producer-outbox intent and the versioned AMQP JSON
contract. `taskId` becomes the AMQP message ID, `causationId` the correlation ID,
and `taskVersion` is also carried as a header. The contract deliberately
excludes member, policy, diagnosis, amount, decision reason, contact address,
rendered content, and security token data.

Publisher confirms prove that RabbitMQ accepted responsibility for the publish,
not that a consumer processed it. Because a direct exchange may accept and then
return an unroutable mandatory message, the relay requires both a positive
confirm and the absence of a returned message before setting `published_at`.

### RabbitMQ failure policy

| Failure | Attempts | Broker outcome | Rationale |
| --- | ---: | --- | --- |
| Explicit transient delivery exception | 3 total, exponential bounded backoff | Ack after a successful committed attempt; otherwise nack without requeue | Temporary providers/dependencies can recover quickly |
| Unsupported contract version | 1 | Nack without requeue → DLQ | Code deployment or contract handling is required |
| Malformed JSON or invalid invariant | 1 | Nack without requeue → DLQ | Repeating identical data cannot repair it |
| Delivered duplicate `taskId` with same intent | 1 | Idempotent no-op then ack | At-least-once redelivery is expected |
| Same `taskId`, different intent | 1 | Nack without requeue → DLQ | Indicates producer/contract corruption |

Default retry settings are configurable through `NOTIFICATION_RETRY_MAX_ATTEMPTS`,
`NOTIFICATION_RETRY_INITIAL_INTERVAL`, `NOTIFICATION_RETRY_MULTIPLIER`, and
`NOTIFICATION_RETRY_MAX_INTERVAL`. Defaults are three total attempts, 250 ms,
2.0, and 2 seconds respectively.

## Event contract v1

The topic contains both decision types. The payload is deliberately independent
of JPA entities and HTTP response DTOs.

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
  "occurredAt": "2026-09-08T12:00:00Z"
}
```

`eventId` is the consumer idempotency key. `eventType` makes the business event
explicit and `eventVersion` lets consumers reject unsupported contracts instead
of silently misinterpreting them. `preAuthorizationId` is the Kafka
message key and the Claims/Billing business uniqueness key. `decision` selects
whether Claims/Billing starts work; both `PreAuthorizationApproved` and
`PreAuthorizationRejected` are published. Additive evolution within v1 must
retain existing meanings, while breaking changes require a new topic/contract
version.

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

The relay can publish a duplicate if it crashes after Kafka acknowledgement but
before committing `published_at`. That window is why the inbox table is required.
Outbox rows are retained as delivery evidence; retention/archival and DLT replay
are explicit operational follow-ups.

## Search projection path (Milestone 7)

```mermaid
flowchart LR
    AuthTopic{{Authorization decision topic}} --> Search[Search Service consumers]
    Claims[Claims and Billing transaction] --> CDB[(claims + invoices)]
    Claims --> SO[(claim_search_outbox)]
    Relay[Scheduled search relay] --> SO
    Relay --> SearchTopic{{health.claims.search-projection.v1}}
    SearchTopic --> Search
    Search -->|deterministic document ID| ES[(healthcare-operations-v1)]
```

Claim and invoice transitions update their aggregate and append a complete
operational projection in one local transaction. The relay publishes only after
commit and marks rows published only after Kafka acknowledgement. Search consumes
at least once; `CLAIM-{claimId}` and `PRE_AUTHORIZATION-{id}` document IDs turn
redelivery into replacement. Kafka partition keys preserve claim transition order
for one aggregate. Elasticsearch remains disposable and rebuildable read state.
