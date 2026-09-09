# ADR-010: APISIX as the external API security boundary

- Status: Accepted
- Date: 2026-09-09

## Context

The browser previously called four Spring services through separately published
host ports. That duplicated public routing knowledge, enlarged the attack
surface, and left traffic limits to each application. The platform needs one
explicit external boundary without moving provider ownership or aggregate
authorization out of the owning services.

## Decision

Run Apache APISIX 3.18 in file-driven standalone mode. Declarative configuration
is versioned in Git, etcd is unnecessary for the local portfolio topology, and
the Admin API is disabled. Only APISIX port `9080` is published for business
APIs; the four Spring API ports are exposed only inside the Compose network.

Every external business route shares these policies:

- Keycloak bearer-token signature, expiry, issuer and dedicated
  `health-insurance-api` audience validation through OIDC discovery and JWKS;
- UUID `X-Correlation-ID` generation or preservation;
- explicit local-development CORS origin, methods and headers;
- 120 requests per source address per 60-second local counter;
- 1 MiB request-body limit and bounded upstream timeouts;
- defensive response headers and no-store caching;
- RFC 9457 JSON adaptation for failures generated before an upstream is called.

APISIX authenticates and governs traffic. Spring Security still validates the
token, endpoint roles and provider ownership. The gateway must not become the
owner of domain authorization, and internal service-to-service calls remain on
the Compose network instead of looping through the external gateway.

The OIDC plugin schema requires a `client_secret`; bearer-only `use_jwks` skips
introspection and never submits that compatibility value to Keycloak. It is
still supplied from an environment variable and no credential is committed.

The API is represented by a bearer-only Keycloak client. Both the browser client
and the local demo client map that client into the access token's `aud` claim.
APISIX requires the claim and matches it against its `client_id`. This prevents a
valid token issued by the same realm for an unrelated client from crossing the
gateway boundary.

The executable gateway demo obtains a second token from Keycloak's unrelated
`admin-cli` client and proves that APISIX returns `403` for its mismatched
audience while accepting the purpose-built API token.

## Consequences

- The portal and demo use one stable API origin.
- Compromising or misconfiguring the gateway does not remove service-level
  authorization checks.
- Standalone local rate counters are per APISIX instance. A multi-replica
  deployment must use a shared Redis policy or accept per-instance quotas.
- Local Keycloak discovery uses HTTP and deliberately produces an APISIX warning.
  Production requires trusted TLS between gateway and identity provider.
- The small custom response adapter is gateway infrastructure, not business
  logic, and has an isolated CI startup test.

## Alternatives considered

- **Expose each Spring service:** simplest, but no single traffic/security edge.
- **Move all authorization to APISIX:** rejected because provider ownership and
  state-dependent permissions belong to application use cases.
- **Traditional APISIX with etcd/Admin API:** useful for dynamic control planes,
  but unnecessary operational state for this local, Git-versioned topology.
- **Spring Cloud Gateway:** viable in a Java-only estate, but APISIX directly
  demonstrates the target gateway and avoids another JVM runtime.
