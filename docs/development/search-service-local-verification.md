# Search Service lokal doğrulaması

Bu rehber Elasticsearch projection'ın öğrenilmesini ve doğrulanmasını izole eder.

## Okuma sırası

1. `SearchRecord` — framework bağımsız projection invariant'ları.
2. `SearchApplicationService` — role ve provider scope.
3. `SearchProjectionListeners` — Kafka contract mapping ve correlation.
4. `ElasticsearchSearchIndex` — mapping, conditional upsert, filter ve alias.
5. `SearchRebuildService` — candidate lifecycle ve count validation.
6. `SecurityConfiguration` — JWT role'leri, method security ve RFC 9457 handler'ları.

## Otomatik doğrulama

Docker Desktop erişilebilirken `services/search-service` dizininden:

```powershell
.\mvnw.cmd --batch-mode test
```

Unprivileged sandbox, Docker named pipe erişilemediği için beş Elasticsearch testini skip edip yine de `BUILD SUCCESS` raporlayabilir. Complete checkpoint için yalnızca `Skipped: 0` sonucu kabul edilmelidir.

Doğrulanan bileşenler:

- Java `21.0.8`
- Elasticsearch Testcontainer `9.5.3`
- `21/21` test başarılı; failure `0`, error `0`, skipped `0`
- stable alias creation ve legacy-index attachment
- filtering ve pagination
- hyphen içeren identifier'ların plain AND search input olarak ele alınması
- newer/older/equal source-revision behavior
- versioned candidate activation ve rollback
- provider/application authorization ve filter-level RFC 9457
- permanent contract failure'ların retry bypass etmesi; transient failure'ların DLT recovery öncesi configured bounded retry budget'i kullanması
- Clean Architecture dependency rule

## Live doğrulama sonucu

Port `8084` üzerindeki source-run service `UP` raporladı, iki Kafka consumer group'a katıldı ve mevcut local Elasticsearch volume'u kullandı:

```text
stable alias: healthcare-operations
active index: healthcare-operations-v2-c2696b1ac70e4db79eb522807c680a30
documents:   71
```

Gerçek Keycloak token'ları şu sonuçları üretti:

```text
Hospital own provider:      200, five returned rows, one provider UUID
Hospital foreign provider:  403 application/problem+json
Specialist page size 1:     200, one row, 22 matching Claim documents
Unauthenticated search:     401 application/problem+json + correlation ID
Hospital rebuild create:    403
```

Admin rehearsal `healthcare-operations-v99-9379d0bd201942a3bf79b39c8c2385b2` candidate'ını oluşturdu, bir sentetik record ingest etti, count `1` doğrulaması sonrası activate etti ve ardından rollback yaptı. Stable alias original 71-document v2 index'e döndü; candidate ve predecessor retained kaldı.

Local `apache/kafka-native:4.1.1` image Kafka CLI producer/offset script'lerini içermediğinden running broker'a artificial poison message inject edilmedi. Retry classification doğrudan Spring Kafka error-handler boundary'de doğrulanır; production listener/DLT topology değişmez.

## Screenshot evidence

Successful rebuild/rollback rehearsal sonrasında:

```powershell
Set-Location apps/operations-portal
npm run screenshots:recovery
```

Updated `11-search-rebuild-recovery.png`; active alias, 71-document count, 55-document retained v1 predecessor ve source-owned/race-safe/rollback-ready özelliklerini gösterir. Token, credential veya document payload içermez.

## .NET karşılaştırması

| Java/Spring/Elastic | .NET karşılığı |
| --- | --- |
| Kafka listener projection | Read model oluşturan MassTransit/Kafka consumer |
| Elasticsearch Java client | Elastic .NET client |
| Painless conditional upsert | scripted optimistic projection update |
| stable alias swap | .NET client üzerinden atomic Elasticsearch alias API |
| application provider scope | authorization handler + tenant filter |
| `@PreAuthorize` | `[Authorize(Roles=...)]` |
| `ProblemDetail` | ASP.NET Core `ProblemDetails` |
