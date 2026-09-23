# Ekran Görüntüsü Kataloğu

Milestone 12 lokal software supply-chain kanıtları ekler. Jenkins görseli pipeline stage sonucunu; Harbor görseli ise private project/repository sınırını kaydeder. Credential'lar yalnızca capture sırasında inject edilir ve hiçbir zaman render edilmez.

Bu dizin, çalışan Operations Portal'dan sentetik demo identifier'larıyla alınmış milestone checkpoint görsellerini içerir. Access token, credential, gerçek hasta bilgisi veya gerçek provider verisi asla içermemelidir.

Portal; pre-authorization operasyonlarını, cross-context search'ü ve ayrıcalıklı service-owned audit görünümünü uygular. Policy ve Claims/Billing command workflow'ları, operational screen'leri sonraki milestone'da uygulanana kadar API script'i üzerinden gösterilir.

Mevcut portfolio evidence kataloğu:

Operations Portal yolculuğu `01`–`05` ile temsil edilir; `07` secured cross-context search'ü, `10` ise yalnızca administrator'a açık audit erişimini kanıtlar. Bu görünümler frontend local verification guide'da kaydedilen gerçek Keycloak-backed Playwright workflow ile eşleşir.

Timeout, unauthorized, request-error ve render-failure davranışları ek screenshot'lar olarak dondurulmak yerine automated test'lerde doğrulanır. Bu yaklaşım kataloğu business evidence üzerinde tutar ve üretilmiş error page'lerinin canlı operational incident gibi sunulmasını önler.

Doğrulanmış backend end-to-end chain `07` ve `19`–`25` ile temsil edilir: Search, iki service-owned PostgreSQL store, Kafka, RabbitMQ ve Notification Worker. Aynı run için yoğun bir terminal kolajı eklemek yerine bu focused, token-free view'ları yeniden kullanmak daha güçlü ve okunabilir evidence sağlar.

- `01-dashboard.png` — role-aware landing page ve operational summary.
- `02-pre-authorization-work-queue.png` — filter, sort ve pagination UI.
- `03-submit-pre-authorization.png` — validated hospital submission form.
- `04-pre-authorization-detail.png` — request detail ve status bilgisi.
- `05-specialist-decision.png` — specialist approval/rejection control'leri.
- `06-rabbitmq-notification-queues.png` — canlı durable delivery queue, DLX/DLK argument'ları, DLQ, consumer processing state ve boşalmış message count'ları.
- `07-healthcare-search.png` — sentetik policy ve financial record'ları kullanan secured Elasticsearch-backed cross-context operations query.
- `08-kibana-apm-services.png` — externally attached Java agent'lar tarafından doldurulan canlı Kibana APM services inventory.
- `09-apisix-gateway-problem-details.png` — canlı Kubernetes APISIX Service üzerinden yenilenmiştir: correlation ID içeren unauthenticated RFC 9457 rejection; executable demo ayrıca audience, routing, CORS, payload ve rate-limit policy'lerini doğrular.
- `10-audit-trail.png` — yalnızca `SYSTEM_ADMIN` erişimine açık service selector, bounded filter'lar, paginated minimized state-change evidence, actor context ve correlation ID.
- `11-search-rebuild-recovery.png` — source-owned versioned rebuild sonrasında canlı stable alias target, current document count ve retained predecessor.
- `12-jenkins-supply-chain.png` — başarılı Jenkins Build #10 end-to-end evidence; final red result, başarılı quality stage'leri tekrar çalıştırılmadan bağımsız olarak resume edilen dokümante edilmiş local Harbor HTTP/HTTPS mismatch durumudur.
- `13-harbor-artifacts.png` — Harbor private project ve OCI repository'leri.
- `14-argocd-gitops-sync.png` — Argo CD staging Application ve GitOps state.
- `15-nexus-maven-artifacts.png` — Nexus Maven snapshot repository evidence.
- `16-docker-cicd-runtime.png` — canlı local CI/CD container inventory.
- `17-kubernetes-argocd-runtime.png` — canlı Kubernetes ve Argo CD resource state.
- `18-policy-service-runtime.png` — health, Keycloak OIDC metadata, signed-token positive/negative coverage decision'ları, RFC 9457 validation, PostgreSQL policy/audit row'ları, applied migration'lar, policy/coverage invariant constraint'leri, audit constraint/trigger ve bounded TTL'li hashed Redis key'leri gösteren izole canlı Policy Service evidence.
- `19-authorization-service-runtime.png` — health, RFC 9457 authentication failure, PostgreSQL state, Liquibase/constraint count'ları, minimized audit action'ları, acknowledged Kafka ve RabbitMQ outbox'ları ve durable RabbitMQ queue state gösteren izole canlı Authorization Service evidence.
- `20-authorization-postgresql-runtime.png` — Authorization-owned PostgreSQL aggregate, Liquibase history, lifecycle constraint'leri ve minimized audit row'ları.
- `21-authorization-kafka-runtime.png` — healthy Kafka broker, persisted topic/DLT partition'ları ve producer-acknowledged integration-event outbox row.
- `22-authorization-rabbitmq-runtime.png` — healthy RabbitMQ broker, durable queue/DLQ topology, binding'ler ve publisher-acknowledged notification outbox.
- `23-claims-billing-postgresql-runtime.png` — settled Claim/Invoice state, optimistic version'lar, Liquibase history, minimized audit action'ları ve owner database içindeki search projection outbox intent.
- `24-claims-billing-kafka-consumer-runtime.png` — Authorization source topic ve DLT partition'ları, Claims consumer inbox marker ve approved pre-authorization başına tek Claim idempotency evidence.
- `25-notification-worker-runtime.png` — canlı RabbitMQ delivery/DLQ state, durable binding'ler, Notification-owned PostgreSQL delivery row'ları, Liquibase history, lifecycle constraint'leri ve operational index'ler.
- `26-operations-portal-mobile-accessibility.png` — canlı axe, overflow ve keyboard-focus doğrulaması sonrası authenticated 390-pixel provider work queue.
- `27-operations-portal-apisix-mobile-smoke.png` — APISIX üzerinden doldurulan authenticated mobile work queue; tek bir canlı checkpoint'te Keycloak login, gateway routing, backend pagination ve accessible responsive UI'ı kanıtlar.

## Önizleme

![Dashboard](01-dashboard.png)

![Pre-authorization work queue](02-pre-authorization-work-queue.png)

![Submission form](03-submit-pre-authorization.png)

![Pre-authorization detail](04-pre-authorization-detail.png)

![Specialist decision](05-specialist-decision.png)

![RabbitMQ notification queues](06-rabbitmq-notification-queues.png)

![Healthcare operations search](07-healthcare-search.png)

![Kibana APM services](08-kibana-apm-services.png)

![APISIX Problem Details](09-apisix-gateway-problem-details.png)

![Service-owned audit trail](10-audit-trail.png)

![Versioned search rebuild and retained predecessor](11-search-rebuild-recovery.png)

![Jenkins supply chain](12-jenkins-supply-chain.png)

![Harbor artifacts](13-harbor-artifacts.png)

![Argo CD GitOps sync](14-argocd-gitops-sync.png)

![Nexus Maven artifacts](15-nexus-maven-artifacts.png)

![Docker CI/CD runtime](16-docker-cicd-runtime.png)

![Kubernetes and Argo CD runtime](17-kubernetes-argocd-runtime.png)

![Policy Service runtime](18-policy-service-runtime.png)

![Authorization Service runtime](19-authorization-service-runtime.png)

![Authorization PostgreSQL runtime](20-authorization-postgresql-runtime.png)

![Authorization Kafka runtime](21-authorization-kafka-runtime.png)

![Authorization RabbitMQ runtime](22-authorization-rabbitmq-runtime.png)

![Claims and Billing PostgreSQL runtime](23-claims-billing-postgresql-runtime.png)

![Claims and Billing Kafka consumer runtime](24-claims-billing-kafka-consumer-runtime.png)

![Notification Worker runtime](25-notification-worker-runtime.png)

![Operations Portal mobile accessibility](26-operations-portal-mobile-accessibility.png)

![Operations Portal through APISIX](27-operations-portal-apisix-mobile-smoke.png)

Bunları yeniden capture etmek için local stack ve portal'ı başlatın, [demo senaryosunda](../demo/demo-scenario.md) açıklandığı şekilde sentetik demo data seed edin, yalnızca runtime'da bulunan local user ile sign in olun ve yalnızca görünümü değişmiş image'ları değiştirin:

```powershell
$env:DEMO_USER_PASSWORD = "<temporary-local-demo-password>"
$env:DEMO_POLICY_NUMBER = "<policy-number-reported-by-the-seed-script>"
Set-Location apps/operations-portal
npm run screenshots
```

Gateway verification, per-IP quota'yı `429` alana kadar bilinçli olarak tüketir. Bu nedenle capture öncesinde one-minute quota window reset'ini bekleyin veya capture run'ı `-SkipGatewayVerification` ile hazırlayıp gateway verification'ı ayrı çalıştırın. Aksi halde ilk portal collection request doğru şekilde `429` alabilir ve capture timeout olabilir.

Capture script; real Keycloak login'leri ve real API-backed page'leri, audit görünümü için `system-admin-demo` dahil headless Chrome içinde çalıştırır. Example form ve pending decision alanlarını doldurur ancak submit etmez; dolayısıyla screenshot'ları yeniden capture etmek business data'yı mutate etmez.

Milestone 7 altıncı portal view ve bir APM runtime view ekler. Bunları yalnızca sentetik demo Elasticsearch document'ları ve Java-agent traffic ürettikten sonra capture edin; token, credential veya raw event payload expose etmeyin.

Yalnızca broker evidence'ı yeniden capture etmek için read-only/monitoring permission'larına sahip temporary local RabbitMQ account kullanın, value'ları process environment içinde tutun ve capture sonrasında kaldırın:

```powershell
$env:RABBITMQ_SCREENSHOT_USERNAME = "<temporary-local-monitoring-user>"
$env:RABBITMQ_SCREENSHOT_PASSWORD = "<temporary-local-password>"
Set-Location apps/operations-portal
npm run screenshots:rabbitmq
```

Script capture öncesi iki exact queue name'in de oluşmasını bekler. Message okumaz, publish etmez, acknowledge etmez veya silmez ve credential'ları image veya repository içine yazmaz.

Traffic ürettikten sonra credential gerektirmeyen local Kibana APM inventory'yi capture edin:

```powershell
Set-Location apps/operations-portal
npm run screenshots:kibana
```

Gateway-native error contract'ını credential olmadan capture edin:

```powershell
Set-Location apps/operations-portal
npm run screenshots:gateway
```

Token-free search recovery evidence'ı yalnızca successful rebuild sonrasında capture edin:

```powershell
Set-Location apps/operations-portal
npm run screenshots:recovery
```

Capture yalnızca Elasticsearch alias, index name/status, document count ve storage-size metadata okur. PNG yazılmadan önce tam olarak bir writable stable alias ve retained `healthcare-operations-v1` predecessor doğrulanır; document payload query edilmez veya render edilmez.

Local Compose stack developer convenience için Elastic security'yi kapatır ve Elastic portlarını loopback'e bind eder. Bu ayar production security modeli değildir.

Yalnızca Policy evidence'ı yeniden capture etmek için Policy Service'i PostgreSQL, Redis ve Keycloak dependency'leriyle başlatın; ardından short-lived specialist token ve sentetik policy identity'yi yalnızca mevcut process üzerinden sağlayın:

```powershell
$env:POLICY_SCREENSHOT_TOKEN = "<short-lived-token>"
$env:POLICY_SCREENSHOT_POLICY_NUMBER = "<synthetic-policy-number>"
$env:POLICY_SCREENSHOT_MEMBER_ID = "<synthetic-member-uuid>"
Set-Location apps/operations-portal
npm run screenshots:policy
```

Script gerçek coverage cache'i warm eder, yalnızca eşleşen sentetik PostgreSQL policy/audit evidence'ını query eder ve yalnızca hashed Redis key'leri render eder. Token, database password veya Keycloak administrator credential'ı hiçbir zaman render veya persist edilmez.

Yalnızca Authorization evidence'ı yeniden capture etmek için Authorization Service ve PostgreSQL, Kafka, RabbitMQ, Keycloak ve Policy dependency'lerini çalışır durumda tutun. Yalnızca sentetik request UUID'yi geçin; access token gerekmez veya persist edilmez:

```powershell
$env:AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID = "<synthetic-pre-authorization-uuid>"
Set-Location apps/operations-portal
npm run screenshots:authorization
npm run screenshots:authorization-infrastructure
```

Image yalnızca operational metadata ve minimized audit action'ları render eder. Business payload, member/diagnosis identifier, credential, token veya broker message body render etmez.

Sentetik claim ve invoice lifecycle tamamlandıktan sonra Claims/Billing evidence'ı yeniden capture edin:

```powershell
$env:CLAIMS_SCREENSHOT_CLAIM_ID = "<synthetic-claim-uuid>"
Set-Location apps/operations-portal
npm run screenshots:claims
```

Script yalnızca state ve idempotency metadata okur. Financial amount, payment reference, policy/member data, credential, token ve Kafka payload bilinçli olarak hariç tutulur.

Sentetik task deliver edildikten ve unsupported contract version DLQ'ya ulaştıktan sonra Notification Worker evidence'ı capture edin:

```powershell
Set-Location apps/operations-portal
npm run screenshots:notification
```

Image yalnızca opaque sentetik identifier, delivery lifecycle, migration/constraint/index metadata, binding ve queue count içerir. Message body, credential, token veya contact address asla okunmaz veya render edilmez.
