# ADR-008: RabbitMQ Notification Task Delivery

- Durum: Kabul edildi ve uygulandı
- Tarih: 2026-09-08

## Bağlam

Kafka bounded context'ler arasında durable business fact'leri zaten taşır.
Notification farklıdır: bir worker'dan retry edilebilir iş yapmasını isteyen
operational command'dır. Task competition için Kafka'yı tekrar kullanmak bu
semantics'i bulanıklaştırır; database write yanında doğrudan RabbitMQ publish
etmek ise dual-write failure penceresini yeniden oluşturur.

Notification payload'ları member, policy, diagnosis veya contact data
açıklamamalıdır. İlk use case provider'a bir pre-authorization'ın approved veya
rejected olduğunu bildirmektir.

## Karar

- Kafka integration-event stream olarak kalır; RabbitMQ notification delivery
  command'larını taşır.
- Authorization, notification task'ı decision ile aynı transaction içinde özel
  local outbox'a persist eder. Scheduled AMQP relay bunu persistent message olarak
  publish eder ve correlated publisher confirm bekler.
- Mandatory publishing ve publisher return etkinleştirilmiştir. Returned message
  ile birlikte gelen positive broker confirm yine failure kabul edilir; çünkü
  routing key hiçbir queue tarafından kabul edilmemiştir.
- Durable direct exchange işi tek delivery queue'ya route eder. Retry'ları tükenen
  mesajlar requeue edilmeden reject edilir ve dead-letter queue'ya yönlendirilir.
- Task; `taskId`, `causationId`, notification type, provider recipient
  reference, business reference, template key ve contract version içerir. Member,
  policy, diagnosis, token, email veya phone değeri içermez.
- Notification Worker kendi PostgreSQL database'indeki delivery record'ların
  sahibidir. `taskId`, broker-consumer ve downstream-provider idempotency key'dir.
- Worker idempotency lookup öncesinde `taskId`'den türetilmiş PostgreSQL
  transaction advisory lock alır. Aynı task consumer'ları sender side effect
  öncesinde serialize edilir; ilgisiz task'lar concurrent kalır.
- Worker yalnızca transactional application use case return ettikten sonra manuel
  acknowledgement gönderir. Unsupported contract ve failed processing,
  broker dead-letter routing bunları quarantine edebilsin diye requeue edilmeden reject edilir.
- Yalnızca `TransientNotificationDeliveryException` retry edilebilir. Listener
  varsayılan olarak bounded exponential backoff ile toplam üç deneme yapar
  (`250 ms`, `500 ms`, maksimum `2 s`); contract/version error'ları ve diğer
  permanent failure'lar bir kez denenir. Tükenen işler `requeue=false` ile
  reject edilir ve RabbitMQ bunları durable DLQ'ya route eder.
- Sender port bu idempotency key'i gelecekteki email/SMS provider'a forward
  etmelidir. Worker provider request'i kabul ettikten sonra local commit öncesi
  crash olsa bile duplicate external side effect'leri sınırlar.

## Sonuçlar

- RabbitMQ ve Kafka farklı ve açıklanabilir problemleri çözer.
- Producer ve consumer at-least-once kalır; duplicate work beklenir.
- RabbitMQ task'ları dağıttığı için Notification Worker horizontal scale edebilir.
- Contact resolution ve gerçek external email/SMS provider ayrı security ve
  integration kararları olarak kalır.
- Implementation; framework bağımsız worker core, private PostgreSQL/Liquibase
  persistence, Authorization producer outbox ve rollback proof, confirm-aware
  relay, durable exchange/queue/DLX/DLQ topology, version-aware listener,
  per-attempt transaction'lar, bounded retry ve manual acknowledgement içerir.
  Compose ve Testcontainers gerçek RabbitMQ broker'ı çalıştırır.
- Retry transaction decorator dışında gerçekleşir. Her transient attempt yeni
  transaction alır: failed attempt rollback olur, successful attempt commit olur,
  ardından listener broker delivery'yi acknowledge eder.
- Advisory lock bu infrastructure adapter'ı bilinçli olarak PostgreSQL'e bağlar.
  64-bit hash collision ilgisiz task'ları serialize edebilir ancak data corrupt
  edemez; lock transaction-scoped'dur ve cleanup table gerektirmez.
- Relay şu anda pessimistic database lock tutarken confirm bekler. Mevcut workload
  için bilinçli olarak basit ve güvenlidir; asynchronous batching gelecekteki
  throughput optimization'dır.

## Alternatifler

- **Worker'ın Kafka'yı doğrudan consume etmesi:** event stream'i competing work
  queue olarak kullanacağı ve RabbitMQ'nun hedeflenen task-delivery sorumluluğunu
  göstermeyeceği için reddedildi.
- **Request transaction içinden RabbitMQ'ya doğrudan publish:** database/broker
  dual-write penceresi nedeniyle reddedildi.
- **Task içine email veya phone koymak:** broker payload'ları sensitive contact
  data source'u olmamalı olduğu için reddedildi.
- **Exactly-once delivery varsaymak:** acknowledgement kaybolabileceği ve external
  side effect'lerin kendi idempotency contract'ına ihtiyacı olduğu için reddedildi.
- **Her exception'ı retry etmek:** malformed/unsupported contract'lar ve violated
  invariant'lar zamanla geçerli hale gelmez; retry quarantine'i geciktirir ve
  consumer capacity tüketir.
