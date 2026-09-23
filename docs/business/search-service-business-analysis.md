# Search Service iş analizi

Bu doküman uygulanmış cross-context operations search'ü açıklar. Search derived read model'dir; policy, authorization, claim, invoice veya payment decision'ları için hiçbir zaman source of truth değildir.

## Amaç ve sahiplik

Search Service Kafka üzerinden versioned Authorization ve Claims/Billing projection'larını consume eder ve stable `healthcare-operations` alias arkasındaki Elasticsearch document'larının sahibidir. Source service'ler business ownership'i korur ve transactional outbox'ları üzerinden recoverable projection intent publish eder.

Portal hızlı operational discovery için Search kullanır. Tüm command ve final state check'leri owner servise geri döner; search hit business operation authorize veya settle edemez.

## Aktörler ve scope

| Aktör | Uygulanan search scope |
| --- | --- |
| `HOSPITAL_USER` | Yalnızca signed JWT `provider_id` ile eşleşen document'lar |
| `INSURANCE_SPECIALIST` | Cross-provider operations search |
| `CLAIM_APPROVER` | Cross-provider claim/authorization discovery |
| `SYSTEM_ADMIN` | Operations search ve controlled rebuild API'leri |

Hospital query-string `providerId` ile scope'u genişletemez. Farklı provider verilirse `403` döner; verilmezse JWT provider otomatik uygulanır. Rebuild endpoint'leri hem method security hem application use case içinde `SYSTEM_ADMIN` gerektirir.

## Projection ve query flow

Kafka event'leri at-least-once deliver eder. Deterministic document ID duplicate identity'yi ortadan kaldırırken owner-defined monotonic `sourceRevision` stale state regression'ı engeller. Yeni document oluşturulur; yalnızca strictly newer revision replace eder. Equal ve older revision no-op'tur; böylece divergent equal-revision duplicate yalnızca arrival order nedeniyle kazanamaz.

Query'ler operational field'lar üzerinde free text; ayrıca type, status, provider, page ve size filter'larını destekler. Page size 1–100 ile bounded'dır ve sorting newest `occurredAt` first'tür. Elasticsearch tasarım gereği eventually consistent'tir.

## Rebuild ve rollback

`SYSTEM_ADMIN` isolated physical candidate oluşturur. Owner API'leri stable, bounded snapshot export eder; Search bunları validate edip index'ler. Activation candidate'ı refresh eder, distinct count'u karşılaştırır, expected current alias'ı kontrol eder ve tek atomic alias swap gerçekleştirir. Predecessor explicit rollback için korunur ve hiçbir index otomatik silinmez.

İlk run registry process-local'dır. Restart Elasticsearch data'yı güvenli bırakır ancak o API run'ını resume edemez; operator alias'ı inspect eder ve yeni rehearsal başlatır. Bu explicit portfolio boundary'dir; durable production workflow engine iddiası değildir.

## Failure ve security behavior

| Durum | Sonuç |
| --- | --- |
| Missing/invalid token | RFC 9457 `401 application/problem+json` |
| Hospital başka provider ister | RFC 9457 `403`; data disclosure yok |
| Non-admin rebuild request | Mutation öncesi filter/method/application denial |
| Invalid type/page/size | RFC 9457 `400` |
| Stale/equal projection | Elasticsearch no-op |
| Count veya alias compare mismatch | `409`; active alias unchanged |
| Malformed/wrong-type/version Kafka projection | Immediate source-topic DLT |
| Transient projection failure | Bounded retry ardından source-topic DLT |
| Elasticsearch unavailable | Owner write'ları kendi outbox'larında committed kalır |

## Doğrulanmış checkpoint

Final Java 21 suite `21/21` testten geçti; failure `0`, error `0`, skipped `0`.
Yedi test gerçek Elasticsearch 9.5.3 Testcontainer üzerinde çalıştı; equal-revision
convergence scenario dahil. Filter-level security MVC testleriyle, Spring Kafka
listener/error-handler testleri ise contract validation ve permanent-versus-transient
failure classification ile kapsanır.

Live evidence 71 indexed synthetic document, hospital provider scoping, specialist
pagination, RFC 9457 authentication failure, administrator-only rebuild, one-document
count validation, atomic activation, retained candidate ve original v2 write index'e
rollback davranışını doğruladı.

Bkz. [search architecture](../architecture/search-and-observability.md),
[recovery ADR](../adr/012-versioned-search-rebuild-and-controlled-message-recovery.md)
ve [local verification](../development/search-service-local-verification.md).
