# Policy Service local learning and verification

This runbook isolates Policy Service so its architecture can be learned without
starting Kafka, RabbitMQ, Elasticsearch, APISIX, or unrelated applications. It
uses only synthetic data and keeps credentials and bearer tokens outside the
repository.

## What this exercise proves

The exercise follows one request through the complete Policy boundary:

```text
Keycloak JWT
  -> Spring Security and realm-role mapping
  -> REST request validation
  -> application input port and authorization
  -> Policy aggregate rules
  -> JPA output adapter
  -> Policy PostgreSQL and transactional audit
  -> Redis cache-aside adapter
```

It proves policy issuance and read-only coverage evaluation. It does not prove
benefit reservation, APISIX audience enforcement, Authorization-to-Policy token
relay, Kafka delivery, or a production identity topology.

## 1. Start only the required dependencies

Create an ignored `.env` from `.env.example` and replace every placeholder.
Never copy its values into commands, screenshots, commits, or documentation.

```powershell
docker compose up -d policy-db redis keycloak
docker compose ps policy-db redis keycloak
```

Expected state: PostgreSQL and Redis become `healthy`; Keycloak remains `Up`.
OIDC discovery must return the imported realm:

```powershell
curl.exe -fsS `
  http://localhost:8080/realms/health-insurance/.well-known/openid-configuration
```

## 2. Run Policy Service from source

Load the ignored local settings into the current shell without printing them,
then map the service-specific database variables:

```powershell
Get-Content ..\..\.env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}
$env:DB_URL = 'jdbc:postgresql://localhost:5434/policy'
$env:DB_USERNAME = $env:POLICY_DB_USERNAME
$env:DB_PASSWORD = $env:POLICY_DB_PASSWORD
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
.\mvnw.cmd --batch-mode --no-transfer-progress spring-boot:run
```

Run this from `services/policy-service`. Expected startup evidence includes Java
21, port `8082`, PostgreSQL 17, Hibernate schema validation, and Liquibase with
all Policy changesets applied.

## 3. Check the public and protected boundaries

```powershell
curl.exe -fsS http://localhost:8082/actuator/health
curl.exe -sS -o NUL -w "HTTP %{http_code}`n" `
  -X POST http://localhost:8082/api/v1/policies `
  -H "Content-Type: application/json" -d "{}"
```

Expected results are `UP` and `401`. Health reveals no business data; every
business endpoint requires authentication. The unauthenticated response uses
`application/problem+json` with `status: 401` and title
`Authentication required`. An authenticated user lacking the required role
receives the same bounded format with `status: 403`; neither response exposes
token details or internal exception text.

## 4. Prepare a disposable learning identity

In the local Keycloak realm, create a temporary public client with Direct
Access Grants enabled and a synthetic user with `INSURANCE_SPECIALIST`. This
client is only a local test fixture; the browser portal continues to use
Authorization Code with PKCE, and production must not use password grant.

Obtain a short-lived token into the current process. Do not print or persist it:

```powershell
$env:POLICY_LEARNING_TOKEN = (Invoke-RestMethod -Method POST `
  -Uri 'http://localhost:8080/realms/health-insurance/protocol/openid-connect/token' `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body @{
    client_id = '<temporary-learning-client>'
    grant_type = 'password'
    username = '<temporary-specialist-user>'
    password = '<temporary-password>'
  }).access_token
```

Delete or disable the disposable client and user after the learning session.

## 5. Issue a synthetic policy

Use a unique policy number for each run:

```powershell
$policyNumber = 'POL-LEARN-' + (Get-Date -Format 'yyyyMMddHHmmss')
$memberId = '20000000-0000-0000-0000-000000000001'
$headers = @{
  Authorization = "Bearer $env:POLICY_LEARNING_TOKEN"
  'X-Correlation-ID' = "learn-$policyNumber"
}

$policy = Invoke-RestMethod -Method POST `
  -Uri 'http://localhost:8082/api/v1/policies' `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{
    policyNumber = $policyNumber
    memberId = $memberId
    validFrom = '2026-01-01'
    validUntil = '2026-12-31'
    coverages = @(@{
      serviceCode = 'IMG-MRI'
      limit = 10000.00
      currency = 'TRY'
    })
  } | ConvertTo-Json -Depth 5)
```

Expected state is `ACTIVE`, with zero used amount and 10,000 TRY remaining.

## 6. Evaluate positive and negative decisions

Submit the same request structure to
`POST /api/v1/coverage-evaluations`. Vary only the stated field:

| Scenario | Input change | Expected code |
| --- | --- | --- |
| Eligible MRI | 2,500 TRY, matching member and date | `ELIGIBLE` |
| Limit exceeded | 12,500 TRY | `LIMIT_EXCEEDED` |
| Member mismatch | Different synthetic member UUID | `MEMBER_MISMATCH` |
| Not covered | Service code `SRV-UNKNOWN` | `SERVICE_NOT_COVERED` |
| Currency mismatch | Currency `USD` | `CURRENCY_MISMATCH` |
| Expired | Service date after `validUntil` | `POLICY_EXPIRED` |

A business denial still returns `200 OK`; the domain successfully answered the
question. Invalid JSON/field constraints return RFC 9457 Problem Details.
Evaluation never changes `used_amount` in the current design.

## 7. Inspect the authoritative database and cache

Query only synthetic identifiers. PostgreSQL must show the policy and its
`POLICY_ISSUED` audit row with the same correlation ID. Redis should expose a
hashed evaluation key and a per-policy invalidation set, both with a short TTL.
No policy number, member ID, credential, or token should appear in Redis keys.

```powershell
docker compose exec -T redis redis-cli --scan `
  --pattern 'policy:coverage:v1:*'
```

The exact PostgreSQL inspection command depends on the ignored database user;
use `psql` inside `policy-db` and select only the synthetic policy. Do not take a
full database dump for portfolio evidence.

## 8. Verify Redis fail-open behavior

With a valid runtime-only token, stop only Redis, repeat one known evaluation,
and immediately restart Redis in a `finally` block. The expected response is the
same domain decision with `200 OK`, because PostgreSQL is authoritative. The
application log should contain a cache warning; the request must not be
converted into an eligible result when PostgreSQL itself is unavailable.

```powershell
$evaluationJson = @{
  policyNumber = $policyNumber
  memberId = $memberId
  serviceCode = 'IMG-MRI'
  requestedAmount = 2500
  currency = 'TRY'
  serviceDate = '2026-09-14'
} | ConvertTo-Json

try {
  docker compose stop redis
  Invoke-RestMethod -Method POST `
    -Uri 'http://localhost:8082/api/v1/coverage-evaluations' `
    -Headers $headers -ContentType 'application/json' -Body $evaluationJson
} finally {
  docker compose up -d redis
}
```

Do not run this check against a shared environment. The 2026-09-14 local run
returned `200 ELIGIBLE` while Redis was stopped, and the focused cache adapter
test run passed all four tests.

## 9. Capture safe runtime evidence

```powershell
$env:POLICY_SCREENSHOT_TOKEN = $env:POLICY_LEARNING_TOKEN
$env:POLICY_SCREENSHOT_POLICY_NUMBER = $policyNumber
$env:POLICY_SCREENSHOT_MEMBER_ID = $memberId
Set-Location ..\..\apps\operations-portal
npm run screenshots:policy
```

The resulting `docs/screenshots/18-policy-service-runtime.png` is generated from
live health, OIDC, API, PostgreSQL, and Redis reads. It never renders the token
or passwords.

## 10. Stop the isolated runtime

Stop the Maven process with `Ctrl+C`. Keep volumes if the next learning step
needs the synthetic record, or stop only the containers:

```powershell
docker compose stop policy-db redis keycloak
```

Removing volumes is intentionally excluded because it destroys local evidence
and requires a separate explicit decision.
