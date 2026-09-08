# Screenshot Catalogue

This directory contains milestone checkpoint images captured from the running
operations portal with synthetic demo identifiers. It must never contain access
tokens, credentials, real patient information, or real provider data.

The current portal only implements pre-authorization operations. Policy and
Claims/Billing are demonstrated through the API script until their screens are
implemented in a later milestone.

Screenshots retained for the Milestone 7 documentation checkpoint:

- `01-dashboard.png` — role-aware landing page and operational summary.
- `02-pre-authorization-work-queue.png` — filter, sort, and pagination UI.
- `03-submit-pre-authorization.png` — validated hospital submission form.
- `04-pre-authorization-detail.png` — request detail and status information.
- `05-specialist-decision.png` — specialist approval/rejection controls.
- `06-rabbitmq-notification-queues.png` — live durable delivery queue, DLX/DLK
  arguments, DLQ, consumer processing state, and drained message counts.
- `07-healthcare-search.png` — secured Elasticsearch-backed cross-context
  operations query using synthetic policy and financial records.
- `08-kibana-apm-services.png` — live Kibana APM services inventory populated by
  externally attached Java agents.

## Preview

![Dashboard](01-dashboard.png)

![Pre-authorization work queue](02-pre-authorization-work-queue.png)

![Submission form](03-submit-pre-authorization.png)

![Pre-authorization detail](04-pre-authorization-detail.png)

![Specialist decision](05-specialist-decision.png)

![RabbitMQ notification queues](06-rabbitmq-notification-queues.png)

![Healthcare operations search](07-healthcare-search.png)

![Kibana APM services](08-kibana-apm-services.png)

To recapture them, start the local stack and portal, seed synthetic demo data as
described in the [demo scenario](../demo/demo-scenario.md), sign in using a
runtime-only local user, and replace only images whose view changed:

```powershell
$env:DEMO_USER_PASSWORD = "<temporary-local-demo-password>"
$env:DEMO_POLICY_NUMBER = "<policy-number-reported-by-the-seed-script>"
Set-Location apps/operations-portal
npm run screenshots
```

The capture script drives the real Keycloak login and real API-backed pages in
headless Chrome. It fills but does not submit the example form or pending
decision, so recapturing screenshots does not mutate business data.

Milestone 7 adds the sixth portal view and an APM runtime view. Capture them only
after the synthetic demo has produced Elasticsearch documents and Java-agent
traffic; never expose tokens, credentials, or raw event payloads.

To recapture only the broker evidence, use a temporary local RabbitMQ account
with read-only/monitoring permissions, keep its values in the process
environment, and remove it after capture:

```powershell
$env:RABBITMQ_SCREENSHOT_USERNAME = "<temporary-local-monitoring-user>"
$env:RABBITMQ_SCREENSHOT_PASSWORD = "<temporary-local-password>"
Set-Location apps/operations-portal
npm run screenshots:rabbitmq
```

The script waits for both exact queue names before capturing. It does not read,
publish, acknowledge, or delete messages and never writes credentials to the
image or repository.

Capture the no-credential local Kibana APM inventory after generating traffic:

```powershell
Set-Location apps/operations-portal
npm run screenshots:kibana
```

The local Compose stack disables Elastic security for developer convenience and
binds Elastic ports to loopback. That setting is not a production security model.
