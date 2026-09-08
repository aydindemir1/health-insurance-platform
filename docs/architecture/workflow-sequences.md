# Workflow Sequence Diagrams

## Submit and decide a pre-authorization

```mermaid
sequenceDiagram
    autonumber
    actor H as Hospital User
    participant UI as Operations Portal
    participant K as Keycloak
    participant A as Authorization Service
    participant P as Policy Service
    participant ADB as Authorization DB
    participant PDB as Policy DB

    H->>UI: Sign in
    UI->>K: Authorization Code + PKCE
    K-->>UI: Access token with HOSPITAL_USER + provider_id
    H->>UI: Submit member, policy, service, diagnosis, amount
    UI->>A: POST /api/v1/pre-authorizations
    A->>A: Derive provider from signed JWT
    A->>P: POST /api/v1/coverage-evaluations
    P->>PDB: Read policy and coverages
    P-->>A: ELIGIBLE or denial code
    alt Eligible
        A->>ADB: Insert PENDING pre-authorization
        A-->>UI: 201 Created
    else Invalid, expired, uncovered or over limit
        A-->>UI: 422 Problem Details
    else Policy unavailable
        A-->>UI: 503 Problem Details, persist nothing
    end
```

## Claim adjudication, reconciliation and settlement

```mermaid
sequenceDiagram
    autonumber
    actor H as Hospital User
    actor C as Claim Approver
    actor F as Insurance Specialist
    participant O as Authorization Outbox
    participant K as Kafka
    participant CB as Claims/Billing Service
    participant A as Authorization Service
    participant DB as Claims/Billing DB

    H->>A: Pre-authorization is approved
    A->>O: Same transaction, append approval event
    O->>K: Relay versioned event, keyed by authorization ID
    K->>CB: Deliver at least once
    CB->>DB: Same transaction, Claim + Invoice + processed message
    H->>CB: GET /claims/by-pre-authorization/{id}
    CB-->>H: Event-created Claim + Invoice

    C->>CB: POST /claims/{id}/review
    CB->>DB: Claim → UNDER_REVIEW
    C->>CB: POST /claims/{id}/approval (partial amount)
    CB->>DB: Transaction: Claim → APPROVED, Invoice → DISPUTED

    F->>CB: POST /invoices/{id}/dispute-resolution
    CB->>DB: Invoice → MATCHED with agreed payable amount
    F->>CB: POST /invoices/{id}/payments (partial)
    CB->>DB: Add immutable payment, remain MATCHED
    F->>CB: POST /invoices/{id}/payments (remaining)
    CB->>DB: Add payment, Invoice → SETTLED
```

## Decision and notification intent transaction

```mermaid
sequenceDiagram
    autonumber
    actor S as Insurance Specialist
    participant A as Authorization Use Case
    participant DB as Authorization PostgreSQL
    participant K as Kafka Relay
    participant R as RabbitMQ Relay
    participant Q as RabbitMQ
    participant W as Notification Worker
    participant NDB as Notification DB

    S->>A: Approve or reject pending request
    A->>DB: Update pre_authorizations
    A->>DB: Insert versioned Kafka event
    A->>DB: Insert minimal notification task
    alt Any write fails
        DB-->>A: Roll back all three writes
    else Transaction commits
        DB-->>A: Decision committed atomically
        K->>DB: Later read Kafka event outbox
        R->>DB: Lock pending notification task batch
        R-->>Q: Persistent JSON + message/correlation IDs
        alt Broker nack, timeout, or unroutable return
            R->>DB: Increment attempts and retain unpublished row
        else Positive confirm and no return
            R->>DB: Set published_at
            Q->>W: Deliver task at least once
            W->>NDB: New transaction per transient attempt
            alt Delivery commits
                W->>Q: basicAck
            else Permanent or 3 transient attempts fail
                W->>Q: basicNack(requeue=false)
                Q->>Q: Route to durable DLQ
            end
        end
    end
```

`taskId` is retained from producer outbox through RabbitMQ and the delivery
primary key. A broker redelivery after a lost acknowledgement therefore becomes
an idempotent no-op. The sender also receives `taskId` so a future external
provider can apply the same protection across the final side-effect boundary.

## Concurrency and duplicate defense

```mermaid
sequenceDiagram
    participant R1 as Request A
    participant R2 as Request B
    participant App as Application
    participant DB as PostgreSQL
    par Competing commands
        R1->>App: Create claim for authorization X
        R2->>App: Create claim for authorization X
    end
    App->>DB: Both may observe no existing claim
    R1->>DB: INSERT with pre_authorization_id X
    DB-->>R1: Commit
    R2->>DB: INSERT with pre_authorization_id X
    DB-->>R2: Unique constraint violation → 409
```
