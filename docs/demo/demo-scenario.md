# Demonstration Scenario — Milestones 0–6

This scenario uses only synthetic identifiers and clinical codes. It proves the
implemented happy path and leaves records in several states for UI and API
demonstration. It does not require or contain real patient information.

Milestone 6 adds a complete notification command path. Each approved/rejected
authorization creates a minimal task in the same transaction as the decision.
A confirm-aware relay publishes it to RabbitMQ; the worker consumes it with
bounded retry, persists an idempotent delivery row, invokes the safe local sender,
commits, and only then acknowledges. Permanent or exhausted work goes to a DLQ.

## Preconditions

1. Copy `.env.example` to the ignored `.env` and replace placeholders.
2. Start the current stack with `docker compose up --build`.
3. In Keycloak, create three temporary local users without committing their
   credentials:
   - hospital user: `HOSPITAL_USER`, user attribute
     `providerId=30000000-0000-0000-0000-000000000001`
   - insurance user: `INSURANCE_SPECIALIST`
   - claim user: `CLAIM_APPROVER`
4. Confirm RabbitMQ Management is available at `http://localhost:15672`; its
   credentials come from the ignored `.env`.
5. Obtain short-lived access tokens through the configured OIDC login and keep
   them only in the current shell.

```powershell
$env:DEMO_HOSPITAL_TOKEN = "<short-lived-token>"
$env:DEMO_INSURANCE_TOKEN = "<short-lived-token>"
$env:DEMO_CLAIM_APPROVER_TOKEN = "<short-lived-token>"
.\demo\seed-demo-data.ps1
```

Add `-VerifyNotificationDelivery` when the Compose Notification Worker and its
database are running. The script then waits for each decision's delivery row:

```powershell
.\demo\seed-demo-data.ps1 -VerifyNotificationDelivery
```

Tokens are parameters/environment values and are never written by the script.
Every run adds a timestamp suffix to policy, invoice, and payment references so
the script can be run repeatedly without defeating production uniqueness rules.

For a completely repeatable local setup, the companion script can create these
three users, assign their roles, create a local-only direct-grant seeder client,
obtain short-lived tokens in memory, and run the same seed operation. Set all
values only in the current shell:

```powershell
$env:DEMO_KEYCLOAK_ADMIN_USERNAME = "<local-admin>"
$env:DEMO_KEYCLOAK_ADMIN_PASSWORD = "<local-admin-password>"
$env:DEMO_USER_PASSWORD = "<temporary-local-demo-password>"
.\demo\prepare-and-seed-local-demo.ps1
```

The repeatable preparation script enables notification verification by default.
Its final JSON must report `DELIVERED` for rejected, settled, and disputed
pre-authorization notifications. Use `-SkipNotificationVerification` only when
intentionally running the business seed without the RabbitMQ/worker runtime.

The direct-grant client exists only in the running local Keycloak database; it
is not part of the imported realm or a production authentication design. The
script does not print or persist passwords/tokens. Browser login continues to
use the committed `health-insurance-web` Authorization Code + PKCE client.
It also registers `providerId` as a managed user-profile attribute that users
can view but only administrators can edit; Keycloak 26 otherwise ignores
undeclared custom attributes by default.

## Data created

The source definitions live in [demo-data.json](../../demo/demo-data.json).
The script creates:

| Record | Expected final state | Purpose |
| --- | --- | --- |
| Policy with MRI and laboratory coverage | `ACTIVE` | Policy validity, coverage and limit demonstration |
| Laboratory pre-authorization | `PENDING` | Work-queue and decision demonstration |
| MRI pre-authorization | `REJECTED` | Rejection state and reason |
| MRI pre-authorization + event-created claim/invoice | `APPROVED` / `APPROVED` / `SETTLED` | Outbox, Kafka, adjudication and payment flow |
| MRI pre-authorization + event-created claim/invoice | `APPROVED` / `APPROVED` / `DISPUTED` | Eventual creation and outstanding reconciliation |
| Three provider notification deliveries | `DELIVERED` | Authorization outbox, publisher confirm, RabbitMQ consumption, worker idempotency and commit-before-ack |

## Live presentation script

1. Open the portal as the hospital user. Explain that Authorization Code + PKCE
   authenticates the browser and `provider_id` scopes the queue.
2. Show the work queue with pending, approved and rejected records. Apply a
   status filter, change sorting, and open a detail page.
3. Submit a covered MRI request. Point out that Authorization calls Policy
   synchronously and persists only after an eligible result.
4. Attempt an uncovered service or amount above the limit and show the RFC 9457
   `422` error. No authorization is created.
5. Sign in as the insurance specialist, open the pending record, and approve or
   reject it. A repeated decision should return `409 Conflict`.
6. Explain that approval and an outbox row commit together. The script polls
   `GET /claims/by-pre-authorization/{id}` until Kafka delivery creates the
   claim/invoice; duplicate delivery is neutralized by `processed_messages`.
7. Open RabbitMQ's Queues and Streams view. Show the durable delivery queue, its
   DLX/DLK arguments, the durable DLQ, one consumer, and zero pending messages
   after successful processing. Then inspect `notification_deliveries` and match
   the three `business_reference_id` values to the JSON summary.
8. Through the Claims/Billing API, inspect the settled scenario. Explain the
   transitions `SUBMITTED → UNDER_REVIEW → APPROVED` and
   `ISSUED → DISPUTED → MATCHED → SETTLED`.
9. Inspect the second invoice left in `DISPUTED`; explain why claim adjudication
   and invoice reconciliation are separate aggregate responsibilities.
10. Finish with the event, architecture, and ER diagrams, highlighting separate
    Kafka-event and RabbitMQ-task semantics, at-least-once delivery, idempotency,
    bounded retry, DLT/DLQ, database ownership, and optimistic locking.

## Expected negative demonstrations

- A hospital token with another provider cannot read the records: `403`.
- A hospital token cannot approve a pre-authorization or claim: `403`.
- A pending/rejected pre-authorization cannot create a claim: `409`.
- A duplicate claim for the same authorization returns `409`.
- Payment before invoice matching returns `409`.
- An overpayment or duplicate payment reference returns an error and does not
  change the invoice.
- If Policy or Authorization is unavailable during validation, the caller gets
  `503` and the local aggregate is not persisted.
- If Kafka is temporarily unavailable, the decision remains committed and its
  outbox row remains unpublished; restarting Kafka allows the relay to resend.
- A poison event is retried three total times and then appears on the `.DLT` topic.
- A transient notification failure gets three total bounded attempts, each in a
  new transaction; exhaustion routes the task to the RabbitMQ DLQ.
- An unsupported notification `taskVersion` is not retried and is dead-lettered.
- Publishing the same valid `taskId` twice results in one `DELIVERED` row and no
  duplicate sender invocation.

## Reset

Demo data is stored in disposable local Docker volumes. To retain it, stop with
`docker compose stop`. To remove it, explicitly run `docker compose down -v`
after confirming that no local data is needed; this deletes all four database
volumes and the RabbitMQ volume.
