# ADR-003: Ön provizyon kararlarının optimistic concurrency ile korunması

- Durum: Kabul edildi
- Tarih: 2026-09-03

## Bağlam

İki sigorta uzmanı aynı pending ön provizyonu açıp neredeyse aynı anda farklı
kararlar gönderebilir. Yalnızca aggregate içindeki status kontrolü yeterli
değildir; iki transaction da diğerinin commit'inden önce `PENDING` durumunu
görebilir.

## Karar

Persistence entity bir JPA `@Version` kolonu kullanır. Bu nedenle update
işlemleri transaction'ın okuduğu version değerini içerir. Repository adapter,
stale write adapter'dan çıkılmadan algılansın diye `saveAndFlush` kullanır;
ardından Spring'in `OptimisticLockingFailureException` exception'ını framework
bağımsız `ConcurrentPreAuthorizationUpdateException` application exception'ına
çevirir. REST exception handler bunu RFC 9457 `409 Conflict` response olarak
temsil eder.

PostgreSQL Testcontainers testleri hem Liquibase version kolonunu hem de gerçek
bir stale-update conflict durumunu doğrular. Unit ve MVC slice testleri exception
translation ve HTTP contract'ını doğrular.

## Sonuçlar

- Concurrent kararlar birbirini sessizce overwrite edemez.
- Normal read işlemleri database lock almaz.
- Conflict yaşayan caller yeniden karar vermeden önce güncel state'i tekrar yüklemelidir.
- Her aggregate write'ın flush edilmesi ek bir database round trip oluşturur,
  ancak teknik persistence failure'ların adapter içinde translate edilmesini sağlar.
- Pessimistic locking, transaction aktifken database lock tuttuğu ve throughput'u
  azalttığı için reddedilmiştir.
- Daha genel edit workflow'ları için HTTP `ETag`/`If-Match` contract'ı ileride
  yararlı bir ek olabilir ancak mevcut decision command için gerekli değildir.
