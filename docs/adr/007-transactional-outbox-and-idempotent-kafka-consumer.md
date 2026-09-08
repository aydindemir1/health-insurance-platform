# ADR-007: Transactional Outbox and Idempotent Kafka Consumer

- Status: Accepted
- Date: 2026-09-08

## Context

Authorization must publish a durable decision without creating a dual-write
failure between PostgreSQL and Kafka. Claims/Billing must tolerate Kafka's
at-least-once delivery, consumer restarts, offset commit failures, and poison
messages. Directly publishing inside the HTTP transaction could persist the
decision without publishing the event, or publish an event for a rolled-back
decision.

## Decision

Authorization writes `PreAuthorizationApproved` or
`PreAuthorizationRejected` to `outbox_messages` in the same local database
transaction as the aggregate update. A scheduled relay locks an unpublished
batch, publishes JSON to `health.authorization.pre-authorization.v1`, and marks
the row published only after broker acknowledgement.

The topic key is the pre-authorization ID, preserving order for one aggregate.
The contract has a stable event name and integer version. Claims/Billing
consumes approved events and atomically creates its Claim/Invoice plus a
`processed_messages` entry. Re-delivery returns without repeating the business
operation. Rejected decisions are durable integration facts but do not start a
claim.

Consumer failures use blocking fixed-backoff retry: three total attempts with a
one-second delay by default. Exhausted records are published to the matching
`.DLT` topic with the original key, value, partition, and diagnostic headers.

## Consequences

- Database state and intent-to-publish are atomic.
- Delivery is at least once, not exactly once; duplicate publication is expected
  after a crash between broker acknowledgement and `published_at` update.
- Consumer idempotency is a business requirement, not a broker configuration.
- Claims start eventually rather than within the approval HTTP response.
- Outbox locks are held during broker acknowledgement. This is intentionally
  simple for the current scale; a lease/claim-based relay is preferable at high
  throughput.
- DLT records require an operational replay/quarantine process in a later
  observability milestone.

## Alternatives

- **Database/Kafka coordinated transaction:** rejected because it still does not
  provide a single atomic transaction across PostgreSQL and Kafka and increases
  operational coupling.
- **Publish directly after commit:** rejected because a process crash can lose
  the event permanently.
- **Kafka exactly-once semantics only:** rejected because Kafka EOS does not make
  the PostgreSQL aggregate write and broker write one transaction.
- **CDC/Debezium outbox relay:** a strong production alternative, deferred until
  its additional infrastructure and operational ownership are justified.
- **Saga/process manager:** not used. This flow has one event followed by one
  local transaction and no multi-step compensation policy yet.
