# ADR-001: Use Clean Architecture boundaries inside services

- Status: Accepted
- Date: 2026-09-03

## Context

The system will integrate PostgreSQL, Kafka, RabbitMQ, Redis, Keycloak, and
external healthcare systems. Business rules must remain testable without
starting those technologies.

## Decision

The domain model is plain Java. Application use cases are also framework-free
and expose input ports while depending on output ports. Spring MVC is the
presentation boundary. JPA, security configuration, transaction management,
and external integrations are infrastructure concerns. Persistence entities
are separate from domain objects.

The dependency direction is:

```text
Presentation -> Application -> Domain
Infrastructure -> Application / Domain
```

ArchUnit rules verify that domain and application code do not depend on Spring,
JPA, presentation, or infrastructure packages.

## Consequences

- Business rules can be tested quickly without Spring or a database.
- Use cases can be tested without a Spring application context.
- Infrastructure can be replaced without rewriting the domain.
- Explicit mapping adds a small amount of code.
- Transaction annotations live in an infrastructure decorator rather than the
  application service.
- The separation must remain pragmatic; trivial behavior does not need an
  interface merely to increase the number of layers.
