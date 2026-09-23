# Lokal Sorun Giderme

Bu rehber Milestone 8'e kadar reproducible local-development failure'larını kapsar. Password, access token, message payload veya gerçek sağlık verisini command, issue, screenshot veya log içine asla yapıştırmayın.

## Disposable Minikube profile'ın API server'ı yok

`portfolio-ci` ilk bootstrap sırasında kesintiye uğradıysa kubelet missing `bootstrap-kubelet.conf` raporlayabilir veya kubeadm empty certificate SAN'i reddedebilir. Generated profile JSON'u edit etmeyin. Cluster explicit disposable olduğundan yalnızca exact profile'ı silin ve Kubernetes deployment guide'daki command ile yeniden oluşturun:

```powershell
minikube delete -p portfolio-ci
```

Bu cluster state'i siler; repository file, Compose volume veya local application image'larını silmez. Sonraki `minikube start` işleminin kesintisiz tamamlanmasına izin verin.

## Compose port zaten kullanılıyor

Belirti:

```text
Bind for 0.0.0.0:5435 failed: port is already allocated
```

Bir şeyi durdurmadan önce owner'ı bulun:

```powershell
docker ps -a --filter publish=5435 `
  --format "table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Bu bilinçli olarak retained eski demo project ise yalnızca o exact project veya container'ları durdurun. Local data disposable olduğu doğrulanmadıkça volume silmeyin. Current Compose port'u değiştirmek stale runtime'ı gizler ve documentation/script consistency'sini bozabilir.

## `.env` değişikliğinden sonra PostgreSQL password authentication failure veriyor

PostgreSQL image `POSTGRES_USER` ve `POSTGRES_PASSWORD` değerlerini yalnızca empty data directory initialize ederken okur. `.env` edit etmek existing named volume içindeki role'leri update etmez.

Disposable synthetic data için data loss güvenli olduğu doğrulandıktan sonra reset edin:

```powershell
docker compose down
docker volume ls --filter label=com.docker.compose.project=health-insurance-platform
# Yalnızca doğruladığınız exact stale database volume'larını silin.
docker compose up -d
```

Değerli local data varsa volume'u silmeyin. Known original database administrator ile bağlanın, gerekli role'ü rotate/create edin, ownership/grant transfer edin ve yeni secret'ı yalnızca `.env` içinde tutun.

## RabbitMQ veya Notification Worker ready olmuyor

State ve bounded log'ları inceleyin:

```powershell
docker compose ps -a
docker compose logs --no-color --tail 120 rabbitmq notification-worker
```

Beklenen evidence: healthy RabbitMQ container, worker'ın `rabbitmq:5672` bağlantısı, başarılı Liquibase migration ve started listener. Ignore edilen `.env` içinde `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `NOTIFICATION_DB_USERNAME`, `NOTIFICATION_DB_PASSWORD` bulunduğunu doğrulayın; value'ları yazdırmayın.

## Notification DLQ'ya düştü

Delivery queue permanent failure veya toplam üç transient attempt sonrasında requeue olmadan reject eder. Header ve safe technical identifier'ları inceleyin, sonra nedeni classify edin:

- unsupported `taskVersion`, malformed JSON veya intent conflict: replay öncesi compatible consumer/producer contract'ı fix/deploy edin;
- exhausted transient dependency failure: dependency'yi restore edin ve replay öncesi downstream idempotency contract'ını doğrulayın;
- gerçek contact veya health information içeren message'ı edit edip replay etmeyin.

Milestone 6 bilinçli olarak automatic DLQ replay içermez. Manual replay operational runbook, authorization, audit evidence ve idempotency review gerektirir.

## Testcontainers RabbitMQ veya PostgreSQL başlatamıyor

Docker Desktop'ın çalıştığını ve daemon'a erişilebildiğini doğrulayın:

```powershell
docker info
docker ps
```

Maven'i service directory'den çalıştırın. Notification Worker broker testi hem PostgreSQL 17 hem RabbitMQ 4.1 container başlatır; mocked broker testi değildir.

## Docker build cold cache üzerinde yavaş

Her service şu anda Maven'i çağırmadan önce source'u kopyalar; bu nedenle cold veya invalidated Docker cache dependency'leri yeniden indirebilir. Bu correctness failure değil performance limitation'dır. Future build-only improvement, runtime behavior değiştirmeden BuildKit Maven cache veya dependency-first layer ekleyebilir.

## Redis unavailable

Policy Service Redis'i bilinçli olarak optimization olarak görür. Yalnızca bounded log tail inceleyin ve cache warning sonrasında successful database evaluation arayın:

```powershell
docker compose ps redis policy-service
docker compose logs --no-color --tail 100 policy-service
```

Authorization'ı unknown coverage kabul edecek şekilde değiştirmeyin. Redis failure Policy PostgreSQL'e fallback eder; PostgreSQL/Policy failure yine `503` döndürür ve authorization oluşturmaz. Gerçek identifier ile raw cache value loglamayın veya inspect etmeyin.

## Elasticsearch, Search Service veya Kibana ready değil

Elasticsearch ve Kibana cold Docker Desktop start'ta memory-intensive olabilir. Exact service'leri inceleyin ve Elasticsearch health gate'i bekleyin:

```powershell
docker compose ps elasticsearch search-service kibana apm-server
docker compose logs --no-color --tail 120 elasticsearch search-service kibana apm-server
curl.exe -sS http://localhost:9200/_cluster/health
```

Core claim command'ları başarılı fakat search stale ise unpublished `claim_search_outbox` row'ları ve Search consumer log'larını inceleyin. Source-service database veya Elasticsearch'e direct write ile tamir etmeyin. Dependency'yi restore edin, relay/consumer catch-up'a izin verin veya bounded [search rebuild runbook](../operations/search-and-messaging-recovery.md) izleyin.

Full Search Testcontainers suite birkaç büyük container concurrent başlarken timeout olabilir. Code defect olarak classify etmeden önce tek başına çalıştırın:

```powershell
Set-Location services/search-service
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

## Search rebuild activate olmuyor

- `Candidate count ... does not match expected count`: bir veya daha fazla owner page/write exact distinct set üretmedi. Service count öncesi candidate'ı refresh eder. Current alias'a dokunmayın ve failed run'ı inspect edin; activation zorlamak için expected count'u düşürmeyin.
- `Alias changed concurrently`: başka operation stable alias'ı taşıdı. Durun, `_cat/aliases` ve `_cat/indices` inceleyin; guessed predecessor ile retry etmeyin.
- Search Service restart sonrası `409` rollback beklenir; local run registry memory'dedir. Physical index'ler intact kalır; alias'ı körlemesine edit etmek yerine yeni reviewed recovery yapın.
- Pre-M10 document `sourceRevision` içermeyebilir. Reader bunu baseline revision 1 kabul eder; search available kalır ve sonraki owner rebuild bunu replace eder.

Diagnosis sırasında yalnızca safe metadata kullanın:

```powershell
curl.exe -sS "http://localhost:9200/_cat/aliases/healthcare-operations?format=json&h=alias,index,is_write_index"
curl.exe -sS "http://localhost:9200/_cat/indices/healthcare-operations-v*?format=json&h=index,docs.count,status"
```

## Recovery inspection Kafka command'larını bulamıyor

Runtime `apache/kafka-native` image bilinçli olarak küçüktür ve console administration binary'lerini içermez. Milestone 10 tools-only `kafka-cli` Compose profile tanımlar; broker container içine package yüklemek yerine committed script'leri kullanın:

```powershell
.\scripts\inspect-recovery-status.ps1 | ConvertTo-Json -Depth 6
.\scripts\recover-kafka-dlt.ps1 -Action Inspect `
  -DltTopic health.authorization.pre-authorization.v1.DLT -MaxMessages 1
```

Empty DLT/DLQ healthy result'tır; script failure değildir. Replayed data hemen tekrar dead-letter olursa durun: yanlış classify edilmiştir veya dependency hâlâ unhealthy'dir. Command'ı loop etmeyin veya Kafka offset reset etmeyin.

## Correlation veya APM data eksik

Safe bounded header gönderin, echo edildiğini doğrulayın, ardından ECS JSON içinde aynı `correlationId` field'ını bulun:

```powershell
curl.exe -i -H "X-Correlation-ID: local-diagnostic-001" http://localhost:9080/api/v1/pre-authorizations
docker compose logs --no-color --tail 100 authorization-service
```

Java agent `JAVA_TOOL_OPTIONS` ile attach edilir; token veya secret yazdırmadan exact container içinde environment ve APM Server connectivity'yi doğrulayın. APM unavailability business processing'i durdurmamalıdır. Correlation ID evidence'ı bağlar ancak distributed transaction semantics sağlamaz.

## APISIX unhealthy veya 502/504 döndürüyor

```powershell
docker compose ps apisix authorization-service policy-service claims-billing-service search-service keycloak
docker compose logs --no-color --tail 150 apisix
docker compose config --quiet
```

`infra/apisix` altındaki iki read-only file'ın mount edildiğini ve route file'ın `#END` ile bittiğini doğrulayın. Missing token için `401`, route ve gateway-native RFC 9457 adapter'ın yüklendiğini kanıtlar; upstream ready olduğunu kanıtlamaz. `502`, seçilen internal service'e ulaşılamadığını; `504` bounded upstream timeout'un dolduğunu gösterir.

Local stack Keycloak discovery için bilinçli olarak HTTP kullanır; bu nedenle APISIX security warning loglar. Production'da check'leri disable ederek susturmayın; public ve backchannel identity-provider endpoint'leri için trusted TLS deploy edin.

APISIX OpenID Connect plugin missing bearer token'ı expected `401` döndürmeden önce şu anda `error` olarak kaydeder. Bu satırı outage saymadan önce status ve request ID'yi correlate edin; alerting isolated client-side authentication failure için page etmemelidir.

Rate limiter APISIX process'e lokaldir. Verification run one-minute window'u bilinçli olarak tüketir. Immediate manual demo öncesi APISIX'i restart edin veya reset header süresini bekleyin. Multiple production replica shared counter policy gerektirir.

## Jenkins, Nexus, Harbor veya Argo CD publication fail oluyor

Verification ve SonarQube gate aynı commit için zaten başarılıysa complete pipeline'ı restart etmeyin. Failed boundary'den devam edin:

- Nexus `403` + EULA message: administrator açıkça `infra/cicd/accept-nexus-eula.ps1 -AcceptEula` çalıştırır, ardından Maven publication retry edilir.
- Harbor HTTP registry için HTTPS raporluyorsa: Docker daemon `localhost:8088`, Jenkins API'ye `host.docker.internal:8088` üzerinden ulaşır.
- Host-side Harbor robot login fail: `.env`, Compose için `$` karakterini `$$` olarak escape eder; yalnızca current process'te unescape edin, secret'ı yazdırmayın.
- Argo CD `Unknown`: `argocd-repo-server` beklenir, ardından hard refresh istenir.
- Argo CD `Synced/Progressing`: Git delivery başarılıdır; workload external Secret veya stateful dependency bekliyordur.

Trivy mandatory portfolio release gate değildir. Optional local scan almak için platformu rebuild etmeyin.

```powershell
kubectl --context portfolio-ci get pods -n argocd
kubectl --context portfolio-ci get application health-insurance-staging -n argocd
docker compose --env-file infra/cicd/.env -f infra/cicd/compose.artifacts.yaml ps
```

## Güvenli sıfırlama

`docker compose stop` container ve volume'ları korur. `docker compose down` container ve network'ü kaldırır ama named volume'ları korur. `docker compose down -v` tüm project volume'larını ve dolayısıyla tüm local demo database/broker state'ini siler; yalnızca data'nın disposable olduğu explicit olarak doğrulandıktan sonra kullanın.

## Kubernetes manifest veya rollout diagnostics

Cluster'a dokunmadan önce yalnızca deployment package'ı validate edin:

```powershell
.\scripts\validate-kubernetes.ps1
kubectl kustomize deploy/kubernetes/overlays/local
```

Live local rollout için önce `kubectl config current-context` değerinin disposable cluster'ı gösterdiğini doğrulayın, ardından `deploy/kubernetes/scripts/apply-local.ps1` kullanın. Missing Secret pod creation'ı tasarım gereği block eder; YAML edit etmek yerine ignore edilen `.env` doldurun. Readiness failure pod'a traffic gitmesini engeller, liveness failure onu restart eder. Process `/tmp` dışına write etmeye çalışırsa read-only root filesystem'i disable etmek yerine explicit writable mount düzeltin. Network timeout için egress'i genişletmeden önce default-deny NetworkPolicy ve declared external port'ları kontrol edin.
