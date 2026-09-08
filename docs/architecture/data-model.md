# Logical Data Model and Ownership

The diagram is logical: relationships crossing a service boundary are UUID or
business-key references, not database foreign keys.

```mermaid
erDiagram
    POLICY ||--|{ POLICY_COVERAGE : contains
    PRE_AUTHORIZATION }o..|| POLICY : "references policy_number"
    CLAIM ||--|| INVOICE : produces
    INVOICE ||--o{ INVOICE_PAYMENT : receives
    CLAIM }o..|| PRE_AUTHORIZATION : "references pre_authorization_id"
    PRE_AUTHORIZATION ||--o{ OUTBOX_MESSAGE : emits
    PRE_AUTHORIZATION ||--o{ NOTIFICATION_TASK_OUTBOX : schedules
    NOTIFICATION_TASK_OUTBOX }o..o| NOTIFICATION_DELIVERY : "becomes task_id"
    PRE_AUTHORIZATION }o..o{ NOTIFICATION_DELIVERY : "business reference"

    POLICY {
        uuid id PK
        varchar policy_number UK
        uuid member_id
        date valid_from
        date valid_until
        varchar status
        bigint version
    }
    POLICY_COVERAGE {
        uuid policy_id FK
        varchar service_code
        decimal limit_amount
        decimal used_amount
        char currency
    }
    PRE_AUTHORIZATION {
        uuid id PK
        uuid member_id
        uuid provider_id
        varchar policy_number
        varchar service_code
        varchar diagnosis_code
        decimal requested_amount
        char currency
        varchar status
        varchar decision_reason
        timestamptz created_at
        timestamptz decided_at
        bigint version
    }
    CLAIM {
        uuid id PK
        uuid pre_authorization_id UK
        uuid member_id
        uuid provider_id
        varchar policy_number
        varchar service_code
        decimal claimed_amount
        decimal approved_amount
        char currency
        varchar status
        bigint version
    }
    INVOICE {
        uuid id PK
        uuid claim_id UK
        uuid provider_id
        varchar invoice_number UK
        decimal total_amount
        decimal payable_amount
        char currency
        varchar status
        bigint version
    }
    INVOICE_PAYMENT {
        uuid invoice_id PK,FK
        varchar payment_reference PK
        decimal amount
        timestamptz paid_at
    }
    OUTBOX_MESSAGE {
        uuid id PK
        uuid aggregate_id
        varchar event_type
        integer event_version
        text payload
        timestamptz occurred_at
        timestamptz published_at
        integer publish_attempts
    }
    NOTIFICATION_TASK_OUTBOX {
        uuid task_id PK
        uuid causation_id UK
        integer task_version
        varchar notification_type
        uuid business_reference_id
        varchar recipient_kind
        uuid recipient_reference_id
        varchar template_key
        timestamptz occurred_at
        timestamptz published_at
        integer publish_attempts
    }
    PROCESSED_MESSAGE {
        uuid message_id PK
        varchar consumer_name
        timestamptz processed_at
    }
    NOTIFICATION_DELIVERY {
        uuid task_id PK
        uuid causation_id
        uuid business_reference_id
        varchar notification_type
        varchar recipient_kind
        uuid recipient_reference_id
        varchar template_key
        varchar status
        timestamptz received_at
        timestamptz delivered_at
    }
```

| Database owner | Tables | Other services' access |
| --- | --- | --- |
| Policy Service | `policies`, `policy_coverages` | REST coverage evaluation only |
| Authorization Service | `pre_authorizations`, `outbox_messages`, `notification_task_outbox` | REST snapshots; Kafka events; confirm-aware RabbitMQ task publishing (runtime pending) |
| Claims/Billing Service | `claims`, `invoices`, `invoice_payments`, `processed_messages` | No direct database access |
| Notification Worker | `notification_deliveries` | No direct database access |

Cross-context references intentionally have no foreign keys. Each owner can
change its schema independently; consistency across services is currently
checked through explicit synchronous contracts or versioned broker messages.
