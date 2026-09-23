# ADR-004: Operations Portal'ın business capability'lere göre düzenlenmesi

- Durum: Kabul edildi
- Tarih: 2026-09-03

## Bağlam

Portal, ön provizyon operasyonlarından policy, claims, billing ve reporting
workflow'larına genişleyecektir. Yalnızca `components`, `hooks` ve
`services` gibi teknik türlere dayalı klasör yapısı ilgisiz domain
capability'lerini karıştırır ve ownership'i belirsiz hale getirir.

## Karar

React application pragmatik bir Feature-Sliced dependency direction kullanır:

```text
app -> pages -> widgets -> features -> entities -> shared
```

- `app` provider'ları, routing'i, error handling'i ve global style'ları compose eder.
- `pages` reusable business behavior sahibi olmadan tam route'ları compose eder.
- `widgets` application shell gibi daha büyük layout composition'ları sağlar.
- `features` authentication, submission ve specialist decision gibi user action'larını uygular.
- `entities` domain-facing type'ları, API function'larını ve visual primitive'leri içerir.
- `shared` domain-agnostic HTTP, configuration, state ve UI helper'larını içerir.

Bir Vitest architecture testi alias import'larını tarar ve bu katmanlar içinde
yukarı yönlü dependency'leri reddeder. Server state TanStack Query tarafından
yönetilir; API data global client-state store'a kopyalanmaz.

## Sonuçlar

- Portal büyüdükçe business capability'leri kolayca bulunabilir kalır.
- Alt katmanlar route veya application setup'a bağımlı olmadan yeniden kullanılabilir.
- Bazı küçük feature'lar flat starter application'a göre daha fazla dosya gerektirir.
- Cross-feature import'lardan kaçınılır; shared behavior bilinçli olarak entity
  veya shared module seviyesine yükseltilmelidir.
- Somut bir client-state problemi oluşana kadar global state library eklenmez.
