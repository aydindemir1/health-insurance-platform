# Demonstration Scenario — Milestones 0–11

This scenario uses only synthetic identifiers and clinical codes. It proves the
implemented happy path and leaves records in several states for UI and API
demonstration. It does not require or contain real patient information.

Milestone 6 adds a complete notification command path. Each approved/rejected
authorization creates a minimal task in the same transaction as the decision.
A confirm-aware relay publishes it to RabbitMQ; the worker consumes it with
bounded retry, persists an idempotent delivery row, invokes the safe local sender,
commits, and only then acknowledges. Permanent or exhausted work goes to a DLQ.

Milestone 7 adds a short-lived Redis coverage cache, eventually consistent
Elasticsearch projections, a provider-scoped search UI, ECS JSON logs,
correlation propagation, and Elastic APM/Kibana runtime evidence. PostgreSQL is
still authoritative and search never participates in a command transaction.

Milestone 8 places APISIX in front of every browser-facing business API. The
same demo now uses port `9080` for Policy, Authorization, Claims/Billing and
Search, then automatically verifies gateway authentication, routing,
correlation, CORS, payload limiting and rate limiting. Spring Security remains
active behind the gateway.

Milestone 9 adds minimized, append-only evidence to the Authorization, Policy,
and Claims/Billing owner transactions. Each service exposes its own bounded
`SYSTEM_ADMIN` query, while the portal presents a service selector without
creating a shared audit database. The seed script verifies expected evidence
counts after completing the synthetic business flow.

Milestone 10 makes the derived search model recoverable without violating
database ownership. Owner APIs export bounded current snapshots, Search builds
an isolated versioned candidate, count and alias compare-and-swap gates protect
activation, and the predecessor remains available for rollback. Broker recovery
uses a separate inspect/classify/explicit-copy process with safe digests instead
of automatic poison-message replay.

Milestone 11 adds an operational deployment demonstration without inventing
new business data. Render both Kustomize variants with
`./scripts/validate-kubernetes.ps1`, explain the production-oriented base and
the Compose-backed local dependency contracts, then inspect one Deployment,
NetworkPolicy, PDB and HPA. Show that containers run as fixed non-root users,
root filesystems are read-only, probes and resource bounds exist, and no Secret
values are committed. Use `deploy/kubernetes/scripts/apply-local.ps1` only when
a disposable local cluster is active; a rendered manifest is not evidence of a
successful live rollout.

## Preconditions

1. Copy `.env.example` to the ignored `.env` and replace placeholders.
2. Start the current stack with `docker compose up --build`.
3. In Keycloak, create four temporary local users without committing their
   credentials:
   - hospital user: `HOSPITAL_USER`, user attribute
     `providerId=30000000-0000-0000-0000-000000000001`
   - insurance user: `INSURANCE_SPECIALIST`
   - claim user: `CLAIM_APPROVER`
   - governance user: `SYSTEM_ADMIN`
4. Confirm RabbitMQ Management is available at `http://localhost:15672`; its
   credentials come from the ignored `.env`.
5. Confirm APISIX, Elasticsearch, APM Server, and Kibana are reachable at ports
   `9080`, `9200`, `8200`, and `5601`. Search Service remains internal.
6. Obtain short-lived access tokens through the configured OIDC login and keep
   them only in the current shell.

```powershell
$env:DEMO_HOSPITAL_TOKEN = "<short-lived-token>"
$env:DEMO_INSURANCE_TOKEN = "<short-lived-token>"
$env:DEMO_CLAIM_APPROVER_TOKEN = "<short-lived-token>"
$env:DEMO_SYSTEM_ADMIN_TOKEN = "<short-lived-token>"
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

Gateway verification intentionally consumes the local per-IP rate quota until
it proves `429`. When taking portal screenshots immediately after seeding, run
the preparation script with `-SkipGatewayVerification`, capture the pages, and
execute gateway verification in a separate run (or wait for the one-minute
quota window to reset). This keeps two individually valid checks from interfering
with each other.

The direct-grant client exists only in the running local Keycloak database; it
is not part of the imported realm or a production authentication design. The
script does not print or persist passwords/tokens. Browser login continues to
use the committed `health-insurance-web` Authorization Code + PKCE client.
It also registers `providerId` as a managed user-profile attribute that users
can view but only administrators can edit; Keycloak 26 otherwise ignores
undeclared custom attributes by default.

## Data created

The source definitions live in [demo-data.json](../../demo/demo-data.json).
The script creates and verifies:

| Record | Expected final state | Purpose |
| --- | --- | --- |
| Policy with MRI and laboratory coverage | `ACTIVE` | Policy validity, coverage and limit demonstration |
| Laboratory pre-authorization | `PENDING` | Work-queue and decision demonstration |
| MRI pre-authorization | `REJECTED` | Rejection state and reason |
| MRI pre-authorization + event-created claim/invoice | `APPROVED` / `APPROVED` / `SETTLED` | Outbox, Kafka, adjudication and payment flow |
| MRI pre-authorization + event-created claim/invoice | `APPROVED` / `APPROVED` / `DISPUTED` | Eventual creation and outstanding reconciliation |
| Three provider notification deliveries | `DELIVERED` | Authorization outbox, publisher confirm, RabbitMQ consumption, worker idempotency and commit-before-ack |
| Claim and decision search documents | Indexed | Transactional projection outbox, Kafka delivery, deterministic idempotency, and Elasticsearch query |
| Authorization audit evidence | At least two rows for the settled authorization | Submission and approval committed with local business state |
| Policy audit evidence | At least one row for the generated policy | Policy issuance and minimized actor evidence |
| Claim audit evidence | At least three rows for the settled claim | Submission, review, and approval transitions |
| Invoice audit evidence | At least five rows for the settled invoice | Issuance, dispute, reconciliation, payment, and settlement-related transitions |
| Versioned search candidate | `ACTIVE` behind `healthcare-operations` alias | Owner snapshot, revision ordering, count gate, atomic alias swap, and retained rollback index |

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
10. Open Healthcare Search as the insurance specialist, search by the generated
    policy number, and filter Claims. Explain eventual consistency and then show
    that a hospital user cannot override their signed provider scope.
11. In Kibana APM, show the Java services and trace navigation. Compare a portal
    `X-Correlation-ID` response header with the same `correlationId` in ECS JSON
    logs. Emphasize that correlation is diagnostic context, not distributed ACID.
12. Sign in as `system-admin-demo`, open Audit Trail, switch among Authorization,
    Policy, and Claims/Billing, then filter by an aggregate ID from the JSON
    summary. Explain dual controller/use-case authorization, bounded filters,
    deterministic pages, minimized fields, and why the UI does not imply a
    central audit database.
13. Run the search rebuild with the `SYSTEM_ADMIN` token kept in a process
    variable. Show that its distinct count matches the stable alias count, then
    show both the active candidate and retained predecessor in Elasticsearch.
    Explain that event writes continue through the alias and stale revisions are
    no-ops. Use `11-search-rebuild-recovery.png` as repeatable visual evidence.
14. Run `inspect-recovery-status.ps1`, then inspect one Kafka DLT and the RabbitMQ
    DLQ. Point out that the output contains counts/digests rather than payloads.
    Explain why replay needs transient classification, bounded attempts, and an
    explicit confirmation; do not manufacture or replay poison data in the main
    happy-path demo.
15. Finish with the event, architecture, and ER diagrams, highlighting separate
    Kafka-event and RabbitMQ-task semantics, at-least-once delivery, idempotency,
    bounded retry, DLT/DLQ, database ownership, and optimistic locking.

## Expected negative demonstrations

- A request without a token is rejected by APISIX with `401` and
  `application/problem+json`.
- An invalid token is rejected before an upstream is called.
- A correctly signed token issued to Keycloak's unrelated `admin-cli` audience
  is rejected with `403`; a token carrying `health-insurance-api` is accepted.
- A request larger than 1 MiB returns `413`; exceeding the local one-minute
  quota returns `429` with rate-limit headers.
- Direct host access to ports `8081`–`8084` fails because API services are only
  exposed on the Compose network.

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
- Stop Redis and repeat a coverage evaluation: safe logs report cache
  unavailability while PostgreSQL still produces the authoritative result.
- Stop Elasticsearch: existing command workflows continue and committed claim
  projection outbox intent remains recoverable; search temporarily fails.
- A hospital user supplying another `providerId` to Search is still scoped to the
  provider in the signed token.
- A non-administrator cannot invoke any audit API or navigate to Audit Trail.
- Invalid audit actions and page sizes above 100 are rejected rather than passed
  to an arbitrary database query.
- Attempting to update, delete, or truncate any service's `audit_records` table
  is rejected by PostgreSQL.
- A projection export page larger than 200 or a non-`SYSTEM_ADMIN` caller is
  rejected at the application boundary.
- Activating a partial candidate with the wrong expected count returns `409` and
  leaves the stable alias on its predecessor.
- If another rebuild changes the alias first, compare-and-swap rejects the stale
  activation or rollback.
- An event with an older `sourceRevision` cannot overwrite a newer search state.
- DLT/DLQ replay without `Transient` classification and `-ConfirmReplay` stops
  before publishing; originals remain quarantined.

## Reset

Demo data is stored in disposable local Docker volumes. To retain it, stop with
`docker compose stop`. To remove it, explicitly run `docker compose down -v`
after confirming that no local data is needed; this deletes all four database
volumes plus RabbitMQ, Redis, Kafka, and every retained Elasticsearch index.
Candidate/predecessor deletion is deliberately not part of the rebuild or
rollback scripts.
