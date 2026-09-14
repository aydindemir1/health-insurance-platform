# Search Service business analysis

This document explains the implemented cross-context operations search. Search
is a derived read model, never the source of truth for policy, authorization,
claim, invoice, or payment decisions.

## Purpose and ownership

Search Service consumes versioned Authorization and Claims/Billing projections
from Kafka and owns Elasticsearch documents behind the stable
`healthcare-operations` alias. Source services retain business ownership and
publish recoverable projection intent through their transactional outboxes.

The portal uses Search for fast operational discovery. All commands and final
state checks return to the owning service; a search hit cannot authorize or
settle a business operation.

## Actors and scope

| Actor | Implemented search scope |
| --- | --- |
| `HOSPITAL_USER` | only documents matching signed JWT `provider_id` |
| `INSURANCE_SPECIALIST` | cross-provider operations search |
| `CLAIM_APPROVER` | cross-provider claim/authorization discovery |
| `SYSTEM_ADMIN` | operations search plus controlled rebuild APIs |

A hospital cannot broaden scope with a query-string `providerId`. Supplying a
different provider returns `403`; omitting it automatically applies the JWT
provider. Rebuild endpoints require `SYSTEM_ADMIN` in both method security and
the application use case.

## Projection and query flow

Kafka delivers events at least once. Deterministic document IDs remove duplicate
identity, while owner-defined monotonic `sourceRevision` prevents stale state
regression. A new document is created; only a strictly newer revision replaces
it. Equal and older revisions are no-ops, so a divergent equal-revision duplicate
cannot win by arrival order.

Queries support free text over operational fields plus type, status, provider,
page, and size filters. Page size is bounded to 1–100 and sorting is newest
`occurredAt` first. Elasticsearch is eventually consistent by design.

## Rebuild and rollback

`SYSTEM_ADMIN` creates an isolated physical candidate. Owner APIs export stable,
bounded snapshots; Search validates and indexes them. Activation refreshes the
candidate, compares its distinct count, checks the expected current alias, and
performs one atomic alias swap. The predecessor is retained for explicit
rollback and no index is deleted automatically.

The initial run registry is process-local. A restart leaves Elasticsearch data
safe but cannot resume that API run; the operator inspects the alias and starts
a new rehearsal. This is an explicit portfolio boundary, not a claim of a
durable production workflow engine.

## Failure and security behavior

| Situation | Result |
| --- | --- |
| missing/invalid token | RFC 9457 `401 application/problem+json` |
| hospital requests another provider | RFC 9457 `403` and no data disclosure |
| non-admin rebuild request | filter/method/application denial before mutation |
| invalid type/page/size | RFC 9457 `400` |
| stale/equal projection | Elasticsearch no-op |
| count or alias compare mismatch | `409`; active alias unchanged |
| malformed/wrong-type/version Kafka projection | immediate source-topic DLT |
| transient projection failure | bounded retry then source-topic DLT |
| Elasticsearch unavailable | owner writes remain committed in their outboxes |

## Verified checkpoint

The final Java 21 suite passed `20/20` with failures `0`, errors `0`, and skipped
`0`. Six tests ran against a real Elasticsearch 9.5.3 Testcontainer, including
the equal-revision convergence scenario. Filter-level security is covered by
MVC tests, and Spring Kafka listener/error-handler tests cover contract
validation plus permanent-versus-transient failure classification.

Live evidence proved 71 indexed synthetic documents, hospital provider scoping,
specialist pagination, RFC 9457 authentication failure, administrator-only
rebuild, one-document count validation, atomic activation, retained candidate,
and rollback to the original v2 write index.

See [search architecture](../architecture/search-and-observability.md),
[recovery ADR](../adr/012-versioned-search-rebuild-and-controlled-message-recovery.md),
and [local verification](../development/search-service-local-verification.md).
