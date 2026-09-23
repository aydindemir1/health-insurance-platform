# ADR-002: Provider sahipliğinin authenticated identity üzerinden türetilmesi

- Durum: Kabul edildi
- Tarih: 2026-09-03

## Bağlam

Bir hastane kullanıcısı bir healthcare provider adına ön provizyon talepleri
gönderir ve okur. HTTP request body içinden `providerId` kabul etmek,
authenticated bir hastane kullanıcısının başka bir provider'ı taklit etmesine
izin verebilir. Yalnızca role check yapılması record-level authorization sağlamaz.

İlk product modelinde her hastane kullanıcısı tek bir provider'a atanır. Gelecek
bir sürüm kullanıcının birden fazla provider'ı temsil etmesine izin verebilir.

## Karar

Keycloak UUID değerli `providerId` user attribute'unu saklar ve bunu
`provider_id` access-token claim'ine map eder. Presentation boundary,
doğrulanmış JWT claim'lerini ve authority'leri application `ActorContext`
modeline dönüştürür.

Submit use case sahipliği yalnızca bu actor context'ten türetir. Request body
`providerId` içermez. Application layer, endpoint role check'lerine ek olarak
şu policy'leri bağımsız biçimde uygular:

- `HOSPITAL_USER` yalnızca kendi provider'ı adına submit yapabilir.
- `HOSPITAL_USER` yalnızca kendi provider'ına ait talepleri okuyabilir.
- `INSURANCE_SPECIALIST` tüm provider'ların taleplerini okuyabilir ve karar verebilir.
- `SYSTEM_ADMIN` talepleri okuyabilir ancak tıbbi veya sigorta kararı veremez.

## Sonuçlar

- Caller JSON input'u değiştirerek başka bir provider seçemez.
- Bir use case HTTP controller dışında çağrılsa da authorization uygulanmaya devam eder.
- Hastane kullanıcılarının token'larında geçerli bir `provider_id` UUID bulunmalıdır.
- Bir kullanıcı başka provider'a taşındığında identity verisi güncellenmeli ve
  yeni token üretilmelidir.
- İleride birden fazla provider desteği, tek claim yerine membership lookup veya
  allowed-provider collection gerektirir. Bu ek model gerçek bir workflow
  ihtiyaç duyana kadar ertelenmiştir.
