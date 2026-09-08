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

## Notification command path (Milestone 6 in progress)

```mermaid
flowchart LR
    Decision[Authorization decision use case]
    PA[(pre_authorizations)]
    Event[(outbox_messages)]
    Task[(notification_task_outbox)]
    AmqpRelay[AMQP outbox relay next slice]
    Rabbit{{RabbitMQ next slice}}
    Worker[Notification Worker]
    Delivery[(notification_deliveries)]

    Decision -->|one DB transaction| PA
    Decision -->|same transaction| Event
    Decision -->|same transaction| Task
    Task -. publisher confirm .-> AmqpRelay
    AmqpRelay -.-> Rabbit
    Rabbit -. manual acknowledgement .-> Worker
    Worker --> Delivery
```

The solid producer-side writes are implemented and transaction-tested. Dashed
AMQP connections are deliberately marked as the next slice.

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

These are the implemented producer-outbox fields, not yet a published wire
message. The future relay will map them into the versioned AMQP contract. The
intent deliberately excludes member, policy, diagnosis, amount, decision reason,
contact address, rendered content, and security token data.

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
