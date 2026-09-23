# Argo CD GitOps sınırı

`AppProject`, repository ve hedef ortamı sınırlar. `Application`, staging
Kustomize overlay'ini takip eder ancak gerçek Harbor image referansları tüm
sentinel değerlerin yerini alana kadar bilinçli olarak automated sync içermez.
Jenkins build ve publication işlemlerini gerçekleştirir; Kubernetes
credential'ları almaz. Promotion işlemi Git üzerindeki altı image referansını
review süreci üzerinden değiştirir; ardından Argo CD bu commit'i reconcile eder.

Uygulamadan önce render edin:

```powershell
kubectl kustomize deploy/gitops/environments/staging
kubectl kustomize deploy/gitops/argocd
```

Argo CD'yi disposable bir cluster'a resmi release manifest'lerinden kurun;
ardından registry değerleri immutable hale gelip ilgili cluster'dan erişilebilir
olduktan sonra render edilmiş project ve application kaynaklarını uygulayın.

`install-local-argocd.ps1 -Context <disposable-context>`, sabitlenmiş resmi
Argo CD `v3.5.2` manifest'ini kurar. Context uyuşmazlığını ve production
ortamını çağrıştıran isimleri reddeder. Sentinel image tag'leri mevcut olduğu
sürece Application automated sync içermez; bu nedenle kurulum placeholder
image'ları deploy edemez.

Lokal installer yalnızca runtime ergonomisini değiştirir: Argo workload'ları,
önceden yüklenmiş bir image'ın tekrar indirilmemesi için `IfNotPresent`
kullanırken API ve repository-server probe'ları Docker Desktop I/O latency'sini
tolere eder. Argo CD ile tüm platform workload'ları aynı node'u paylaşıyorsa
Minikube profili için en az 8 GiB bellek kullanın.

İlk private-registry rollout'undan önce Git tarafından ignore edilen runtime
Secret'ı oluşturun:

```powershell
.\deploy\gitops\create-local-registry-secret.ps1 -Context portfolio-ci
```

Script `infra/cicd/.env` dosyasını okur, Docker registry credential'ını stdin
üzerinden uygular ve Secret payload'ını hiçbir zaman dosyaya yazmaz veya ekrana
basmaz. `AppProject`, `Secret`, `Role` veya `RoleBinding` kaynaklarını
bilinçli olarak yönetemez.
