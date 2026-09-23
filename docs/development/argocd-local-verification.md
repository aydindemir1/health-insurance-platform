# Argo CD lokal doğrulaması

## Kapsam

Bu kayıt yalnızca 15 Eylül 2026 tarihinde doğrulanan lokal GitOps boundary'yi kapsar. Stateful dependency'ler için production availability iddiasında bulunmaz.

## Güvenlik modeli

- `health-insurance` AppProject yalnızca bu repository'yi ve `health-insurance` namespace'ini kabul eder.
- Cluster scope yalnızca `Namespace` ile sınırlıdır; namespace scope ise ConfigMap, Service, ServiceAccount, Deployment, HPA, NetworkPolicy, PodDisruptionBudget, ResourceQuota ve LimitRange için açık bir allowlist'tir.
- Secret, Role ve RoleBinding bilinçli olarak hariç tutulur. Harbor pull credential, ignore edilen lokal configuration'dan runtime'da oluşturulur.
- Automatic sync kapalıdır. Review edilmiş immutable revision tek bir explicit sync operation ile promote edilir.
- Application workload'larında ServiceAccount token automount kapalı kalır.

## Çalıştırılmış kanıt

```text
Argo CD version:             v3.5.2
Argo control-plane pods:     7/7 Ready
Application:                 health-insurance-staging
Sync:                        Synced
Operation:                   Succeeded
Git desired-state revision:  c9c1baa496df1c0126648573c5f25a75f6967a5c
Image/source revision:       6c07fa81df22330699c58574059b89e58777f0ed
Private registry credential: runtime-only harbor-registry Secret
Rendered private images:     6/6 use the same full Git SHA
```

Application `Synced` durumuna ulaştı; operation `Succeeded` oldu. Kubernetes Deployment specification'ları promote edilen altı `6c07fa8...` tag'ini gösterir. Application health `Degraded` kaldı; çünkü independently managed database, broker, IAM ve search runtime resource-isolated CI sırasında bilinçli olarak durdurulmuştu.

## Bulunan ve düzeltilen failure

Orijinal 4 GiB Minikube container, Argo CD ve platform aynı node'u paylaşırken memory'nin %99.7'sine ulaştı. Kubelet `container runtime is down` raporladı ve node `NotReady` oldu. Live container limit 8 GiB'ye yükseltildi; node data loss veya cluster recreation olmadan yeniden `Ready` oldu.

Argo'nun default bir saniyelik probe timing'i Docker Desktop I/O pressure altında false local restart'lara da neden oldu. Local installer artık cached pinned image'lar için `IfNotPresent` ve bounded, daha toleranslı server/repository probe ayarları kullanır. Bunlar local runtime uyarlamalarıdır; production health semantics'i zayıflatmaz.

GitHub fetch işlemleri local Docker Desktop network üzerinde yaklaşık 30 saniye sürerken Argo CD repository request timeout değeri 15 saniyeydi. Installer artık `reposerver.git.request.timeout=60s` ayarlar; bu yalnızca bounded local fetch window'u değiştirir. Hard refresh sonrasında desired revision `c9c1baa...` çözüldü ve explicit sync image revision `6c07fa8...` değerini başarıyla uyguladı.

Search HPA daha sonra Deployment replica count'u değiştirdi ve kısa süreli `OutOfSync` raporlandı. Application artık yalnızca Deployment'lar için `/spec/replicas` alanını ignore eder ve `RespectIgnoreDifferences` etkinleştirir; scaling HPA'ya ait olurken GitOps image, configuration, security ve resource drift'ini algılamaya devam eder.

ServiceAccount registry reference oluşturulmadan önce yaratılan pod'lar bu reference'ı geriye dönük olarak almadı. Yalnızca failed ve replace edilebilir pod'lar silindi; controller'ları bunları `harbor-registry` ile yeniden oluşturdu ve ardından tüm private image pull'ları başarılı oldu.

## Dürüst health boundary

`Synced` ve `Succeeded`, Git'ten cluster'a desired-state delivery ile private registry access'i kanıtlar. Application health; operator-owned PostgreSQL, Kafka, RabbitMQ, Redis, Elasticsearch, Keycloak, APM endpoint'leri ve runtime Secret'lar staging topology'ye sağlanana kadar `Degraded` kalabilir. Bu ayrım deployment reconciliation'ı full production readiness gibi göstermeyi engeller.
