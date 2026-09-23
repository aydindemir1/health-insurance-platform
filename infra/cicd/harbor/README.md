# Harbor sözleşmesi

Jenkins yalnızca Quality Gate başarıyla tamamlandıktan sonra, yalnızca `main` branch'inde ve yalnızca `PUBLISH_ARTIFACTS=true` olduğunda yayın yapar. Hedefi `HARBOR_REGISTRY` ve `HARBOR_PROJECT` ile yapılandırın; robot-account credential'larını Jenkins içinde `harbor-publisher` ID'si altında saklayın. Bu robot account'a yalnızca tek bir project üzerinde push/pull yetkisi verin.

Lokal Harbor kanıtı için resmi Harbor distribution'ını veya Helm chart'ını ayrı bir oturumda kurun ve private `health-insurance` project'ini oluşturun. Trivy kullanılabilir durumda kalır ancak bu sınırlı portfolio workflow'unda automatic scanning kapalıdır; release gate değildir. 16 GiB RAM'e sahip bir workstation üzerinde Harbor'ı application, SonarQube ve Nexus ile aynı anda çalıştırmayın.

Pipeline bilinçli olarak `latest` tag'i içermez: her image tam Git commit SHA ile adreslenir. Lightweight Docker Registry, Harbor evidence olarak tanımlanmaz; çünkü Harbor RBAC, retention veya scanning davranışını kanıtlayamaz.

Windows ve Docker Desktop üzerinde `start-local-harbor.ps1`, Harbor'ın pinlenmiş resmi `prepare:v2.15.2` image'ını doğrudan çalıştırır, Trivy'yi etkinleştirir ve oluşturulan Compose model'ini `http://host.docker.internal:8088` üzerinde başlatır. Runtime configuration ve data Git dışında tutulur; generated credential'lar ignore edilen CI `.env` dosyasında kalır.

Harbor healthy duruma geldikten sonra private project'i oluşturun ve 90 günlük, least-privilege robot credential'ını Jenkins'e inject edin:

```powershell
.\infra\cicd\harbor\bootstrap-local-harbor.ps1
```

Pipeline, deploy edilebilir altı component'in tamamı için CycloneDX SBOM archive eder. Trivy ayrı bir diagnostic olarak açıkça çalıştırılabilir; ancak availability'si bu eğitim repository'sinde mandatory gate olarak sunulmaz.
