# Search Service local verification

This guide isolates learning and verification of the Elasticsearch projection.

## Reading order

1. `SearchRecord` — framework-independent projection invariants.
2. `SearchApplicationService` — role and provider scope.
3. `SearchProjectionListeners` — Kafka contract mapping and correlation.
4. `ElasticsearchSearchIndex` — mappings, conditional upsert, filters, alias.
5. `SearchRebuildService` — candidate lifecycle and count validation.
6. `SecurityConfiguration` — JWT roles, method security, RFC 9457 handlers.

## Automated verification

From `services/search-service` run with Docker Desktop available:

```powershell
.\mvnw.cmd --batch-mode test
```

An unprivileged sandbox may report `BUILD SUCCESS` while skipping the five
Elasticsearch tests because the Docker named pipe is inaccessible. Accept only
a result with `Skipped: 0` for the complete checkpoint.

Verified components:

- Java `21.0.8`
- Elasticsearch Testcontainer `9.5.3`
- `20/20` tests passed; failures `0`, errors `0`, skipped `0`
- stable alias creation and legacy-index attachment
- filtering and pagination
- newer/older/equal source-revision behavior
- versioned candidate activation and rollback
- provider/application authorization and filter-level RFC 9457
- permanent contract failures bypass retry; transient failures retain the
  configured bounded retry budget before DLT recovery
- Clean Architecture dependency rule

## Live verification result

The source-run service on port `8084` reported `UP`, joined both Kafka consumer
groups, and used the existing local Elasticsearch volume:

```text
stable alias: healthcare-operations
active index: healthcare-operations-v2-c2696b1ac70e4db79eb522807c680a30
documents:   71
```

Real Keycloak tokens produced:

```text
Hospital own provider:      200, five returned rows, one provider UUID
Hospital foreign provider:  403 application/problem+json
Specialist page size 1:     200, one row, 22 matching Claim documents
Unauthenticated search:     401 application/problem+json + correlation ID
Hospital rebuild create:    403
```

The admin rehearsal created
`healthcare-operations-v99-9379d0bd201942a3bf79b39c8c2385b2`, ingested one
synthetic record, activated it after count `1`, and then rolled back. The stable
alias returned to the original 71-document v2 index; both candidate and
predecessor remained retained.

The locally available `apache/kafka-native:4.1.1` image does not include the
Kafka CLI producer/offset scripts, so no artificial poison message was injected
into the running broker. Retry classification is instead verified directly at
the Spring Kafka error-handler boundary; the production listener/DLT topology
remains unchanged.

## Screenshot evidence

After a successful rebuild/rollback rehearsal:

```powershell
Set-Location apps/operations-portal
npm run screenshots:recovery
```

The updated `11-search-rebuild-recovery.png` renders the active alias,
71-document count, 55-document retained v1 predecessor, and the implemented
source-owned/race-safe/rollback-ready properties. It contains no tokens,
credentials, or document payloads.

## .NET comparison

| Java/Spring/Elastic | .NET analogue |
| --- | --- |
| Kafka listener projection | MassTransit/Kafka consumer building a read model |
| Elasticsearch Java client | Elastic .NET client |
| Painless conditional upsert | scripted optimistic projection update |
| stable alias swap | atomic Elasticsearch alias API through .NET client |
| application provider scope | authorization handler plus tenant filter |
| `@PreAuthorize` | `[Authorize(Roles=...)]` |
| `ProblemDetail` | ASP.NET Core `ProblemDetails` |
