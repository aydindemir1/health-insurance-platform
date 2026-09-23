# Gösterim Senaryosu — Milestone 0–12

Bu senaryo yalnızca sentetik identifier ve clinical code kullanır. Uygulanmış happy path'i kanıtlar ve UI/API gösterimi için kayıtları farklı state'lerde bırakır. Gerçek hasta bilgisi gerektirmez ve içermez.

Milestone 6 complete notification command path ekler. Her approved/rejected authorization, decision ile aynı transaction içinde minimal bir task oluşturur. Confirm-aware relay bunu RabbitMQ'ya publish eder; worker bounded retry ile consume eder, idempotent delivery row persist eder, safe local sender'ı invoke eder, commit eder ve ancak sonrasında acknowledge eder. Permanent veya exhausted work DLQ'ya gider.

Milestone 7 kısa ömürlü Redis coverage cache, eventually consistent Elasticsearch projection'ları, provider-scoped search UI, ECS JSON log'ları, correlation propagation ve Elastic APM/Kibana runtime evidence ekler. PostgreSQL hâlâ authoritative'dir ve search hiçbir command transaction'a katılmaz.

Milestone 8 browser-facing tüm business API'lerin önüne APISIX koyar. Aynı demo artık Policy, Authorization, Claims/Billing ve Search için `9080` portunu kullanır; ardından gateway authentication, routing, correlation, CORS, payload limiting ve rate limiting otomatik doğrulanır. Spring Security gateway arkasında aktif kalır.

Milestone 9 Authorization, Policy ve Claims/Billing owner transaction'larına minimized, append-only evidence ekler. Her servis kendi bounded `SYSTEM_ADMIN` query'sini expose eder; portal shared audit database oluşturmadan service selector sunar. Seed script sentetik business flow tamamlandıktan sonra beklenen evidence count'larını doğrular.

Milestone 10 derived search model'i database ownership'i ihlal etmeden recover edilebilir hale getirir. Owner API'leri bounded current snapshot export eder, Search isolated versioned candidate oluşturur, count ve alias compare-and-swap gate'leri activation'ı korur ve predecessor rollback için retained kalır. Broker recovery automatic poison-message replay yerine safe digest'lerle ayrı inspect/classify/explicit-copy process kullanır.

Milestone 11 yeni business data icat etmeden operational deployment demonstration ekler. `./scripts/validate-kubernetes.ps1` ile iki Kustomize variant'ı render edin; production-oriented base'i ve Compose-backed local dependency contract'larını açıklayın, ardından bir Deployment, NetworkPolicy, PDB ve HPA inceleyin. Container'ların fixed non-root user ile çalıştığını, root filesystem'lerin read-only olduğunu, probe ve resource bound'larının bulunduğunu ve hiçbir Secret value'nun commit edilmediğini gösterin. `deploy/kubernetes/scripts/apply-local.ps1` yalnızca disposable local cluster aktifken kullanılmalıdır; rendered manifest başarılı live rollout kanıtı değildir.

Milestone 12 delivery'yi business-data flow'dan ayrı gösterir. Jenkins Java/React verification ve blocking SonarQube Quality Gate çalıştırır, Maven snapshot'larını Nexus'a publish eder, CycloneDX SBOM'ları archive eder ve Harbor'a altı full-Git-SHA OCI tag publish eder. Kustomize immutable revision'ı Git'e kaydeder; ardından Argo CD bunu disposable cluster'a synchronize eder. [Odaklı CI/CD demo](milestone-12-ci-cd-demo.md) dosyasını kullanın. Registry retry, aynı commit için zaten geçmiş quality stage'lerini tekrar çalıştırmamalıdır.

## Ön koşullar

1. `.env.example` dosyasını ignore edilen `.env` olarak kopyalayın ve placeholder'ları değiştirin.
2. Güncel stack'i `docker compose up --build` ile başlatın.
3. Keycloak'ta credential'larını commit etmeden beş temporary local user oluşturun:
   - hospital user: `HOSPITAL_USER`, user attribute
     `providerId=30000000-0000-0000-0000-000000000001`
   - other-provider hospital user: `HOSPITAL_USER`, user attribute
     `providerId=30000000-0000-0000-0000-000000000002`, yalnızca ownership-denial evidence için
   - insurance user: `INSURANCE_SPECIALIST`
   - claim user: `CLAIM_APPROVER`
   - governance user: `SYSTEM_ADMIN`
4. RabbitMQ Management'in `http://localhost:15672` adresinde erişilebilir olduğunu doğrulayın; credential'lar ignore edilen `.env` dosyasından gelir.
5. APISIX, Elasticsearch, APM Server ve Kibana'nın sırasıyla `9080`, `9200`, `8200` ve `5601` portlarında erişilebilir olduğunu doğrulayın. Search Service internal kalır.
6. Configured OIDC login üzerinden short-lived access token alın ve yalnızca mevcut shell içinde tutun.

```powershell
$env:DEMO_HOSPITAL_TOKEN = "<short-lived-token>"
$env:DEMO_INSURANCE_TOKEN = "<short-lived-token>"
$env:DEMO_CLAIM_APPROVER_TOKEN = "<short-lived-token>"
$env:DEMO_SYSTEM_ADMIN_TOKEN = "<short-lived-token>"
.\demo\seed-demo-data.ps1
```

Compose Notification Worker ve database çalışıyorsa `-VerifyNotificationDelivery` ekleyin. Script ardından her decision için delivery row'u bekler:

```powershell
.\demo\seed-demo-data.ps1 -VerifyNotificationDelivery
```

Token'lar parameter/environment value'dur ve script tarafından asla yazılmaz. Her run policy, invoice ve payment reference'larına timestamp suffix ekler; böylece production uniqueness rule'larını bozmadan script tekrar tekrar çalıştırılabilir.

Tamamen repeatable local setup için companion script bu üç user'ı oluşturabilir, role'leri atayabilir, local-only direct-grant seeder client oluşturabilir, short-lived token'ları memory'de alabilir ve aynı seed operation'ı çalıştırabilir. Tüm value'ları yalnızca mevcut shell içinde tanımlayın:

```powershell
$env:DEMO_KEYCLOAK_ADMIN_USERNAME = "<local-admin>"
$env:DEMO_KEYCLOAK_ADMIN_PASSWORD = "<local-admin-password>"
$env:DEMO_USER_PASSWORD = "<temporary-local-demo-password>"
.\demo\prepare-and-seed-local-demo.ps1
```

Repeatable preparation script notification verification'ı varsayılan olarak etkinleştirir. Final JSON rejected, settled ve disputed pre-authorization notification'ları için `DELIVERED` raporlamalıdır. `-SkipNotificationVerification` yalnızca business seed bilinçli olarak RabbitMQ/worker runtime olmadan çalıştırılırken kullanılmalıdır.

Gateway verification local per-IP rate quota'yı bilinçli olarak `429` kanıtlanana kadar tüketir. Seeding hemen sonrasında portal screenshot alacaksanız preparation script'i `-SkipGatewayVerification` ile çalıştırın, sayfaları capture edin ve gateway verification'ı ayrı run'da çalıştırın (veya bir dakikalık quota window reset'ini bekleyin). Böylece iki ayrı valid check birbirini etkilemez.

Direct-grant client yalnızca çalışan local Keycloak database içinde vardır; imported realm'in veya production authentication design'ın parçası değildir. Script password/token yazdırmaz veya persist etmez. Browser login committed `health-insurance-web` Authorization Code + PKCE client'ını kullanmaya devam eder. Script ayrıca `providerId` değerini user'ların görebildiği fakat yalnızca administrator'ların edit edebildiği managed user-profile attribute olarak register eder; Keycloak 26 aksi halde undeclared custom attribute'ları varsayılan olarak ignore eder.

### Yalnızca Authorization öğrenme checkpoint'i

Authorization Service öğrenirken ilgisiz Claims/Search workflow'larını başlatmayın veya yeniden seed etmeyin. Yalnızca local Keycloak identity'lerini hazırlayın:

```powershell
.\demo\prepare-and-seed-local-demo.ps1 -SkipDataSeed
```

Policy ve Authorization'ı PostgreSQL, Kafka ve RabbitMQ dependency'leriyle çalıştırın; ardından submit, detail, approve ve repeat-approve request'lerini yürütün. Beklenen state'ler `PENDING`, `APPROVED` ve repeat decision için RFC 9457 `409`'dur. Matching aggregate, minimized audit action'lar ve acknowledged broker outbox row'larını [odaklı doğrulama rehberi](../development/authorization-service-local-verification.md) ile inceleyin.

## Oluşturulan veri

Source definition'lar [demo-data.json](../../demo/demo-data.json) içindedir. Script şunları oluşturur ve doğrular:

| Record | Beklenen final state | Amaç |
| --- | --- | --- |
| MRI ve laboratory coverage içeren Policy | `ACTIVE` | Policy validity, coverage ve limit gösterimi |
| Laboratory pre-authorization | `PENDING` | Work-queue ve decision gösterimi |
| MRI pre-authorization | `REJECTED` | Rejection state ve reason |
| MRI pre-authorization + event-created claim/invoice | `APPROVED` / `APPROVED` / `SETTLED` | Outbox, Kafka, adjudication ve payment flow |
| MRI pre-authorization + event-created claim/invoice | `APPROVED` / `APPROVED` / `DISPUTED` | Eventual creation ve outstanding reconciliation |
| Üç provider notification delivery | `DELIVERED` | Authorization outbox, publisher confirm, RabbitMQ consumption, worker idempotency ve commit-before-ack |
| Claim ve decision search document'ları | Indexed | Transactional projection outbox, Kafka delivery, deterministic idempotency ve Elasticsearch query |
| Authorization audit evidence | Settled authorization için en az iki row | Submission ve approval local business state ile commit edildi |
| Policy audit evidence | Generated policy için en az bir row | Policy issuance ve minimized actor evidence |
| Claim audit evidence | Settled claim için en az üç row | Submission, review ve approval transition'ları |
| Invoice audit evidence | Settled invoice için en az beş row | Issuance, dispute, reconciliation, payment ve settlement-related transition'lar |
| Versioned search candidate | `ACTIVE`, `healthcare-operations` alias arkasında | Owner snapshot, revision ordering, count gate, atomic alias swap ve retained rollback index |

## Canlı sunum script'i

1. Portal'ı hospital user olarak açın. Authorization Code + PKCE'nin browser'ı authenticate ettiğini ve `provider_id` değerinin queue'yu scope ettiğini açıklayın.
2. Pending, approved ve rejected record'ların bulunduğu work queue'yu gösterin. Status filter uygulayın, sorting'i değiştirin ve detail page açın.
3. Covered MRI request gönderin. Authorization'ın Policy'yi synchronous çağırdığını ve yalnızca eligible result sonrasında persist ettiğini vurgulayın.
4. Uncovered service veya limit üzerindeki amount deneyin ve RFC 9457 `422` hatasını gösterin. Authorization oluşturulmaz.
5. Insurance specialist olarak sign in olun, pending record'u açın ve approve/reject edin. Repeat decision `409 Conflict` döndürmelidir.
6. Approval ile outbox row'un birlikte commit edildiğini açıklayın. Script, Kafka delivery claim/invoice oluşturana kadar `GET /claims/by-pre-authorization/{id}` endpoint'ini poll eder; duplicate delivery `processed_messages` ile neutralize edilir.
7. RabbitMQ Queues and Streams görünümünü açın. Durable delivery queue, DLX/DLK argument'ları, durable DLQ, bir consumer ve başarılı processing sonrası sıfır pending message gösterin. Ardından `notification_deliveries` tablosunu inceleyin ve üç `business_reference_id` value'sunu JSON summary ile eşleştirin.
8. Claims/Billing API üzerinden settled scenario'yu inceleyin. `SUBMITTED → UNDER_REVIEW → APPROVED` ve `ISSUED → DISPUTED → MATCHED → SETTLED` transition'larını açıklayın.
9. `DISPUTED` durumda bırakılan ikinci invoice'u inceleyin; claim adjudication ile invoice reconciliation'ın neden ayrı aggregate sorumlulukları olduğunu açıklayın.
10. Insurance specialist olarak Healthcare Search'ü açın, generated policy number ile arayın ve Claims filter uygulayın. Eventual consistency'yi açıklayın ve hospital user'ın signed provider scope'u override edemediğini gösterin.
11. Kibana APM'de Java service'leri ve trace navigation'ı gösterin. Portal `X-Correlation-ID` response header'ını ECS JSON log'lardaki aynı `correlationId` ile karşılaştırın. Correlation'ın diagnostic context olduğunu, distributed ACID olmadığını vurgulayın.
12. `system-admin-demo` olarak sign in olun, Audit Trail'i açın, Authorization/Policy/Claims-Billing arasında geçiş yapın ve JSON summary içinden bir aggregate ID ile filter uygulayın. Dual controller/use-case authorization, bounded filter, deterministic page, minimized field ve UI'ın neden central audit database ima etmediğini açıklayın.
13. Search rebuild'i process variable içinde tutulan `SYSTEM_ADMIN` token ile çalıştırın. Distinct count'un stable alias count ile eşleştiğini gösterin; ardından Elasticsearch'te active candidate ve retained predecessor'ı gösterin. Event write'ların alias üzerinden devam ettiğini ve stale revision'ların no-op olduğunu açıklayın. Repeatable visual evidence olarak `11-search-rebuild-recovery.png` kullanın.
14. `inspect-recovery-status.ps1` çalıştırın; ardından bir Kafka DLT ve RabbitMQ DLQ inceleyin. Output'un payload yerine count/digest içerdiğini vurgulayın. Replay için neden transient classification, bounded attempt ve explicit confirmation gerektiğini açıklayın; main happy-path demo içinde poison data üretmeyin veya replay etmeyin.
15. Event, architecture ve ER diyagramlarıyla bitirin; ayrı Kafka-event ve RabbitMQ-task semantics, at-least-once delivery, idempotency, bounded retry, DLT/DLQ, database ownership ve optimistic locking'i vurgulayın.

## Beklenen negative demonstration'lar

- Token olmadan request APISIX tarafından `401` ve `application/problem+json` ile reddedilir.
- Invalid token upstream çağrılmadan reddedilir.
- Keycloak'ın ilgisiz `admin-cli` audience'ına issue edilmiş correctly signed token `403` ile reddedilir; `health-insurance-api` taşıyan token kabul edilir.
- 1 MiB üzerindeki request `413`, local one-minute quota aşıldığında rate-limit header'larıyla `429` döner.
- `8081`–`8084` portlarına direct host access başarısız olur; API service'leri yalnızca Compose network içinde expose edilir.
- Kubernetes variant hiçbir backend Service port'u expose etmez; APISIX'e yalnızca temporary local port-forward ile ulaşılır.
- Başka provider'a ait hospital token record'ları okuyamaz: `403`.
- Hospital token pre-authorization veya claim approve edemez: `403`.
- Pending/rejected pre-authorization claim oluşturamaz: `409`.
- Aynı authorization için duplicate claim `409` döndürür.
- Invoice match olmadan payment `409` döndürür.
- Overpayment veya duplicate payment reference error döndürür ve invoice'u değiştirmez.
- Validation sırasında Policy veya Authorization unavailable ise caller `503` alır ve local aggregate persist edilmez.
- Kafka geçici unavailable ise decision committed kalır, outbox row unpublished kalır; Kafka restart sonrası relay resend eder.
- Poison event toplam üç kez retry edilir ve sonra `.DLT` topic'te görünür.
- Transient notification failure toplam üç bounded attempt alır; her attempt yeni transaction içindedir; exhaustion task'ı RabbitMQ DLQ'ya route eder.
- Unsupported notification `taskVersion` retry edilmez ve dead-letter edilir.
- Aynı valid `taskId` iki kez publish edilirse tek `DELIVERED` row oluşur ve duplicate sender invocation olmaz.
- Redis'i durdurun ve coverage evaluation'ı tekrarlayın: safe log'lar cache unavailability raporlar, PostgreSQL authoritative result üretmeye devam eder.
- Elasticsearch'ü durdurun: existing command workflow'ları devam eder ve committed claim projection outbox intent recoverable kalır; search geçici olarak fail olur.
- Hospital user Search'e başka `providerId` verse bile signed token içindeki provider'a scope edilir.
- Non-administrator hiçbir audit API çağramaz veya Audit Trail'e navigate edemez.
- Invalid audit action ve 100 üzerindeki page size arbitrary database query'ye iletilmek yerine reddedilir.
- Her service'in `audit_records` tablosunda update/delete/truncate girişimi PostgreSQL tarafından reddedilir.
- 200 üzerindeki projection export page veya non-`SYSTEM_ADMIN` caller application boundary'de reddedilir.
- Wrong expected count ile partial candidate activation `409` döndürür ve stable alias predecessor üzerinde kalır.
- Başka rebuild alias'ı önce değiştirirse compare-and-swap stale activation veya rollback'i reddeder.
- Older `sourceRevision` event daha yeni search state'i overwrite edemez.
- `Transient` classification ve `-ConfirmReplay` olmadan DLT/DLQ replay publish öncesinde durur; original'lar quarantine'de kalır.

## En son doğrulanmış backend checkpoint

API-only run `20260914231932`, 2026-09-14 tarihinde gerçek local PostgreSQL, Kafka, RabbitMQ, Notification Worker, Elasticsearch ve Keycloak instance'larına karşı tamamlandı. Content-aware Search assertion generated policy için tam beş record döndürdü. Rehearsal sırasında bulunan detaylı result ve defect'ler [backend end-to-end verification guide](../development/backend-end-to-end-local-verification.md) içinde kaydedilmiştir.

## Sıfırlama

Demo data disposable local Docker volume'larda saklanır. Tutmak için `docker compose stop` kullanın. Silmek için, local data'ya ihtiyaç olmadığını doğruladıktan sonra açıkça `docker compose down -v` çalıştırın; bu dört database volume'unu, RabbitMQ, Redis, Kafka ve retained tüm Elasticsearch index'lerini siler. Candidate/predecessor deletion bilinçli olarak rebuild veya rollback script'lerinin parçası değildir.
