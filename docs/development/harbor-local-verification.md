# Harbor lokal doğrulaması

## Amaç

Harbor, başarılı quality gate ile GitOps deployment revision arasındaki private OCI boundary'dir. Bu checkpoint mevcut image'ları yeniden kullanarak gerçek Harbor behavior'ını doğrular; hiçbir application image rebuild edilmez.

## Runtime doğrulaması — 15 Eylül 2026

Harbor 2.15.2 daha önce hazırlanmış runtime ve local image'lardan başlatıldı. İlk start iki Docker Desktop bind-mount defect'i ortaya çıkardı:

1. `/data/database` boştu ancak `root:root` sahibiydi; Harbor PostgreSQL UID/GID `999:999` ile çalışır ve `pg18` dizinini oluşturamadı.
2. Eksik `/data/secret/registry/root.crt` daha önce Docker tarafından empty directory olarak materialize edilmişti; bu nedenle certificate file olarak mount edilemedi.

Registry veya database data silinmedi. Empty mount point kaldırıldı, database directory ownership düzeltildi ve mevcut Harbor `prepare` image runtime secret/configuration'ı yeniden oluşturdu. Start script artık setup'ın repeatable olması için `prepare` öncesinde aynı bounded preflight'ı yapar.

Ardından on Harbor container'ın tamamı healthy durumuna ulaştı.

## Güvenlik ve repository evidence

| Kontrol | Doğrulanan sonuç |
|---|---|
| Project | `health-insurance`, private |
| Anonymous project API | `401 Unauthorized` |
| Automatic Trivy scanning | Disabled; required release gate değil |
| Robot | `robot$health-insurance+jenkins-publisher`, enabled |
| Credential lifetime | 7,776,000 saniye (90 gün) |
| Robot permission'ları | Yalnızca repository pull/push ve artifact read/create |
| Robot push | Mevcut altı image başarıyla push edildi |
| Robot pull | Authorization manifest expected digest ile `200` döndürdü |
| Tag policy | Her repository full SHA `6c07fa81df22330699c58574059b89e58777f0ed` kullanır; `latest` yok |

## Publish edilmiş OCI envanteri

| Repository | Manifest digest |
|---|---|
| authorization-service | `sha256:e6bfca05...` |
| policy-service | `sha256:cd3125e4...` |
| claims-billing-service | `sha256:d6c37d2c...` |
| notification-worker | `sha256:3bfb6737...` |
| search-service | `sha256:b17aa0b5...` |
| operations-portal | `sha256:9d9af944...` |

Bunlar registry manifest digest'leridir. Full Git SHA tag image'ları Jenkins source revision'a bağlarken digest addressing exact content'i korur. Her image ayrıca OCI `revision=6c07fa8...` ve GitHub repository `source` label'ını taşır. Aynı revision Kustomize'a commit edildi ve Argo CD tarafından reconcile edilerek source-to-runtime trace contract tamamlandı.

## Operasyonel boundary

Trivy official Harbor distribution ile kuruludur ancak `auto_scan=false`. Bu bilinçlidir: vacancy-aligned portfolio registry RBAC, private repository, immutable tag ve GitOps promotion gösterir; resource-heavy vulnerability database update'ini mandatory gate yapmaz. Production scanning/signing policy ve exception process'i organization seviyesinde tanımlamalıdır.

Harbor credential'ları ignore edilen local runtime state ve Jenkins credential storage içinde kalır. Robot time-bounded'dır ve bootstrap yeniden çalıştırılarak rotate edilebilir; administrator credential publication pipeline tarafından asla kullanılmaz.

## .NET eşlemesi

Harbor, private Azure Container Registry'ye karşılık gelir. Project registry namespace, robot scoped service principal, Git SHA immutable deployment tag ve manifest digest exact content identity'dir.
