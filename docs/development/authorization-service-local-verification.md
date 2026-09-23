# Authorization Service lokal doğrulaması

Bu rehber Authorization Service için odaklı öğrenme ve doğrulama yoludur. Sentetik veri kullanır ve tüm platformun çalışmasını gerektirmez.

## Bu çalışma neyi kanıtlar?

- Java 21/Spring Boot application wiring ve PostgreSQL persistence
- Keycloak authentication, role check'leri ve provider ownership
- Submission öncesinde senkron Policy coverage validation
- Legal `PENDING -> APPROVED|REJECTED` transition'ları ve optimistic concurrency
- Aggregate, audit, Kafka outbox ve RabbitMQ task-outbox transaction boundary'leri
- Broker çağrısını HTTP transaction içine koymadan asynchronous publication
- Unauthenticated, invalid, forbidden ve conflicting operation'lar için RFC 9457 error'ları

## Architecture öğrenme haritası

| Java/Spring öğesi | Sorumluluk | Tanıdık .NET karşılığı |
| --- | --- | --- |
| REST controller | HTTP mapping ve validation | ASP.NET Core Controller |
| input port/use case | application capability ve orchestration | application service/command handler |
| aggregate | lifecycle invariant'larını korur | rich domain entity/aggregate |
| output port | framework bağımsız dependency contract | application interface |
| JPA adapter | PostgreSQL mapping | EF Core repository adapter |
| transaction decorator | application write'ları etrafında tek transaction | transactional decorator/unit of work |
| Spring Security resource server | JWT ve authority doğrulaması | ASP.NET Core JWT bearer authorization |
| Liquibase changeset | versioned database evolution | EF Core migration/DbUp script |

## Hızlı otomatik doğrulama

`services/authorization-service` dizininden:

```powershell
.\mvnw.cmd --batch-mode test
```

Mevcut checkpoint 78 başarılı test içerir. Suite domain, application authorization/ownership, controller contract'ları, Spring bean wiring, PostgreSQL/Testcontainers persistence ve concurrency, transactional outbox, relay behavior ve ArchUnit dependency rule'larını kapsar. Gelecekteki JDK'larla ilgili Mockito agent warning bilgilendirme amaçlıdır; failed test değildir.

## Odaklı lokal runtime

Bu bounded context için yalnızca gerekli dependency'leri mevcut local image'larla başlatın:

```powershell
docker compose up -d authorization-db kafka rabbitmq keycloak
```

Claims/Search data seed etmeden local identity'leri hazırlayın:

```powershell
$env:DEMO_KEYCLOAK_ADMIN_USERNAME = "<local-admin>"
$env:DEMO_KEYCLOAK_ADMIN_PASSWORD = "<local-admin-password>"
$env:DEMO_USER_PASSWORD = "<temporary-demo-password>"
.\demo\prepare-and-seed-local-demo.ps1 -SkipDataSeed
```

Policy Service'i `8082`, ardından Authorization Service'i `8081` üzerinde başlatın. Database credential ve JWT'leri yalnızca process environment variable'larında tutun. `HOSPITAL_USER` olarak submit yapın, oluşturulan `PENDING` request'i okuyun ve `INSURANCE_SPECIALIST` olarak karar verin. Aynı decision tekrarlandığında `409` dönmelidir.

Doğrulanmış sentetik checkpoint şu sonuçları üretti:

- unauthenticated collection request: `401 application/problem+json`
- submitted request: `PENDING`, trusted token provider scope
- specialist decision: `APPROVED`
- repeated decision: `409 Conflict`
- persisted aggregate version: `1`
- uygulanan Authorization migration sayısı: `8`
- Kafka event outbox: broker acknowledged, one attempt
- RabbitMQ task outbox: broker acknowledged, one attempt
- audit action'ları: `PRE_AUTHORIZATION_SUBMITTED`, `PRE_AUTHORIZATION_APPROVED`

## Güvenli evidence inceleme

UUID'yi API'nin döndürdüğü sentetik request ile değiştirin. Evidence içinde payload, member, diagnosis, policy, token veya credential value göstermeyin.

```powershell
docker compose exec -T authorization-db psql -U authorization_local -d authorization -c "select id,status,version,provider_id,created_at,decided_at from pre_authorizations where id='<synthetic-uuid>';"
docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages consumers durable
$env:AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID = "<synthetic-uuid>"
Set-Location apps/operations-portal
npm run screenshots:authorization
npm run screenshots:authorization-infrastructure
```

Kafka native runtime image broker executable'ını içerir, klasik `kafka-topics.sh` toolbox'ını değil. Broker health ile outbox relay'in acknowledged `published_at` değeri local publication evidence'dır. Ayrı CLI container bu bounded-context checkpoint'e anlamlı ek kanıt sağlamadan download/runtime maliyeti ekler.

## Yorumlama ve sınırlar

PostgreSQL authoritative'dir. `published_at`, relay'in broker acknowledgement aldığını kanıtlar; downstream consumer'ların tamamladığını kanıtlamaz. Kafka durable integration event sağlar; RabbitMQ notification work dağıtır. Bunlar aynı problemi çözmez. Provider ownership client input'tan değil verified token'dan alınır. Test ve screenshot checkpoint portfolio evidence'dır; production capacity, penetration veya availability iddiası değildir.

## Requirement-to-evidence traceability

| Concern | Implementation boundary | Automated/live evidence |
| --- | --- | --- |
| provider ownership | verified JWT claim -> application `ActorContext` | controller/use-case testleri ve `401/403` runtime contract |
| submission öncesi coverage | `CoverageVerificationPort` ve fail-closed REST adapter | adapter testleri + successful synthetic submission |
| legal decision'lar | `PreAuthorization` aggregate | domain testleri + `PENDING -> APPROVED -> 409` runtime flow |
| concurrent decision'lar | application conflict'e çevrilen JPA optimistic version | PostgreSQL integration testi + persisted version evidence |
| atomic decision side effect'leri | state, audit ve iki outbox'ı saran transaction decorator | transaction integration testi + matching committed row'lar |
| Kafka business event | Kafka outbox relay | broker topic partition'ları + acknowledged outbox evidence |
| RabbitMQ notification work | notification task outbox relay | durable queue/DLQ topology + acknowledged outbox evidence |
| architecture direction | inner-layer dependency allowlist'leri | ArchUnit suite |

Bu tablo service için okuma sırasıdır: önce business rule'u bulun, owning layer'ı belirleyin, focused test'i çalıştırın, ardından safe live evidence'ı inceleyin. Screenshot executable verification'ı destekler; yerine geçmez.

Detaylar için [Authorization architecture](../architecture/authorization-service.md), [business analysis](../business/authorization-service-business-analysis.md) ve [demo scenario](../demo/demo-scenario.md) dosyalarına bakın.
