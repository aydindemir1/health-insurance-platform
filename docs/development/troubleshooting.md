# Local Troubleshooting

This guide covers reproducible local-development failures through Milestone 8.
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
allow the relay/consumer to catch up, or follow the bounded
[search rebuild runbook](../operations/search-and-messaging-recovery.md).

A full Search Testcontainers suite can time out while several large containers
start concurrently. Run it alone before classifying the failure as a code defect:

```powershell
Set-Location services/search-service
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

## Search rebuild does not activate

- `Candidate count ... does not match expected count` means one or more owner
  pages/writes did not produce the exact distinct set. The service refreshes the
  candidate before counting. Leave the current alias untouched and inspect the
  failed run; never lower the expected count to force activation.
- `Alias changed concurrently` means another operation moved the stable alias.
  Stop and inspect `_cat/aliases` and `_cat/indices`; do not retry with a guessed
  predecessor.
- A `409` rollback after a Search Service restart is expected because the local
  run registry is in memory. Both physical indices remain intact; perform a new
  reviewed recovery rather than editing the alias blindly.
- A pre-M10 document can omit `sourceRevision`. The reader treats it as baseline
  revision 1 so search remains available, and the next owner rebuild replaces it.

Use only safe metadata during diagnosis:

```powershell
curl.exe -sS "http://localhost:9200/_cat/aliases/healthcare-operations?format=json&h=alias,index,is_write_index"
curl.exe -sS "http://localhost:9200/_cat/indices/healthcare-operations-v*?format=json&h=index,docs.count,status"
```

## Recovery inspection cannot find Kafka commands

The runtime `apache/kafka-native` image is intentionally small and does not
contain console administration binaries. Milestone 10 defines a tools-only
`kafka-cli` Compose profile; use the committed scripts rather than installing
packages in the broker container:

```powershell
.\scripts\inspect-recovery-status.ps1 | ConvertTo-Json -Depth 6
.\scripts\recover-kafka-dlt.ps1 -Action Inspect `
  -DltTopic health.authorization.pre-authorization.v1.DLT -MaxMessages 1
```

An empty DLT/DLQ is a healthy result, not a script failure. If replayed data
immediately returns to dead letter, stop: it was misclassified or its dependency
is still unhealthy. Do not loop the command or reset Kafka offsets.

## Correlation or APM data is missing

Send a safe bounded header, confirm it is echoed, then find the same
`correlationId` field in ECS JSON:

```powershell
curl.exe -i -H "X-Correlation-ID: local-diagnostic-001" http://localhost:9080/api/v1/pre-authorizations
docker compose logs --no-color --tail 100 authorization-service
```

The Java agent is attached through `JAVA_TOOL_OPTIONS`; verify that environment
and APM Server connectivity in the exact container without printing tokens or
secrets. APM unavailability must not stop business processing. Correlation IDs
connect evidence but do not provide distributed transaction semantics.

## APISIX is unhealthy or returns 502/504

```powershell
docker compose ps apisix authorization-service policy-service claims-billing-service search-service keycloak
docker compose logs --no-color --tail 150 apisix
docker compose config --quiet
```

Confirm both read-only files under `infra/apisix` are mounted and the route file
ends with `#END`. A `401` for a missing token proves the route and gateway-native
RFC 9457 adapter loaded; it does not prove the upstream is ready. A `502` means
the selected internal service could not be reached, while `504` indicates the
bounded upstream timeout expired.

The local stack intentionally uses HTTP for Keycloak discovery, so APISIX logs a
security warning. Do not silence this by disabling checks in production; deploy
trusted TLS for the public and backchannel identity-provider endpoints.

The APISIX OpenID Connect plugin currently records a missing bearer token as an
`error` before returning the expected `401`. Correlate the status and request ID
before treating that line as an outage; alerting should not page on isolated
client-side authentication failures.

The rate limiter is local to the APISIX process. A verification run deliberately
exhausts its one-minute window. Restart APISIX or wait for the reset header
before an immediate manual demo. Multiple production replicas require a shared
counter policy.

## Safe reset

`docker compose stop` preserves containers and volumes. `docker compose down`
removes containers and the network but preserves named volumes. `docker compose
down -v` deletes all project volumes and therefore all local demo databases and
broker state; use it only after explicit confirmation that the data is disposable.

## Kubernetes manifest or rollout diagnostics

Validate only the deployment package before touching a cluster:

```powershell
.\scripts\validate-kubernetes.ps1
kubectl kustomize deploy/kubernetes/overlays/local
```

For a live local rollout, first confirm that `kubectl config current-context`
names the disposable cluster, then use
`deploy/kubernetes/scripts/apply-local.ps1`. A missing Secret blocks pod
creation by design; populate the ignored `.env` rather than editing YAML.
Readiness failures keep traffic away from a pod, while liveness failures restart
it. If a process tries to write outside `/tmp`, fix its explicit writable mount
instead of disabling the read-only root filesystem. Network timeouts should be
checked against the default-deny NetworkPolicies and declared external ports
before broadening egress.
