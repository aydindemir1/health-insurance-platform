# API Gateway and Security Boundary

## Runtime boundary

```mermaid
flowchart LR
    Browser[Operations Portal] -->|OIDC Code + PKCE| Keycloak[Keycloak]
    Browser -->|Bearer JWT :9080| APISIX[APISIX external boundary]
    APISIX -->|JWT forwarded| Auth[Authorization :8081 internal]
    APISIX -->|JWT forwarded| Policy[Policy :8082 internal]
    APISIX -->|JWT forwarded| Claims[Claims/Billing :8083 internal]
    APISIX -->|JWT forwarded| Search[Search :8084 internal]
    APISIX -.->|OIDC discovery + JWKS| Keycloak
    Auth -.->|second JWT validation| Keycloak
    Policy -.->|second JWT validation| Keycloak
    Claims -.->|second JWT validation| Keycloak
    Search -.->|second JWT validation| Keycloak
```

The browser knows one business API origin. APISIX owns edge authentication and
traffic policy; Spring owns role, provider and business-state authorization.
Service-to-service REST calls use internal DNS and do not traverse the gateway.

## Request processing

```mermaid
sequenceDiagram
    participant UI as Operations Portal
    participant G as APISIX
    participant KC as Keycloak
    participant S as Spring Service

    UI->>G: API request + bearer token + optional correlation ID
    G->>G: Enforce CORS, payload size and rate limit
    G->>KC: Load/cache discovery and JWKS
    G->>G: Verify RS256 signature, expiry, issuer and API audience
    alt invalid or missing token
        G-->>UI: 401 application/problem+json
    else valid token for another audience
        G-->>UI: 403 application/problem+json
    else valid token
        G->>S: Forward JWT and X-Correlation-ID
        S->>S: Validate JWT again, role and provider ownership
        S-->>G: Business response or RFC 9457 error
        G-->>UI: Response + security/rate/correlation headers
    end
```

## Policy ownership

| Concern | APISIX | Spring service |
| --- | --- | --- |
| Route selection | Owns | Does not own |
| Token signature/issuer/expiry | First validation | Second validation |
| `health-insurance-api` audience | Required at external boundary | Not yet repeated |
| Rate, request size, CORS, timeout | Owns edge policy | May keep defensive defaults |
| Realm-role permission | Token is authenticated | Owns endpoint/use-case decision |
| `provider_id` ownership | Does not decide | Owns trusted business scope |
| Aggregate state transition | Does not know | Aggregate/application owns |
| Correlation ID | Creates/preserves | Validates, logs and propagates |
| Gateway-generated error | RFC 9457 adapter | Not involved |
| Business error | Passes through | RFC 9457 owner |

## Local route table

| External path | Internal upstream |
| --- | --- |
| `/api/v1/pre-authorizations*` | `authorization-service:8081` |
| `/api/v1/policies*`, `/api/v1/coverage-evaluations*` | `policy-service:8082` |
| `/api/v1/claims*`, `/api/v1/invoices*` | `claims-billing-service:8083` |
| `/api/v1/search*` | `search-service:8084` |

The Admin API is disabled. Configuration changes are reviewed as code and hot
loaded from `infra/apisix/apisix.yaml`. Production must add trusted TLS, a shared
rate-limit store for multiple gateway replicas and controlled deployment of
configuration revisions.
