# Local Troubleshooting

This guide covers reproducible local-development failures through Milestone 7.
Never paste passwords, access tokens, message payloads, or real health data into
commands, issues, screenshots, or logs.

## A Compose port is already allocated

Symptom:

```text
Bind for 0.0.0.0:5435 failed: port is already allocated
```

Identify the owner before stopping anything:

```powershell
docker ps -a --filter publish=5435 `
  --format "table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Ports}}"
```

If it is a deliberately retained older demo project, stop that exact project or
its exact containers. Do not delete volumes unless their local data is known to
be disposable. Changing the current Compose port hides the stale runtime and can
make documentation and scripts inconsistent.

## PostgreSQL reports password authentication failure after `.env` changes

PostgreSQL's image reads `POSTGRES_USER` and `POSTGRES_PASSWORD` only when it
initializes an empty data directory. Editing `.env` does not update roles inside
an existing named volume.

For disposable synthetic data, reset only after confirming the loss is safe:

```powershell
docker compose down
docker volume ls --filter label=com.docker.compose.project=health-insurance-platform
# Remove only the exact stale database volumes you have verified.
docker compose up -d
```

For valuable local data, do not delete the volume. Connect with the known
original database administrator, rotate/create the required role, transfer
ownership/grants, and keep the new secret only in `.env`.

## RabbitMQ or Notification Worker does not become ready

Inspect state and bounded logs:

```powershell
docker compose ps -a
docker compose logs --no-color --tail 120 rabbitmq notification-worker
```

Expected evidence includes a healthy RabbitMQ container, a worker connection to
`rabbitmq:5672`, successful Liquibase migration, and a started listener. Verify
that `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `NOTIFICATION_DB_USERNAME`, and
`NOTIFICATION_DB_PASSWORD` exist in the ignored `.env`; do not print them.

## A notification appears in the DLQ

The delivery queue rejects without requeue after a permanent failure or after
three total transient attempts. Inspect headers and safe technical identifiers,
then classify the cause:

- unsupported `taskVersion`, malformed JSON, or intent conflict: fix/deploy the
  compatible consumer or producer contract before replay;
- exhausted transient dependency failure: restore the dependency and verify its
  downstream idempotency contract before replay;
- never edit and replay a message using real contact or health information.

Milestone 6 deliberately has no automatic DLQ replay. Manual replay requires an
operational runbook, authorization, audit evidence, and an idempotency review.

## Testcontainers cannot start RabbitMQ or PostgreSQL

Confirm Docker Desktop is running and the daemon is reachable:

```powershell
docker info
docker ps
```

Run Maven from the service directory. The Notification Worker broker test starts
both PostgreSQL 17 and RabbitMQ 4.1 containers; it is an integration test, not a
mocked broker test.

## Docker builds are slow on a cold cache

Each service currently copies its source before invoking Maven, so a cold or
invalidated Docker cache may download dependencies again. This is a performance
limitation, not a correctness failure. A future build-only improvement can add a
BuildKit Maven cache or dependency-first layer without changing runtime behavior.

## Redis is unavailable

Policy Service deliberately treats Redis as an optimization. Inspect only a
bounded log tail and look for a cache warning followed by a successful database
evaluation:

```powershell
docker compose ps redis policy-service
docker compose logs --no-color --tail 100 policy-service
```

Do not change Authorization to accept unknown coverage. Redis failures fall back
to Policy PostgreSQL; a PostgreSQL/Policy failure still returns `503` and creates
no authorization. Never log or inspect raw cache values using real identifiers.

## Elasticsearch, Search Service, or Kibana is not ready

Elasticsearch and Kibana are memory-intensive on a cold Docker Desktop start.
Inspect exact services and wait for the Elasticsearch health gate:

```powershell
docker compose ps elasticsearch search-service kibana apm-server
docker compose logs --no-color --tail 120 elasticsearch search-service kibana apm-server
curl.exe -sS http://localhost:9200/_cluster/health
```

If core claim commands succeed but search is stale, inspect unpublished
`claim_search_outbox` rows and Search consumer logs. Do not repair this by writing
directly to source-service databases or Elasticsearch. Restore the dependency,
allow the relay/consumer to catch up, or use a future audited reindex operation.

A full Search Testcontainers suite can time out while several large containers
start concurrently. Run it alone before classifying the failure as a code defect:

```powershell
Set-Location services/search-service
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

## Correlation or APM data is missing

Send a safe bounded header, confirm it is echoed, then find the same
`correlationId` field in ECS JSON:

```powershell
curl.exe -i -H "X-Correlation-ID: local-diagnostic-001" http://localhost:8081/actuator/health
docker compose logs --no-color --tail 100 authorization-service
```

The Java agent is attached through `JAVA_TOOL_OPTIONS`; verify that environment
and APM Server connectivity in the exact container without printing tokens or
secrets. APM unavailability must not stop business processing. Correlation IDs
connect evidence but do not provide distributed transaction semantics.

## Safe reset

`docker compose stop` preserves containers and volumes. `docker compose down`
removes containers and the network but preserves named volumes. `docker compose
down -v` deletes all project volumes and therefore all local demo databases and
broker state; use it only after explicit confirmation that the data is disposable.
