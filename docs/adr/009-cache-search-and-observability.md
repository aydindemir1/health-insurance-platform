# ADR-009: Redis caching, Elasticsearch projection'ları ve observability data'nın ayrılması

- Durum: Kabul edildi
- Tarih: 2026-09-09

## Bağlam

Policy coverage check'leri synchronous authorization path üzerinde tekrarlanır;
operations kullanıcıları ise cross-context claim ve pre-authorization search'e
ihtiyaç duyar. Bunlar farklı problemlerdir: eligibility, Policy source of truth
üzerinde kısa ömürlü acceleration layer gerektirirken search denormalized,
eventually consistent read model gerektirir. Runtime diagnosis ayrıca HTTP ve
message boundary'leri boyunca tek request identifier ve machine-readable log
gerektirir.

## Karar

Policy Service Redis'i cache-aside semantics ile kullanır. Cache key complete
coverage evaluation input'un SHA-256 digest'idir ve value'lar 30 saniye sonra
expire olur. Policy creation ilgili policy'nin tracked key'lerini invalidate
eder. Cache outage fail-open'dır: Policy safe metadata loglar ve PostgreSQL
üzerinden evaluate eder. Policy veya PostgreSQL outage ise eligibility tahmin
edilemeyeceği için Authorization tarafında fail-closed kalır.

Search Service `healthcare-operations-v1` adlı Elasticsearch index'in sahibidir.
Claims/Billing her aggregate transition ile aynı transaction içinde versioned
search projection'ı PostgreSQL outbox'a yazar ve sonra Kafka'ya publish eder.
Search Service ayrıca Authorization decision event'lerini consume eder.
Deterministic document identifier'lar at-least-once redelivery'yi duplicate
yerine overwrite yapar. Hospital user'lar signed `provider_id` ile sınırlandırılır;
insurer role'leri provider'lar arası query yapabilir. Elasticsearch projection'dır,
asla system of record değildir.

Tüm Java servisleri Spring Boot ECS JSON üretir. HTTP filter'ları yalnızca bounded
safe `X-Correlation-ID` value'larını kabul eder; aksi durumda ID üretir, MDC'ye
ekler ve response'a echo eder. Synchronous client'lar bunu propagate eder;
Kafka/RabbitMQ consumer'ları correlation ID'yi message metadata'dan türetir.
Elastic Java agent runtime image'a kopyalanır ve `-javaagent` ile attach edilir;
APM Server telemetry'yi aynı sürüm Elastic Stack'te saklar ve Kibana görselleştirir.

## Sonuçlar

- PostgreSQL authoritative kalır ve cache kaybı business data'yı corrupt etmez.
- Search database sharing olmadan hızlı ve cross-context olur; karşılığında
  eventual consistency ve projection-rebuild operation gerekir.
- Claims search outbox, commit edilmiş financial transition'ın indexing intent'ini
  kaybetmesini engeller.
- Authorization search şu anda decision event'lerini temsil eder; pending request'ler
  Authorization'ın strongly consistent work queue'sundan erişilebilir kalır.
- Redis, Elasticsearch, Kibana ve APM memory ve operational overhead ekler; core
  domain model dışında optional infrastructure'dır.
- Correlation ID navigation'ı geliştirir ancak technical identifier'dır,
  distributed transaction kanıtı değildir.

## Değerlendirilen alternatifler

- Cache annotation'ları reddedildi; çünkü explicit port'lar fallback, invalidation
  ve privacy-safe key'leri use case içinde Spring olmadan test edilebilir kılar.
- Database join veya shared schema'lar bounded context ownership'i ihlal ettiği
  için reddedildi.
- Elasticsearch'e synchronous dual-write, search outage'ın financial transaction'ı
  bozabilmesi veya update kaybettirebilmesi nedeniyle reddedildi.
- Logback-specific JSON encoder'lar reddedildi; Spring Boot ECS structured logging
  sağlar ve MDC field'larını otomatik içerir.
- APM agent'ı application dependency olarak eklemek reddedildi; desteklenen
  external-agent attachment instrumentation'ı domain/application code dışında tutar.
