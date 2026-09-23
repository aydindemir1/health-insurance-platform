# GitHub ve GitHub Actions doğrulaması

## Amaç

GitHub Actions repository-hosted verification boundary'dir. Jenkins; SonarQube, Nexus, Harbor ve Argo CD için vacancy-aligned delivery orchestrator olarak kalır; GitHub Actions bu publication sorumluluklarını duplicate etmez.

## 15 Eylül 2026 tarihinde doğrulandı

- Hardened workflow'lar commit `e9ccb414b4662e63694902b40fbad9e56f6d8573` üzerinde bir kez çalıştırıldı; üçü de başarıyla tamamlandı:
  [Backend CI run 34907516491](https://github.com/aydindemir1/health-insurance-platform/actions/runs/34907516491),
  [Frontend CI run 34907516480](https://github.com/aydindemir1/health-insurance-platform/actions/runs/34907516480) ve
  [Gateway CI run 34907516501](https://github.com/aydindemir1/health-insurance-platform/actions/runs/34907516501).
- Bu CI hardening değişikliği öncesinde local `main` ve `origin/main` her ikisi de `fb84335ebf91ed856df0adbff33b0f24502ca8bc` revision'ına çözülüyordu.
- Gateway CI run `34901588813` en güncel Compose/gateway değişikliği için başarılı oldu.
- Frontend CI run `34896585451` en güncel portal değişikliği için başarılı oldu.
- Backend CI run `34890911677`, commit `fbadd47628b1eac821bcbcf5984caa5690a8e5ab` üzerinde beş servisin tamamı için başarılı oldu.
- Daha sonraki Backend CI run `34892915944`, diğer dört service başarılı olurken yalnızca `claims-billing-service` matrix job'ında fail oldu. O commit Search Service ve documentation'ı değiştiriyordu, Claims/Billing'i değil. Public metadata failing `Verify application` step'ini gösterir; fakat GitHub log'u indirmek için authenticated repository administrator gerektirir. Bu nedenle evidence transient/flaky-run hipotezini destekler, kanıtlanmış code root cause'u değil; eski run bilinçli olarak retry edilmedi. Daha sonraki full backend matrix success `e9ccb41` üzerinde current source'u bağımsız olarak doğrular.

## Workflow kontrolleri

Üç workflow da read-only repository permission, path filter, bounded job timeout ve ref başına concurrency cancellation kullanır. Her run, full 40-character `GITHUB_SHA` değerini run summary'ye yazar. Bu, Jenkins publication flow'un daha sonra Maven artifact, OCI image label/tag ve Kustomize deployment revision içine taşıdığı source identity'yi oluşturur.

Backend matrix beş Maven service'in tamamını Java 21 üzerinde bağımsız doğrular. Frontend workflow `npm ci`, lint, unit test ve `build:budget` kullanır; böylece bundle regression local convention olarak kalmak yerine CI'ı fail eder. Gateway CI Compose model'i validate eder ve gerçek APISIX process'i çalıştırır; third-party image Kubernetes runtime tarafından daha önce doğrulanmış digest ile pinlenmiştir.

## Failure yorumlama

Green historical run yalnızca kendi exact commit'ini kanıtlar. Red matrix run'da code değiştirilmeden önce failing job ve log üzerinden classification yapılmalıdır. Downstream registry veya deployment boundary fail oldu diye publication stage'leri yeniden çalıştırılmamalıdır; immutable Git SHA ilgili boundary'nin güvenle resume etmesini sağlar.

## İddia edilmeyen production hardening

GitHub branch protection, required status check ve environment approval repository/organization ayarlarıdır; yalnızca committed YAML üzerinden iddia edilmez. Reusable GitHub Actions'ı immutable commit SHA ile pinlemek değerli ek supply-chain control'dür; ancak upstream revision'lar review edilip govern edilene kadar explicit follow-up olarak bırakılmıştır.
