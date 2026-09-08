# ADR-008: RabbitMQ Notification Task Delivery

- Status: Accepted for incremental implementation
- Date: 2026-09-08

## Context

Kafka already carries durable business facts between bounded contexts. A
notification is different: it is an operational command asking a worker to
perform retryable work. Reusing Kafka for task competition would blur those
semantics, while publishing directly to RabbitMQ beside a database write would
reintroduce a dual-write failure window.

Notification payloads must not expose member, policy, diagnosis, or contact
data. The first use case is informing a provider that a pre-authorization was
approved or rejected.

## Decision

- Kafka remains the integration-event stream; RabbitMQ carries notification
  delivery commands.
- Authorization persists a notification task in a dedicated local outbox in
  the same transaction as the decision. A scheduled AMQP relay publishes it as
  a persistent message and waits for a correlated publisher confirm.
- Mandatory publishing and publisher returns are enabled. A positive broker
  confirm with a returned message is still treated as failure because no queue
  accepted the routing key.
- The durable direct exchange will route work to one delivery queue. Exhausted
  retries will be rejected without requeue and routed to a dead-letter queue.
- A task contains `taskId`, `causationId`, notification type, provider recipient
  reference, business reference, template key, and contract version. It contains
  no member, policy, diagnosis, token, email, or phone value.
- Notification Worker owns delivery records in its own PostgreSQL database.
  `taskId` is the broker-consumer and downstream-provider idempotency key.
- The worker acknowledges manually only after the transactional application
  use case returns. Unsupported contracts and failed processing are rejected
  without requeue so broker dead-letter routing can quarantine them.
- The sender port must forward that idempotency key to any future email/SMS
  provider. This limits duplicate external side effects if the worker crashes
  after the provider accepts a request but before local commit.

## Consequences

- RabbitMQ and Kafka solve distinct, explainable problems.
- Producer and consumer both remain at-least-once; duplicate work is expected.
- Notification Worker can scale horizontally because RabbitMQ distributes tasks.
- Contact resolution and a real external email/SMS provider remain separate
  security and integration decisions.
- The first five implementation slices establish the framework-independent
  worker core, its private PostgreSQL/Liquibase persistence adapter, and the
  Authorization producer outbox with transaction rollback proof, plus the AMQP
  relay and durable delivery/DLQ topology, plus the version-aware worker listener,
  transactional decorator, and manual acknowledgement. Bounded retry and
  Compose-backed runtime proof follow.
- The relay currently waits for confirms while holding a pessimistic database
  lock. This is deliberately simple and safe for the current workload, but
  asynchronous batching is a future throughput optimization.

## Alternatives

- **Consume Kafka directly in the worker:** rejected because it would use the
  event stream as a competing work queue and would not demonstrate the intended
  task-delivery responsibility of RabbitMQ.
- **Publish directly to RabbitMQ from the request transaction:** rejected due to
  the database/broker dual-write window.
- **Put email or phone in the task:** rejected because broker payloads should not
  become a source of sensitive contact data.
- **Assume exactly-once delivery:** rejected because acknowledgements can be lost
  and external side effects require their own idempotency contract.
