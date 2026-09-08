# ADR 009: Separate Redis caching, Elasticsearch projections, and observability data

- Status: Accepted
- Date: 2026-09-09

## Context

Policy coverage checks are repeated on the synchronous authorization path, while
operations users need cross-context claim and pre-authorization search. These are
different problems: eligibility needs a short-lived acceleration layer over the
Policy source of truth; search needs a denormalized, eventually consistent read
model. Runtime diagnosis also needs one request identifier and machine-readable
logs across HTTP and message boundaries.

## Decision

Policy Service uses Redis with cache-aside semantics. The cache key is a SHA-256
digest of the complete coverage evaluation input and values expire after 30
seconds. Policy creation invalidates the policy's tracked keys. A cache outage is
fail-open: Policy logs safe metadata and evaluates against PostgreSQL. A Policy or
PostgreSQL outage remains fail-closed at Authorization because eligibility cannot
be guessed.

Search Service owns an Elasticsearch index named `healthcare-operations-v1`.
Claims/Billing writes a versioned search projection to its PostgreSQL outbox in
the same transaction as each aggregate transition, then publishes it to Kafka.
Search Service also consumes Authorization decision events. Deterministic document
identifiers make at-least-once redelivery an overwrite rather than a duplicate.
Hospital users are restricted to the signed `provider_id`; insurer roles may query
across providers. Elasticsearch is a projection, never a system of record.

All Java services emit Spring Boot ECS JSON. HTTP filters accept only bounded safe
`X-Correlation-ID` values, create an ID otherwise, add it to MDC and echo it in
the response. Synchronous clients propagate it; Kafka/RabbitMQ consumers derive a
correlation ID from message metadata. The Elastic Java agent is copied into the
runtime image and attached with `-javaagent`; APM Server stores telemetry in the
same-version Elastic Stack and Kibana visualizes it.

## Consequences

- PostgreSQL remains authoritative and cache loss does not corrupt business data.
- Search is fast and cross-context without database sharing, at the cost of
  eventual consistency and projection-rebuild operations.
- The claims search outbox prevents a committed financial transition from losing
  its indexing intent.
- Authorization search currently represents decision events, so pending requests
  remain available from Authorization's strongly consistent work queue.
- Redis, Elasticsearch, Kibana and APM add memory and operational overhead; they
  are optional infrastructure outside the core domain model.
- Correlation IDs improve navigation but are technical identifiers, not proof of
  a distributed transaction.

## Alternatives considered

- Cache annotations were rejected because explicit ports make fallback,
  invalidation and privacy-safe keys testable without Spring in the use case.
- Database joins or shared schemas were rejected because they violate bounded
  context ownership.
- Synchronous dual-write to Elasticsearch was rejected because a search outage
  could break financial transactions or silently lose updates.
- Logback-specific JSON encoders were rejected because Spring Boot provides ECS
  structured logging and automatically includes MDC fields.
- Adding the APM agent as an application dependency was rejected; the supported
  external-agent attachment keeps instrumentation out of domain/application code.
