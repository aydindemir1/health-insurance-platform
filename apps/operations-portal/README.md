# Operations Portal

Hastane ve sigorta operasyonları için React ve TypeScript frontend uygulamasıdır.
İlk vertical slice; Keycloak authentication, role-aware navigation,
ön provizyon oluşturma, arama, detay inceleme ve uzman kararlarını destekler.

## Lokal geliştirme

Varsayılan lokal URL'ler ortamınızla eşleşmiyorsa `.env.example` dosyasını
`.env.local` olarak kopyalayın. `.env.local` dosyasını veya credential'ları
asla commit etmeyin.

```powershell
npm install
npm run dev
```

Development server `http://localhost:5173` adresinde çalışır. Keycloak'ın
`http://localhost:8080` adresinde olması beklenir; tüm business API'ler
`http://localhost:9080` adresindeki APISIX üzerinden kullanılır. Bireysel
Spring servis portları Compose ağı içinde kalır.

## Kalite kontrolleri

```powershell
npm run lint
npm test
npm run build
```

## Bağımlılık yönü

Source tree aşağıdaki import yönünü izler:

```text
app -> pages -> widgets -> features -> entities -> shared
```

`src/app/architecture.test.ts` bu kuralı doğrular. Alt katman, üst katmandan
import yapmamalıdır. Bu nedenle shared code business entity'ler hakkında hiçbir
şey bilmez; entity'ler ise kullanıcı iş akışlarından ve page composition'dan
bağımsız kalır.
