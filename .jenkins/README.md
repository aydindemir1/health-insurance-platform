# Jenkins Quality Gate Temeli

Declarative pipeline; Java 21, Node.js 24, npm, Git ve Docker CLI bulunan,
`java21-node24-docker` etiketiyle tanımlanmış geçici bir Linux agent bekler.
Jenkins üzerinde aşağıdakilerin tanımlı olması gerekir:

- `health-sonarqube` adında bir SonarQube server;
- `sonar-scanner` adında bir SonarScanner kurulumu;
- `waitForQualityGate` işleminin tamamlanabilmesi için
  `<jenkins>/sonarqube-webhook/` adresinde SonarQube webhook endpoint'i;
- isteğe bağlı publication stage'leri etkinleştirilmeden önce
  `nexus-publisher` ve `harbor-publisher` adlarında username/password
  credential'ları.

Token'lar Jenkins Credentials/SonarQube yapılandırmasında tutulmalı ve hiçbir
zaman build parameter olarak geçirilmemeli veya commit edilen dosyalara
eklenmemelidir. Bu ilk aşama testleri, build işlemlerini, analizleri ve blocking
Quality Gate'i gerçekleştirir. Artifact publication ve GitOps promotion,
Milestone 12'nin daha sonraki bir aşamasında bilinçli olarak eklenmiştir.

`PUBLISH_ARTIFACTS` varsayılan olarak `false` değerindedir. `main` branch'inde,
başarılı bir Quality Gate sonrasında açıkça etkinleştirildiğinde executable Maven
artifact'ları Nexus release repository'sine ve altı OCI image Harbor'a yayınlanır.
Image'lar tam Git commit SHA değerini kullanır; pipeline hiçbir zaman `latest`
tag'ini yayınlamaz.

Kaynakları sınırlandırılmış lokal Jenkins controller ve SonarQube server'ı
application stack'ten bağımsız olarak başlatın:

```powershell
.\infra\cicd\start-quality-stack.ps1
```

Script ilk çalıştırmada yalnızca lokal kullanım için rastgele credential'lar
içeren ve Git tarafından ignore edilen bir `.env` dosyası oluşturur. CPU ve
belleği serbest bırakırken volume'ları korumak için `stop-quality-stack.ps1`
kullanın. Üç servisli profil en fazla 4.25 CPU ve yaklaşık 4.5 GiB RAM kullanacak
şekilde sınırlandırılmıştır; bu bir production topology değildir.

İlk healthy startup sonrasında `bootstrap-quality-stack.ps1` çalıştırın. Bu
script varsayılan SonarQube administrator password'ünü değiştirir, lokal bir
analysis token oluşturur, bunu yalnızca Git tarafından ignore edilen `.env`
dosyasında saklar, JCasC üzerinden Jenkins string credential'ını provision eder
ve Jenkins webhook'unu oluşturur. Scanner sürümü, pipeline kodunda sürümsüz
indirme yapmak yerine açıkça tanımlanmış bir Jenkins tool kurulumu
(`8.1.0.6389`) olarak kullanılır.

Commit edilmiş SCM tanımından lokal pipeline job'ını oluşturmak veya güncellemek
ve bir build kuyruğa almak için `run-local-pipeline.ps1` çalıştırın. Publication
varsayılan olarak kapalı kalır; bu komut Nexus veya Harbor'a push yapamaz.
