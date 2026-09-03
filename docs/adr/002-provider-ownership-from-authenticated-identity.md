# ADR-002: Derive provider ownership from authenticated identity

- Status: Accepted
- Date: 2026-09-03

## Context

A hospital user submits and reads pre-authorization requests on behalf of a
healthcare provider. Accepting `providerId` from the HTTP request body would let
an authenticated hospital user impersonate another provider. Role checks alone
do not provide record-level authorization.

The initial product model assigns each hospital user to one provider. A future
version may allow a user to represent multiple providers.

## Decision

Keycloak stores a UUID-valued `providerId` user attribute and maps it to the
`provider_id` access-token claim. The presentation boundary converts verified
JWT claims and authorities into an application `ActorContext`.

The submit use case derives ownership exclusively from this actor context. The
request body does not contain `providerId`. The application layer independently
enforces these policies in addition to endpoint role checks:

- `HOSPITAL_USER` can submit for its own provider.
- `HOSPITAL_USER` can only read its provider's requests.
- `INSURANCE_SPECIALIST` can read all providers' requests and make decisions.
- `SYSTEM_ADMIN` can read requests but cannot make a medical or insurance decision.

## Consequences

- A caller cannot choose another provider by changing JSON input.
- Authorization remains enforced when a use case is called outside an HTTP
  controller.
- Tokens for hospital users must contain a valid `provider_id` UUID.
- Moving a user between providers requires identity data to be updated and a
  new token to be issued.
- Supporting multiple providers later requires replacing the single claim with
  a membership lookup or an allowed-provider collection. That additional model
  is deferred until a real workflow requires it.
