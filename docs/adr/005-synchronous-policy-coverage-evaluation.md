# ADR-005: Senkron poliçe kapsam değerlendirmesi

- Durum: Kabul edildi
- Tarih: 2026-09-03

## Uygulama durumu güncellemesi (2026-09-14)

Daha sonraki event-driven milestone benefit reservation eklememiştir; coverage
evaluation bilinçli olarak read-only kalır. Event delivery tek başına concurrent
benefit consumption'ı güvenli hale getirmez. Gelecekteki bir uygulama idempotent
reservation ve release command'ları, optimistic concurrency ve açık compensation
veya process coordination gerektirecektir.

Lokal portföy topolojisi de end-user bearer token'ı relay etmeye devam eder.
Gateway milestone edge security policy'lerini eklemiş ancak workload identity
getirmemiştir. Bu nedenle OAuth 2.0 client credentials veya token exchange,
tamamlanmış bir capability değil production-hardening kararı olarak kalır.

## Bağlam

Bir hastane, ön provizyon kabul edilmeden önce üyenin poliçesinin talep edilen
sağlık hizmetini kapsayıp kapsamadığını bilmelidir. Policy validity, ownership,
benefit, currency ve limit'ler Policy bounded context'ine aittir. Bu rule'ları
Authorization içinde kopyalamak veya Policy database'ini Authorization'dan
okumak iki source of truth oluşturur ve database-per-service ownership'i ihlal eder.

## Karar

Authorization, pending ön provizyon oluşturmadan önce Policy Service'in
coverage-evaluation REST endpoint'ini senkron çağırır. Application layer yalnızca
`CoverageVerificationPort`'a bağımlıdır; HTTP implementation outbound
infrastructure adapter'dır. Request policy number, member, service code, amount,
currency ve service date içerir.

Bu lokal milestone için caller'ın bearer token'ı relay edilir. Her iki servis de
application-level role'leri uygulamaya devam eder. Production service-to-service
identity, Milestone 8'de gateway ve security model tamamlandığında OAuth 2.0 client
credentials veya token exchange'e taşınacaktır; şu anda hiçbir client secret
commit edilmez.

Çağrıda kısa connection ve response timeout'ları vardır. Policy'ye ulaşılamazsa
Authorization `503 Service Unavailable` ile fail-closed davranır ve hiçbir ön
provizyon persist edilmez. Business denial, stable denial code ile
`422 Unprocessable Content` döndürür.

Coverage evaluation bu milestone'da read-only'dir: mevcut used amount ile request'i
kontrol eder fakat limit rezerve etmez. Limit reservation, release ve compensation
idempotency ile process manager gerektirir ve event-driven milestone'a ertelenmiştir.

## Sonuçlar

- Policy rule'ları ve verisi tek bounded context tarafından sahiplenilmeye devam eder.
- Kullanıcı anlık ve deterministik eligibility yanıtı alır.
- Submit sırasında Authorization availability artık Policy availability'ye bağlıdır.
- Timeout'lar dependency'yi sınırlar ancak retry ve circuit-breaker policy'leri
  gelecekteki çalışmadır; gelecekteki reservation command'ını körlemesine retry
  etmek güvenli olmaz.
- Reservation ve optimistic concurrency uygulanana kadar concurrent eligible
  evaluation'lar limitin aşılmasına izin verebilir.

## Değerlendirilen alternatifler

- **Shared database:** başlangıçta daha basittir ancak ownership ve independent
  evolution'ı bozar.
- **Replicated policy read model:** availability'yi artırır ancak staleness
  getirir; policy-change event'leri ve reconciliation gerektirir.
- **Tamamen asynchronous validation:** senkron dependency'yi kaldırır ancak
  hastane final response almadan önce `PENDING_VALIDATION` state ve process
  manager gerektirir.
