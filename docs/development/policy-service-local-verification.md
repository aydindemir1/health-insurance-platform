# Policy Service lokal öğrenme ve doğrulama

Bu runbook Policy Service'i izole eder; böylece Kafka, RabbitMQ, Elasticsearch, APISIX veya ilgisiz application'ları başlatmadan mimarisi öğrenilebilir. Yalnızca sentetik data kullanır ve credential ile bearer token'ları repository dışında tutar.

## Bu çalışma neyi kanıtlar?

Çalışma tek bir request'i Policy boundary'nin tamamından geçirir:

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

Policy issuance ve read-only coverage evaluation'ı kanıtlar. Benefit reservation, APISIX audience enforcement, Authorization-to-Policy token relay, Kafka delivery veya production identity topology kanıtlamaz.

## 1. Yalnızca gerekli dependency'leri başlatın

`.env.example` dosyasından ignore edilen `.env` oluşturun ve tüm placeholder'ları değiştirin. Value'ları command, screenshot, commit veya documentation içine kopyalamayın.

```powershell
docker compose up -d policy-db redis keycloak
docker compose ps policy-db redis keycloak
```

Beklenen durum: PostgreSQL ve Redis `healthy`, Keycloak `Up` olur. OIDC discovery imported realm'i döndürmelidir:

```powershell
curl.exe -fsS `
  http://localhost:8080/realms/health-insurance/.well-known/openid-configuration
```

## 2. Policy Service'i source'tan çalıştırın

Ignore edilen local setting'leri yazdırmadan mevcut shell'e yükleyin ve service-specific database variable'larını map edin:

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

Bunu `services/policy-service` dizininden çalıştırın. Beklenen startup evidence Java 21, port `8082`, PostgreSQL 17, Hibernate schema validation ve tüm Policy changeset'lerinin uygulandığı Liquibase'i içerir.

## 3. Public ve protected boundary'leri kontrol edin

```powershell
curl.exe -fsS http://localhost:8082/actuator/health
curl.exe -sS -o NUL -w "HTTP %{http_code}`n" `
  -X POST http://localhost:8082/api/v1/policies `
  -H "Content-Type: application/json" -d "{}"
```

Beklenen sonuçlar `UP` ve `401`'dir. Health business data göstermez; her business endpoint authentication gerektirir. Unauthenticated response `application/problem+json`, `status: 401`, title `Authentication required` kullanır. Required role'a sahip olmayan authenticated user aynı bounded format'ta `status: 403` alır; hiçbir response token detail veya internal exception text expose etmez.

## 4. Disposable learning identity hazırlayın

Local Keycloak realm içinde Direct Access Grants enabled temporary public client ve `INSURANCE_SPECIALIST` rolüne sahip sentetik user oluşturun. Bu client yalnızca local test fixture'dır; browser portal Authorization Code + PKCE kullanmaya devam eder, production password grant kullanmamalıdır.

Short-lived token'ı mevcut process içine alın. Yazdırmayın veya persist etmeyin:

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

Learning session sonrasında disposable client ve user'ı silin veya disable edin.

## 5. Sentetik policy issue edin

Her run için unique policy number kullanın:

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

Beklenen state `ACTIVE`, used amount sıfır ve remaining amount 10.000 TRY'dir.

## 6. Positive ve negative decision'ları değerlendirin

Aynı request structure'ı `POST /api/v1/coverage-evaluations` endpoint'ine gönderin. Yalnızca belirtilen field'ı değiştirin:

| Scenario | Input değişikliği | Beklenen code |
| --- | --- | --- |
| Eligible MRI | 2.500 TRY, matching member ve date | `ELIGIBLE` |
| Limit exceeded | 12.500 TRY | `LIMIT_EXCEEDED` |
| Member mismatch | Farklı sentetik member UUID | `MEMBER_MISMATCH` |
| Not covered | Service code `SRV-UNKNOWN` | `SERVICE_NOT_COVERED` |
| Currency mismatch | Currency `USD` | `CURRENCY_MISMATCH` |
| Expired | Service date `validUntil` sonrasında | `POLICY_EXPIRED` |

Business denial hâlâ `200 OK` döndürür; domain soruya başarıyla yanıt vermiştir. Invalid JSON/field constraint'leri RFC 9457 Problem Details döndürür. Evaluation mevcut tasarımda `used_amount` değiştirmez.

## 7. Authoritative database ve cache'i inceleyin

Yalnızca sentetik identifier'ları query edin. PostgreSQL policy'yi ve aynı correlation ID'ye sahip `POLICY_ISSUED` audit row'u göstermelidir. Redis hashed evaluation key ve per-policy invalidation set göstermelidir; ikisi de kısa TTL'ye sahip olmalıdır. Redis key'lerinde policy number, member ID, credential veya token görünmemelidir.

```powershell
docker compose exec -T redis redis-cli --scan `
  --pattern 'policy:coverage:v1:*'
```

Exact PostgreSQL inspection command ignore edilen database user'a bağlıdır; `policy-db` içinde `psql` kullanın ve yalnızca synthetic policy'yi select edin. Portfolio evidence için full database dump almayın.

## 8. Redis fail-open davranışını doğrulayın

Valid runtime-only token ile yalnızca Redis'i durdurun, known evaluation'ı tekrarlayın ve `finally` block içinde hemen restart edin. Beklenen response aynı domain decision ve `200 OK`'dir; çünkü PostgreSQL authoritative'dir. Application log cache warning içermelidir; PostgreSQL unavailable ise request eligible result'a çevrilmemelidir.

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

Bu check'i shared environment üzerinde çalıştırmayın. 2026-09-14 local run Redis durmuşken `200 ELIGIBLE` döndürdü ve focused cache adapter test run dört testin tamamından geçti.

## 9. Güvenli runtime evidence capture edin

```powershell
$env:POLICY_SCREENSHOT_TOKEN = $env:POLICY_LEARNING_TOKEN
$env:POLICY_SCREENSHOT_POLICY_NUMBER = $policyNumber
$env:POLICY_SCREENSHOT_MEMBER_ID = $memberId
Set-Location ..\..\apps\operations-portal
npm run screenshots:policy
```

Oluşan `docs/screenshots/18-policy-service-runtime.png`, live health, OIDC, API, PostgreSQL ve Redis read'lerinden üretilir. Token veya password asla render edilmez.

## 10. İzole runtime'ı durdurun

Maven process'i `Ctrl+C` ile durdurun. Sonraki learning step synthetic record'a ihtiyaç duyuyorsa volume'ları koruyun veya yalnızca container'ları durdurun:

```powershell
docker compose stop policy-db redis keycloak
```

Volume removal bilinçli olarak hariç tutulur; local evidence'ı yok eder ve ayrı explicit karar gerektirir.
