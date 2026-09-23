# ADR-014: Lokal CI/CD ve software supply chain

## Durum

Kabul edildi

## Bağlam

Portföy; developer laptop'ın production platform olduğu izlenimini vermeden,
ilandaki Git, Jenkins, SonarQube, Nexus, Harbor, Kubernetes ve Argo CD workflow'unu
göstermelidir.

## Karar

Jenkins orchestration boundary'dir. Git'i checkout eder, backend ve frontend
quality stage'lerini çalıştırır, SonarQube Quality Gate'i bekler, Maven snapshot'larını
Nexus'a publish eder, CycloneDX SBOM üretir ve OCI image'larını full Git SHA
tag'leriyle private Harbor project'e publish eder. Review edilmiş GitOps commit,
Kustomize image reference'larını günceller; Argo CD `main` branch'ini okur ve
Kubernetes sync'i gerçekleştirir. Credential'lar ignore edilen local
environment/runtime store'larda kalır ve Jenkins credentials üzerinden inject edilir.

Nexus Community Edition EULA kabulü explicit administrator action'dır, hiçbir
zaman automatic default değildir. Trivy optional'dır ve bu educational environment
için release gate değildir. SonarQube mandatory static quality gate olarak kalır;
Harbor private registry ownership ve immutable delivery'yi gösterir.

## Sonuçlar

- Failed publication bağımsız olarak resume edilebilir; yalnızca Nexus veya Harbor
  geçici erişilemez olduğu için successful test'ler tekrar çalıştırılmaz.
- Git, Harbor ve Argo CD aynı Git SHA identity'yi paylaştığı için image promotion
  auditable'dır.
- Local HTTP endpoint'leri ve Minikube-specific registry DNS yalnızca development içindir.
- Production hâlâ trusted TLS, external secret management, signed artifact,
  protected environment, HA runner ve organizational approval gerektirir.

## Alternatifler

GitHub Actions primary orchestrator olabilirdi ancak advertised stack'i çalıştırmak
için Jenkins seçildi. Kustomize environment overlay sağladığı için Helm gerekli
değildi. Terraform ve Ansible eklenmedi: bu milestone cloud infrastructure veya
fleet configuration provision etmez.
