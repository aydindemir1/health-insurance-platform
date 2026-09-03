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
```

| Database owner | Tables | Other services' access |
| --- | --- | --- |
| Policy Service | `policies`, `policy_coverages` | REST coverage evaluation only |
| Authorization Service | `pre_authorizations` | REST provider-scoped snapshot only |
| Claims/Billing Service | `claims`, `invoices`, `invoice_payments` | No external access implemented yet |

Cross-context references intentionally have no foreign keys. Each owner can
change its schema independently; consistency across services is currently
checked through synchronous contracts and will gain event reconciliation in a
future milestone.
