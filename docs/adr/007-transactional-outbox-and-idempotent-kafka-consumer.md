# ADR-007: Transactional Outbox ve Idempotent Kafka Consumer

- Durum: Kabul edildi
- Tarih: 2026-09-08

## Bağlam

Authorization, PostgreSQL ile Kafka arasında dual-write failure oluşturmadan
durable bir decision yayınlamalıdır. Claims/Billing Kafka'nın at-least-once
delivery davranışını, consumer restart'larını, offset commit failure'larını ve
poison message'ları tolere etmelidir. HTTP transaction içinde doğrudan publish
etmek, decision persist edildiği halde event yayınlanmamasına veya rollback
edilen decision için event yayınlanmasına yol açabilir.

## Karar

Authorization, `PreAuthorizationApproved` veya `PreAuthorizationRejected`
event'ini aggregate update ile aynı local database transaction içinde
`outbox_messages` tablosuna yazar. Scheduled relay unpublished batch'i lock'lar,
JSON'u `health.authorization.pre-authorization.v1` topic'ine publish eder ve
satırı yalnızca broker acknowledgement sonrasında published olarak işaretler.

Topic key pre-authorization ID'dir; böylece tek aggregate için sıra korunur.
Contract stable event name ve integer version taşır. Claims/Billing approved
event'leri tüketir ve Claim/Invoice ile `processed_messages` entry'sini atomik
olarak oluşturur. Redelivery business operation'ı tekrarlamadan return eder.
Rejected decision'lar durable integration fact'tir ancak claim başlatmaz.

Consumer failure'ları blocking fixed-backoff retry kullanır: varsayılan olarak
bir saniye arayla toplam üç deneme. Tükenen record'lar original key, value,
partition ve diagnostic header'larla eşleşen `.DLT` topic'ine yayınlanır.

## Sonuçlar

- Database state ile publish intent atomiktir.
- Delivery exactly-once değil at-least-once'dur; broker acknowledgement ile
  `published_at` update arasındaki crash sonrasında duplicate publication beklenir.
- Consumer idempotency broker configuration değil business requirement'tır.
- Claims approval HTTP response içinde değil eventually başlar.
- Broker acknowledgement beklenirken outbox lock'ları tutulur. Mevcut ölçekte
  bilinçli olarak basit tutulmuştur; yüksek throughput için lease/claim-based
  relay daha uygundur.
- DLT record'ları daha sonraki observability milestone'da operational
  replay/quarantine process gerektirir.

## Alternatifler

- **Database/Kafka coordinated transaction:** PostgreSQL ile Kafka arasında tek
  atomik transaction sağlamadığı ve operational coupling'i artırdığı için reddedildi.
- **Commit sonrasında doğrudan publish:** process crash event'i kalıcı olarak
  kaybettirebileceği için reddedildi.
- **Yalnızca Kafka exactly-once semantics:** Kafka EOS PostgreSQL aggregate write
  ile broker write'ı tek transaction yapmadığı için reddedildi.
- **CDC/Debezium outbox relay:** güçlü bir production alternatifidir; ek
  infrastructure ve operational ownership gerekçelendirilene kadar ertelendi.
- **Saga/process manager:** kullanılmadı. Bu flow tek event ardından tek local
  transaction içerir ve henüz multi-step compensation policy'si yoktur.
