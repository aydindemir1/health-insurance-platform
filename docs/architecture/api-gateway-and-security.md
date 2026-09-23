# API Gateway ve Güvenlik Sınırı

## Çalışma zamanı sınırı

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

Tarayıcı tek bir business API origin bilir. APISIX edge authentication ve trafik
policy'sinin sahibidir; Spring ise role, provider ve business-state authorization'ın
sahibidir. Service-to-service REST çağrıları internal DNS kullanır ve gateway'den geçmez.

## Request işleme

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

## Policy sahipliği

| Concern | APISIX | Spring service |
| --- | --- | --- |
| Route seçimi | Sahibi | Sahibi değil |
| Token signature/issuer/expiry | İlk doğrulama | İkinci doğrulama |
| `health-insurance-api` audience | External boundary'de zorunlu | Henüz tekrar edilmiyor |
| Rate, request size, CORS, timeout | Edge policy'nin sahibi | Defensive default'ları koruyabilir |
| Realm-role permission | Token authenticate edilir | Endpoint/use-case decision'ın sahibi |
| `provider_id` ownership | Karar vermez | Trusted business scope'un sahibi |
| Aggregate state transition | Bilmez | Aggregate/application sahibi |
| Correlation ID | Oluşturur/korur | Doğrular, loglar ve propagate eder |
| Gateway-generated error | RFC 9457 adapter | Dahil değildir |
| Business error | Değiştirmeden geçirir | RFC 9457 sahibi |

## Lokal route tablosu

| External path | Internal upstream |
| --- | --- |
| `/api/v1/pre-authorizations*` | `authorization-service:8081` |
| `/api/v1/policies*`, `/api/v1/coverage-evaluations*` | `policy-service:8082` |
| `/api/v1/claims*`, `/api/v1/invoices*` | `claims-billing-service:8083` |
| `/api/v1/search*` | `search-service:8084` |

Admin API kapalıdır. Configuration değişiklikleri code olarak review edilir ve
`infra/apisix/apisix.yaml` dosyasından hot-load edilir. Production; trusted TLS,
birden fazla gateway replica için shared rate-limit store ve configuration
revision'larının kontrollü deployment'ını eklemelidir.
