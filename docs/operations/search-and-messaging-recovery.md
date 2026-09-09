# Search and messaging recovery runbook

This runbook covers the executable local recovery controls delivered in
Milestone 10. It is intentionally conservative: source databases remain
authoritative, the active Elasticsearch index is never rebuilt in place, and
dead-letter messages are never replayed automatically.

## Safety contract

- Use only synthetic local data. Never print or persist access tokens, message
  payloads, member identifiers, policy numbers, contact details, or diagnoses.
- Inspect before classifying. Replay only a reviewed transient failure after the
  failed dependency is healthy.
- Keep each recovery batch between 1 and 10 broker messages and each search
  ingestion batch between 1 and 200 projections.
- Preserve original DLT/DLQ records as quarantine evidence. The supplied tools
  copy a reviewed message; they do not delete or acknowledge the original.
- Stop if the active alias, document count, topic, queue, or recovery attempt is
  not the expected value. Do not compensate by resetting offsets or deleting an
  index.
- Runtime tokens and RabbitMQ credentials belong only in process variables.

## Preconditions and first inspection

Start the local stack using the ignored `.env` file, then check container health
and the bounded recovery summary:

```powershell
docker compose up -d
docker compose ps
.\scripts\inspect-recovery-status.ps1 | ConvertTo-Json -Depth 6
```

The summary reads only:

- unpublished row count, oldest age, and maximum attempts for Authorization's
  Kafka and RabbitMQ outboxes and Claims/Billing's search outbox;
- Kafka consumer-group lag using the tools-only `kafka-cli` Compose profile;
- RabbitMQ ready, unacknowledged, consumer, and queue-state counters.

It does not display outbox bodies or broker payloads. A non-zero backlog is a
signal to investigate dependency health and logs; it is not proof that a
business transaction failed.

## Rebuild the Elasticsearch projection

Obtain a short-lived local `SYSTEM_ADMIN` access token without storing it in the
repository, then run:

```powershell
$runtimeAccessToken = '<short-lived-system-admin-token>'
.\demo\rebuild-search-index.ps1 `
  -AccessToken $runtimeAccessToken `
  -SchemaVersion 2 `
  -PageSize 100
Remove-Variable runtimeAccessToken
```

The orchestrator performs these steps:

1. Search Service records the current alias target and creates an isolated
   `healthcare-operations-v{schema}-{runId}` candidate.
2. Authorization exports stable `CREATED_AT, id` pages from its own database.
3. Claims/Billing exports stable claim-ID pages and computes the current joined
   claim/invoice/payment projection from its own database.
4. The script detects duplicate deterministic document IDs in memory and sends
   bounded batches through APISIX.
5. Search validates every record as a domain `SearchRecord` and uses conditional
   upsert based on the owner-defined monotonic `sourceRevision`.
6. Activation refreshes the candidate, compares its count with the distinct
   exported ID count, verifies the predecessor has not changed, and atomically
   swaps the stable alias.

An owner/API/mapping/count failure leaves the old alias untouched and prints
only the run/candidate identifiers needed for inspection. A partial candidate
must not be activated by changing the expected count.

### Verify activation

```powershell
curl.exe -sS "http://localhost:9200/_cat/aliases/healthcare-operations?format=json&h=alias,index,is_write_index"
curl.exe -sS "http://localhost:9200/healthcare-operations/_count"
curl.exe -sS "http://localhost:9200/_cat/indices/healthcare-operations-v*?format=json&h=index,docs.count,status"
```

Expected evidence is exactly one writable alias target, the exported distinct
count on that target, and a retained predecessor. Normal reads and event writes
continue through `healthcare-operations`; physical index names are operational
details only.

### Explicit rollback

Rollback is valid only while the Search Service process still holds the active
run state and the alias still points to that run's candidate:

```powershell
$runtimeAccessToken = '<short-lived-system-admin-token>'
.\demo\rollback-search-index.ps1 `
  -AccessToken $runtimeAccessToken `
  -RunId '<run-id-returned-by-rebuild>'
Remove-Variable runtimeAccessToken
```

The operation is another compare-and-swap alias update. It retains both the
candidate and predecessor; cleanup is a separate, intentionally unimplemented
retention decision. The local run registry is in memory, so a Search Service
restart requires manual alias inspection and a newly reviewed recovery plan.

## Kafka DLT workflow

Only the two allowlisted DLT topics can be inspected:

```powershell
.\scripts\recover-kafka-dlt.ps1 `
  -Action Inspect `
  -DltTopic health.authorization.pre-authorization.v1.DLT `
  -MaxMessages 5
```

The output contains the message key, SHA-256 digest, and byte length—not the
payload. Classify each digest using the consumer error and deployment evidence:

| Classification | Examples | Action |
| --- | --- | --- |
| Transient | Broker/dependency outage after a valid contract arrived | Prove dependency recovery, then bounded replay |
| Permanent | Unsupported schema version, malformed JSON, invariant violation | Quarantine until compatible code or reviewed transform exists |
| Unknown | Insufficient evidence | Keep quarantined and investigate |

Record a reviewed transient copy replay explicitly:

```powershell
.\scripts\recover-kafka-dlt.ps1 `
  -Action Replay `
  -DltTopic health.authorization.pre-authorization.v1.DLT `
  -MaxMessages 1 `
  -Classification Transient `
  -RecoveryAttempt 1 `
  -ConfirmReplay
```

The copy targets the allowlisted original topic, preserves the original key and
payload, and adds recovery ID/source/attempt headers. Attempts are capped at
three. Consumer inbox/business uniqueness and monotonic search revisions make a
reviewed duplicate safe; they do not make poison data valid.

## RabbitMQ DLQ workflow

Supply a temporary local RabbitMQ account at runtime:

```powershell
$runtimeRabbitUser = '<temporary-monitor-user>'
$runtimeRabbitPassword = '<temporary-password>'
.\scripts\recover-rabbitmq-dlq.ps1 `
  -Action Inspect `
  -Username $runtimeRabbitUser `
  -Password $runtimeRabbitPassword `
  -MaxMessages 5
Remove-Variable runtimeRabbitUser, runtimeRabbitPassword
```

Inspection uses `ack_requeue_true`, so messages remain in
`health.notifications.delivery.v1.dlq`. Output contains digest, byte length,
redelivery flag, exchange, and routing key. A transient replay additionally
requires `-Classification Transient -RecoveryAttempt 1 -ConfirmReplay`; it
publishes a persistent copy to the allowlisted `health.notifications` exchange
and `pre-authorization.decision` routing key. The original `taskId` remains the
worker idempotency key.

## Failure decision table

| Symptom | Likely cause | Safe next step |
| --- | --- | --- |
| Candidate count mismatch | Refresh/write failure, duplicate owner ID, incomplete page | Leave alias unchanged; inspect owner/API and candidate |
| Alias compare-and-swap conflict | Another operator/rebuild changed the alias | Stop; inspect alias and retained indices |
| Legacy search document has no revision | Pre-M10 projection | Reader maps it to baseline revision 1; rebuild replaces it from owners |
| Outbox pending and attempts rising | Broker unavailable or contract send failure | Restore dependency; observe bounded relay retries |
| Kafka DLT permanent error | Contract/schema/data defect | Quarantine; deploy reviewed compatibility handling |
| Rabbit DLQ transient delivery error | Provider/dependency outage | Prove recovery, copy one message, verify idempotent result |
| Replay returns to DLT/DLQ | Misclassification or dependency still unhealthy | Stop replay immediately; reclassify and investigate |

## Current limitations

- The rebuild coordinator and run/rollback state are local and in-memory; they
  are not resumable across Search Service restarts.
- Broker tools rely on local Docker/RabbitMQ access rather than a production
  workload identity and audited operations API.
- Broker quarantine is retention-by-non-deletion, not a separate quarantine
  store. There is no destructive discard command.
- Index lifecycle cleanup, durable checkpoints, automated alerts, and production
  authorization/audit for recovery commands belong to later milestones.
