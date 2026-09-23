# Jenkins ve SonarQube lokal doğrulaması

## Amaç

Bu checkpoint mevcut lokal quality stack'i image rebuild etmeden veya yeni pahalı pipeline başlatmadan doğrular. Jenkins delivery flow'u orchestrate eder; SonarQube onun blocking static-analysis gate'idir. Healthy UI tek başına yeterli evidence değildir; bu nedenle check'ler authenticated API, job stage, source revision, webhook configuration ve container-to-container connectivity'yi kapsar.

## Runtime doğrulaması — 15 Eylül 2026

Mevcut local image ve persistent volume'lar şu komutla başlatıldı:

```powershell
docker compose --env-file infra/cicd/.env `
  -f infra/cicd/compose.quality.yaml up --detach --no-build
```

Hiçbir image build veya pull edilmedi. Runtime secret'lar ignore edilen `infra/cicd/.env` içinde kaldı; yalnızca variable name'ler doğrulandı ve hiçbir value yazdırılmadı.

| Kontrol | Doğrulanan sonuç |
|---|---|
| Jenkins container | Healthy; authenticated API `127.0.0.1:8086` üzerinde erişilebilir |
| SonarQube container | Healthy; `/api/system/status`, `127.0.0.1:9000` üzerinde `UP` döndürdü |
| Sonar PostgreSQL | Healthy; persisted database yeniden kullanıldı |
| Internal connectivity | Jenkins `sonarqube` adını resolve etti ve Compose network üzerinden `UP` aldı |
| Jenkins job | `health-insurance-platform`, GitHub `main`, repository `Jenkinsfile`, lightweight checkout |
| Sonar authentication | Runtime administrator credential kabul edildi |
| Quality Gate | `OK`; `new_violations=0`, error threshold `0` |
| Webhook | `jenkins-local`, `http://jenkins:8080/sonarqube-webhook/` hedefini kullanıyor |

## Final Build #10 evidence

Jenkins Build #10 immutable source revision `6c07fa81df22330699c58574059b89e58777f0ed` için `SUCCESS` ile tamamlandı. Delivery proof'ta kullanılan tek final pipeline execution budur:

```text
Checkout -> five backend services -> frontend -> Sonar analysis -> Quality Gate
         -> Nexus publication -> CycloneDX SBOMs -> Harbor publication
              SUCCESS              SUCCESS             SUCCESS
```

Beş Java service'in tamamı JaCoCo report'larıyla geçti; frontend lint, 27 test, coverage ve production build başarılı oldu. SonarQube ardından Quality Gate `OK`, `new_violations=0`, overall coverage `80.3%`, line coverage `86.3%` ve branch coverage `61.7%` raporladı. Nexus, SBOM ve altı Harbor publication tamamlandı.

Build #8 fail-closed gate'i kanıtladı (`new_coverage=0`, dokuz new violation). Build #9 Elasticsearch Testcontainers başlatılırken local resource contention ortaya çıkardı. Minikube ve application Compose stack data loss olmadan durduruldu; Build #10 ardından geçti. Build #10 sonrasında başka Jenkins build tekrarlanmadı.

## Güvenli inceleme

```powershell
docker compose --env-file infra/cicd/.env `
  -f infra/cicd/compose.quality.yaml ps

./scripts/validate-ci-pipeline.ps1
```

Stage graph için `http://localhost:8086/job/health-insurance-platform/10/pipeline-overview/`, analysis için `http://localhost:9000/dashboard?id=health-insurance-platform` adresini açın. Credential'lar ignore edilen runtime environment'tan gelir; screenshot, command, Git history veya documentation içine asla kopyalanmamalıdır.

## Architecture ve .NET eşlemesi

- Jenkins Pipeline multi-stage Azure DevOps/TFS veya GitLab CI pipeline'a karşılık gelir; `Jenkinsfile` versioned pipeline definition'dır.
- SonarQube Quality Gate, NuGet/Maven veya container publication stage öncesindeki blocking code-quality policy'ye karşılık gelir.
- Sonar webhook asynchronous completion notification'dır. Jenkins scanner exit status'tan tahmin yürütmek yerine authoritative gate result'ı bekler.
- Nexus Maven artifact boundary, Harbor OCI image boundary'dir. Independent retry semantics bilinçli tasarımdır.

## Dürüst portfolio boundary

Bu local evidence configuration, authentication, network integration, analysis gating ve stage-level failure isolation'ı kanıtlar. Jenkins veya SonarQube high availability, enterprise backup, trusted TLS, external identity veya production runner isolation iddiasında bulunmaz.
