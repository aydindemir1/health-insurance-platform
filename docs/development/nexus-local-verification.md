# Nexus lokal doğrulaması

## Amaç

Nexus, blocking Quality Gate sonrasında versioned Maven artifact'ların sahibidir. Bu checkpoint service'leri rebuild etmeden, artifact'ları republish etmeden veya yeni Nexus image pull etmeden mevcut local repository'yi doğrular.

## Runtime doğrulaması — 15 Eylül 2026

Persist edilmiş Nexus instance mevcut `sonatype/nexus3:3.84.1` image ile başlatıldı:

```powershell
docker compose -f infra/cicd/compose.artifacts.yaml `
  up --detach --no-build --pull never
```

| Kontrol | Doğrulanan sonuç |
|---|---|
| Container health | `127.0.0.1:8087` üzerinde Healthy |
| Community Edition EULA | Daha önce explicit user-approved administration step ile kabul edildi |
| Hosted repository'ler | `maven-snapshots` ve `maven-releases`, Maven 2 format |
| Publisher identity | `jenkins-publisher`, active, yalnızca `health-publisher` role |
| Publisher privilege'ları | İki project Maven repository ile sınırlı sekiz add/browse/edit/read privilege |
| Beklenmeyen privilege | Sıfır |
| Snapshot component | Altı: iki Authorization publication ve diğer her Java service için bir publication |
| Anonymous component API | Hardening sonrası `403 Forbidden` |
| Authenticated component API | Publisher identity ile `200 OK` |

Publisher şu gerçek artifact'ı başarıyla indirdi:

```text
com.aydindemir.health:authorization-service:0.0.1-20260910.194813-1
bytes: 93188778
sha256: 1a0bc4c9d1280a309432503e605c441d537a298ffb271cc78f490453ad41691c
```

Hash Nexus'tan byte-level retrieval'ı kanıtlar; signature veya provenance attestation olarak sunulmaz.

## Güvenlik düzeltmesi

Persist edilmiş Nexus default'u anonymous read'e hâlâ izin veriyordu; unauthenticated component metadata `200` dönüyordu. Anonymous access runtime'da kapatıldı ve bootstrap script aynı policy'yi idempotent biçimde enforce edecek şekilde güncellendi. Unauthenticated request artık `403`, least-privilege Jenkins publisher ise artifact browse/download için `200` alır.

Credential'lar yalnızca ignore edilen `infra/cicd/.env` ve Jenkins credential storage içinde kalır. Administrator ve publisher secret'ları yazdırılmaz veya commit edilmez.

## Artifact envanteri

```text
authorization-service    0.0.1-20260910.194813-1
authorization-service    0.0.1-20260910.195719-2
policy-service           0.0.1-20260910.195726-1
claims-billing-service   0.0.1-20260910.195733-1
notification-worker      0.0.1-20260910.195739-1
search-service           0.0.1-20260910.195745-1
```

## Failure ve ownership boundary

Nexus Maven package'ları; Harbor OCI image'ları saklar. Nexus publication failure, aynı başarılı Quality Gate sonrasında yalnızca Maven publication boundary'de retry edilir. İlgisiz Harbor retry tetiklememeli veya unchanged test stage'lerini tekrar çalıştırmamalıdır.

## Doğrulanmış provenance attachment'ları

Build #10, beş Java service artifact'ının her birinin yanına `build-provenance.json` classifier publish etti. Beş record'un tamamı source revision `6c07fa81df22330699c58574059b89e58777f0ed`, Jenkins Build #10 URL ve exact JAR SHA-256 içerir. Böylece timestamped Maven coordinate, mutable CI state'i artifact version içine encode etmeden source'a bağlanır. Representative hash'ler: authorization `f639dac1...`, policy `71c9e361...`, claims `33dbc1c0...`, notification `c56336a6...`, search `9b21f095...`.

## .NET eşlemesi

Nexus hosted Maven repository'leri Azure Artifacts veya private NuGet feed'e karşılık gelir. Maven `deploy`, `dotnet nuget push` karşılığıdır; repository role ve credential'ları package consumer, publisher ve administrator arasındaki aynı separation'ı sağlar.
