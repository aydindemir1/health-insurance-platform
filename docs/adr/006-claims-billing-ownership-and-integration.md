# ADR-006: Claims ve Billing ownership ile Authorization entegrasyonu

- Durum: Kabul edildi
- Tarih: 2026-09-03

## Bağlam

Bir claim yalnızca caller healthcare provider'a ait onaylanmış bir ön
provizyondan başlayabilir. Adjudication daha sonra payable amount üretirken,
invoice reconciliation ve payment farklı bir lifecycle üzerinde devam eder.
Tasarım Authorization rule'larını kopyalamamalı veya başka servisin database'ini
paylaşmamalıdır.

## Karar

Tek bir Claims and Billing bounded context hem `Claim` hem `Invoice`
aggregate'lerinin sahibidir; bunlar tek private PostgreSQL database içinde ayrı
aggregate root'lar olarak modellenir. Claim adjudication decision'ın sahibidir.
Invoice billed/payable amount'ların, payment reference'ların, dispute'ların ve
settlement'ın sahibidir. Transaction decorator claim decision'larını ve bunların
invoice etkisini servis içinde atomik hale getirir.

Milestone 4 için claim creation açık bir hospital command'dır. Servis,
Authorization REST query'sini `ApprovedPreAuthorizationPort` üzerinden senkron
çağırır, caller token'ını relay eder ve fail-closed davranır. `APPROVED`
snapshot gerektirir, trusted provider identity ile eşleşme yapar ve authorized
amount üzerinde invoice'u reddeder. Authorization database'ini asla okumaz.
Unique constraint'ler pre-authorization başına tek claim sağlar ve yarış
durumlarında invoice ile payment reference'larını korur.

## Sonuçlar

- Data ownership ve audit boundary'leri açıktır.
- Kullanıcılar claim başlatırken anlık validation alır.
- Claim creation geçici olarak Authorization availability'ye bağlıdır; kısa
  timeout'lar bu dependency'yi sınırlar.
- Claim ve invoice bağımsız evolve olabilir; local transaction'lar decision
  consistency'yi korur.
- Payment, invoice aggregate içinde immutable entry'ler olarak modellenir.
  Mevcut ölçekte ayrı payment service gerekli değildir.

Milestone 5 approved-authorization ve claims lifecycle event'lerini transactional
outbox ve idempotent consumer'larla yayınlayacaktır. Bu automatic claim workflow
initiation'ı destekleyebilir ancak claim uniqueness guard'ını kaldırmaz veya
Kafka'yı synchronous source of truth haline getirmez. Production service identity
Milestone 8'de client credentials veya token exchange kullanacaktır.

## Değerlendirilen alternatifler

- **Shared Authorization database:** başlangıç maliyeti düşüktür ancak
  database-per-service ownership'i ihlal eder ve schema'ları birbirine bağlar.
- **Claims ve Billing'i şimdi ayrı microservice yapmak:** independent scaling
  sağlar ancak operasyonel ihtiyaç oluşmadan distributed consistency getirir.
- **Şimdi yalnızca event tabanlı creation:** runtime coupling'i azaltır ancak
  Milestone 5 kapsamındaki Outbox, idempotency ve process manager'ı gerektirir.
- **Invoice field'larını Claim içine koymak:** persistence'ı basitleştirir ancak
  claim adjudication ile payment/reconciliation invariant'larını birbirine karıştırır.
