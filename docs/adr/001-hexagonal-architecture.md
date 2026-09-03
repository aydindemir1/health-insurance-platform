# ADR-001: Use hexagonal boundaries inside services

- Status: Accepted
- Date: 2026-09-03

## Context

The system will integrate PostgreSQL, Kafka, RabbitMQ, Redis, Keycloak, and
external healthcare systems. Business rules must remain testable without
starting those technologies.

## Decision

The domain model is plain Java. Application services call repository ports.
Spring MVC and JPA are adapters at the system boundary. Persistence entities
are separate from domain objects.

## Consequences

- Business rules can be tested quickly without Spring or a database.
- Infrastructure can be replaced without rewriting the domain.
- Explicit mapping adds a small amount of code.
- The separation must remain pragmatic; trivial behavior does not need an
  interface merely to increase the number of layers.
