# System context

The Health Insurance Platform connects healthcare providers with an insurer.

## Actors

- **Healthcare provider user:** submits treatment pre-authorization requests.
- **Insurance specialist:** reviews, approves, or rejects pending requests.
- **System administrator:** manages identities and access policies in Keycloak.

## Current container boundary

The Authorization Service owns the complete lifecycle of a pre-authorization.
Its PostgreSQL database is private to the service. Other bounded contexts will
consume published domain events instead of reading its database directly.

## Primary workflow

1. A provider submits a request for an insured member.
2. The service validates the command and creates a pending aggregate. The
   provider identity comes from the authenticated user's trusted token claim
   rather than the request body.
3. An insurance specialist makes a decision.
4. The aggregate enforces that only a pending request can be decided.
5. A future milestone publishes the decision through a transactional Outbox.
