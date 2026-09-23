# Claims and Billing Service lokal doğrulaması

Bu rehber, önceki milestone'ları yeniden doğrulamadan uygulanmış Claims/Billing bounded context'ini öğrenmek ve test etmek için odaklı bir sıra sağlar.

## Okuma sırası

1. Adjudication transition ve amount rule'ları için `Claim`.
2. Reconciliation, payment ve settlement rule'ları için `Invoice`.
3. Role, ownership, transaction composition, audit write ve search projection intent için `ClaimsBillingApplicationService`.
4. Kafka contract boundary için `PreAuthorizationDecisionListener`.
5. Concurrency ve uniqueness için JPA adapter'ları ve Liquibase changeset'leri.
6. Code reading'i doğrulamak için test ve live evidence kullanın; yerine geçirmeyin.

## Hızlı otomatik doğrulama

`services/claims-billing-service` dizininden:

```powershell
.\mvnw.cmd --batch-mode test
```

Doğrulanmış checkpoint:

- Java: `21.0.8`
- test: `57`
- failure/error/skip: `0/0/0`
- PostgreSQL integration runtime: Testcontainers `postgres:17-alpine`
- Kafka integration runtime: Testcontainers `apache/kafka-native:4.1.1`
- sonuç: `BUILD SUCCESS`

Mockito dynamic-agent mesajı future-JDK compatibility warning'dir; failed business veya integration test değildir.

## Requirement-to-evidence traceability

| Concern | Owning implementation | Evidence |
| --- | --- | --- |
| claim transition'ları | `Claim` aggregate | `ClaimTest` |
| invoice reconciliation/payment | `Invoice` aggregate | `InvoiceTest` |
| role ve provider ownership | application service + REST annotation'ları | application/controller testleri |
| Claim + Invoice atomicity | transaction decorator/application wiring | transaction integration testleri |
| concurrent write'lar | JPA `@Version` | PostgreSQL repository integration testi |
| duplicate Kafka delivery | processed-message inbox + unique pre-authorization | Kafka integration testi tam bir pair oluşturur |
| poison event recovery | bounded retry + DLT | Kafka integration testi original invalid payload'ı DLT'den okur |
| recoverable search indexing | claim-search transactional outbox | application ve transaction testleri |
| append-only minimized audit | audit port/JDBC adapter/Liquibase guard'ları | audit use-case ve transaction testleri |
| synchronous Authorization boundary | token-relaying REST adapter + validated application record | yedi adapter contract/failure testi |
| dependency direction | Clean Architecture package rule'ları | dört ArchUnit testi |

## 57 testlik sonuç ne anlama gelir?

Sonuç deterministic domain behavior, dependency wiring, gerçek PostgreSQL mapping/migration ve gerçek Kafka consumer retry/idempotency davranışını gösterir. Production load capacity, external bank integration veya complete end-to-end deployment health iddiası değildir.

Hardening checkpoint ayrıca fresh Testcontainer ve mevcut local dataset üzerinde beş Liquibase migration'ı, 13 owner-table lifecycle constraint'i, invalid rehydration rejection, allowlist-based inner-layer ArchUnit rule'ları ve RFC 9457 filter-level `401/403` response'larını doğrular.

`prepare-and-seed-local-demo.ps1 -SkipDataSeed`, ikinci sentetik `provider_id` ile `hospital-other-provider-demo` kullanıcısını da hazırlar. Valid `HOSPITAL_USER` token'ın ilk provider'ın Claim'ini okurken hâlâ `403` aldığını göstermek için kullanın. Bu authentication success ile resource ownership authorization'ı ayırır.

Live ownership check, ikinci synthetic provider için valid token ile ilk provider'ın Claim'ine karşı çalıştırıldı. `403 application/problem+json` döndü ve Claim data disclose edilmedi. Unauthenticated request bağımsız olarak aynı media type ile `401` döndürdü.

Synchronous manual-claim adapter fail-closed olarak doğrulandı: bearer token'ı relay eder ve yalnızca complete, identity-matching, positive-money Authorization contract ile allowlisted status kabul eder. Network/`5xx` failure, malformed JSON, missing field, mismatched response ID, invalid value ve missing bearer token; Claim oluşturulmadan önce dependency unavailability olarak map edilir (API boundary'de `503`). Authorization `404`, ayrı not-approved/not-found business path olarak kalır.

## Sonraki live-runtime checkpoint

Sonraki adım workflow tarafından kullanılan mevcut Claims/Billing dependency'lerini başlatmalı, bir synthetic approved Authorization event consume etmeli ve şunları incelemelidir:

- bir `claims` row ve `SUBMITTED` status;
- bağlı bir `invoices` row, `ISSUED`;
- bir `processed_messages` idempotency marker;
- Claim/Invoice audit row'ları;
- bir claim-search outbox row;
- Kafka source topic ve DLT metadata;
- authenticated provider-scoped read'ler;
- review, approval, dispute/match, payment ve settlement transition'ları.

Ayrı PostgreSQL ve Kafka screenshot'ları bu live run'dan üretilmelidir. Token, message payload, member/policy/service value, money, payment reference ve credential gösterilmemelidir.

## Doğrulanmış live checkpoint

Port `8083` üzerindeki source-run service daha önce publish edilmiş synthetic Authorization approval'ı consume etti ve tam olarak bir `SUBMITTED` Claim ile bir `ISSUED` Invoice oluşturdu. Gerçek role-bearing Keycloak token'lar şu akışı yürüttü:

```text
Claim:   SUBMITTED -> UNDER_REVIEW -> APPROVED
Invoice: ISSUED -> MATCHED -> SETTLED
```

Repeated claim approval `409` döndürdü. PostgreSQL Claim ve Invoice için optimistic version `2`, bir payment row, altı minimized audit action, dört successive search-projection outbox snapshot ve Kafka event identity'yi `claims-pre-authorization-approved-v1` ile bağlayan processed-message inbox marker kaydetti.

Workflow'u tekrar etmeden safe evidence capture edin:

```powershell
$env:CLAIMS_SCREENSHOT_CLAIM_ID = "<synthetic-claim-uuid>"
Set-Location apps/operations-portal
npm run screenshots:claims
```

## .NET karşılaştırması

| Java/Spring | .NET karşılığı |
| --- | --- |
| aggregate method'ları | rich domain entity method'ları |
| input port/use case | application command handler/service |
| JPA/Hibernate adapter | EF Core repository implementation |
| `@Version` | optimistic concurrency token/row version |
| Spring Kafka listener | MassTransit/Kafka consumer |
| processed-message table | consumer inbox/idempotency store |
| transactional outbox | EF Core transaction + outbox entity |
| Spring transaction decorator | Unit of Work/application decorator |

Business anlamı ve state diagram'ları için [Claims/Billing business analysis](../business/claims-billing-service-business-analysis.md) dosyasına bakın.
