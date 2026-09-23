# Search ve messaging recovery runbook

Bu runbook, Milestone 10 kapsamında sunulan çalıştırılabilir lokal recovery kontrollerini kapsar. Bilinçli olarak muhafazakârdır: source database'ler authoritative kalır, aktif Elasticsearch index'i hiçbir zaman yerinde yeniden oluşturulmaz ve dead-letter message'lar hiçbir zaman otomatik olarak replay edilmez.

## Güvenlik sözleşmesi

- Yalnızca sentetik lokal veri kullanın. Access token, message payload, member identifier, policy number, contact detail veya diagnosis bilgisini hiçbir zaman yazdırmayın veya persist etmeyin.
- Classification yapmadan önce inspect edin. Replay işlemini yalnızca failed dependency tekrar healthy olduktan sonra, incelenmiş transient failure için gerçekleştirin.
- Her recovery batch'ini 1–10 broker message arasında; her search ingestion batch'ini ise 1–200 projection arasında tutun.
- Orijinal DLT/DLQ record'larını quarantine evidence olarak koruyun. Sağlanan araçlar incelenmiş message'ın bir kopyasını oluşturur; orijinal message'ı silmez veya acknowledge etmez.
- Active alias, document count, topic, queue veya recovery attempt beklenen value değilse durun. Offset reset ederek veya index silerek telafi etmeyin.
- Runtime token'lar ve RabbitMQ credential'ları yalnızca process variable'larında bulunmalıdır.

## Ön koşullar ve ilk inceleme

Ignore edilen `.env` dosyasını kullanarak lokal stack'i başlatın; ardından container health durumunu ve bounded recovery summary'yi kontrol edin:

```powershell
docker compose up -d
docker compose ps
.\scripts\inspect-recovery-status.ps1 | ConvertTo-Json -Depth 6
```

Summary yalnızca şunları okur:

- Authorization'ın Kafka ve RabbitMQ outbox'ları ile Claims/Billing search outbox'ı için unpublished row count, oldest age ve maximum attempt sayısı;
- tools-only `kafka-cli` Compose profile kullanılarak Kafka consumer-group lag;
- RabbitMQ ready, unacknowledged, consumer ve queue-state counter'ları.

Outbox body veya broker payload göstermez. Non-zero backlog, dependency health ve log'ların incelenmesi gerektiğine dair bir sinyaldir; business transaction'ın başarısız olduğunun kanıtı değildir.

## Elasticsearch projection'ını yeniden oluşturma

Repository içinde saklamadan short-lived lokal `SYSTEM_ADMIN` access token alın ve ardından şunu çalıştırın:

```powershell
$runtimeAccessToken = '<short-lived-system-admin-token>'
.\demo\rebuild-search-index.ps1 `
  -AccessToken $runtimeAccessToken `
  -SchemaVersion 2 `
  -PageSize 100
Remove-Variable runtimeAccessToken
```

Orchestrator şu adımları gerçekleştirir:

1. Search Service mevcut alias target'ını kaydeder ve izole bir `healthcare-operations-v{schema}-{runId}` candidate oluşturur.
2. Authorization kendi database'inden stable `CREATED_AT, id` page'leri export eder.
3. Claims/Billing stable claim-ID page'leri export eder ve kendi database'inden current joined claim/invoice/payment projection'ını hesaplar.
4. Script memory içinde duplicate deterministic document ID'leri tespit eder ve bounded batch'leri APISIX üzerinden gönderir.
5. Search her record'u domain `SearchRecord` olarak validate eder ve owner tarafından tanımlanan monotonic `sourceRevision` değerine göre conditional upsert kullanır.
6. Activation candidate'ı refresh eder, count değerini distinct exported ID count ile karşılaştırır, predecessor'ın değişmediğini doğrular ve stable alias'ı atomik olarak swap eder.

Owner/API/mapping/count failure eski alias'a dokunmaz ve inspection için yalnızca gerekli run/candidate identifier'larını yazdırır. Partial candidate, expected count değiştirilerek activate edilmemelidir.

### Activation doğrulaması

```powershell
curl.exe -sS "http://localhost:9200/_cat/aliases/healthcare-operations?format=json&h=alias,index,is_write_index"
curl.exe -sS "http://localhost:9200/healthcare-operations/_count"
curl.exe -sS "http://localhost:9200/_cat/indices/healthcare-operations-v*?format=json&h=index,docs.count,status"
```

Beklenen evidence tam olarak bir writable alias target, bu target üzerinde exported distinct count ve retained predecessor'dır. Normal read ve event write işlemleri `healthcare-operations` üzerinden devam eder; physical index name'leri yalnızca operational detail'dır.

### Açık rollback

Rollback yalnızca Search Service process active run state'i hâlâ tutuyorsa ve alias hâlâ ilgili run'ın candidate'ını gösteriyorsa geçerlidir:

```powershell
$runtimeAccessToken = '<short-lived-system-admin-token>'
.\demo\rollback-search-index.ps1 `
  -AccessToken $runtimeAccessToken `
  -RunId '<run-id-returned-by-rebuild>'
Remove-Variable runtimeAccessToken
```

Operation başka bir compare-and-swap alias update işlemidir. Candidate ve predecessor'ın ikisini de korur; cleanup ayrı ve bilinçli olarak uygulanmamış bir retention kararıdır. Lokal run registry memory içindedir; bu nedenle Search Service restart'ı manual alias inspection ve yeni review edilmiş recovery plan gerektirir.

## Kafka DLT workflow

Yalnızca allowlist edilmiş iki DLT topic inspect edilebilir:

```powershell
.\scripts\recover-kafka-dlt.ps1 `
  -Action Inspect `
  -DltTopic health.authorization.pre-authorization.v1.DLT `
  -MaxMessages 5
```

Output payload yerine message key, SHA-256 digest ve byte length içerir. Her digest'i consumer error ve deployment evidence kullanarak classify edin:

| Classification | Örnekler | Aksiyon |
| --- | --- | --- |
| Transient | Valid contract geldikten sonra broker/dependency outage | Dependency recovery'yi kanıtlayın, ardından bounded replay |
| Permanent | Unsupported schema version, malformed JSON, invariant violation | Compatible code veya review edilmiş transform oluşana kadar quarantine |
| Unknown | Yetersiz evidence | Quarantine'de tutun ve araştırın |

Review edilmiş transient copy replay işlemini açıkça kaydedin:

```powershell
.\scripts\recover-kafka-dlt.ps1 `
  -Action Replay `
  -DltTopic health.authorization.pre-authorization.v1.DLT `
  -MaxMessages 1 `
  -Classification Transient `
  -RecoveryAttempt 1 `
  -ConfirmReplay
```

Kopya allowlist edilmiş original topic'i hedefler, original key ve payload'u korur ve recovery ID/source/attempt header'ları ekler. Attempt sayısı üç ile sınırlandırılır. Consumer inbox/business uniqueness ve monotonic search revision'lar review edilmiş duplicate'i güvenli hale getirir; poison data'yı geçerli hale getirmez.

## RabbitMQ DLQ workflow

Runtime'da temporary lokal RabbitMQ account sağlayın:

```powershell
$runtimeRabbitUser = '<temporary-monitor-user>'
$runtimeRabbitPassword = '<temporary-password>'
.\scripts\recover-rabbitmq-dlq.ps1 `
  -Action Inspect `
  -Username $runtimeRabbitUser `
  -Password $runtimeRabbitPassword `
  -MaxMessages 5
Remove-Variable runtimeRabbitUser, runtimeRabbitPassword
```

Inspection `ack_requeue_true` kullanır; bu nedenle message'lar `health.notifications.delivery.v1.dlq` içinde kalır. Output digest, byte length, redelivery flag, exchange ve routing key içerir. Transient replay ayrıca `-Classification Transient -RecoveryAttempt 1 -ConfirmReplay` gerektirir; persistent bir kopyayı allowlist edilmiş `health.notifications` exchange ve `pre-authorization.decision` routing key'e publish eder. Orijinal `taskId`, worker idempotency key olarak korunur.

## Failure karar tablosu

| Belirti | Muhtemel neden | Güvenli sonraki adım |
| --- | --- | --- |
| Candidate count mismatch | Refresh/write failure, duplicate owner ID, incomplete page | Alias'ı değiştirmeden bırakın; owner/API ve candidate'ı inceleyin |
| Alias compare-and-swap conflict | Başka operator/rebuild alias'ı değiştirdi | Durun; alias ve retained index'leri inceleyin |
| Legacy search document revision içermiyor | Pre-M10 projection | Reader bunu baseline revision 1'e map eder; rebuild owner'lardan replace eder |
| Outbox pending ve attempt sayısı artıyor | Broker unavailable veya contract send failure | Dependency'yi restore edin; bounded relay retry'larını gözlemleyin |
| Kafka DLT permanent error | Contract/schema/data defect | Quarantine; review edilmiş compatibility handling deploy edin |
| Rabbit DLQ transient delivery error | Provider/dependency outage | Recovery'yi kanıtlayın, tek message kopyalayın, idempotent sonucu doğrulayın |
| Replay yeniden DLT/DLQ'ya dönüyor | Yanlış classification veya dependency hâlâ unhealthy | Replay'i hemen durdurun; yeniden classify edin ve araştırın |

## Mevcut sınırlamalar

- Rebuild coordinator ve run/rollback state lokal ve in-memory'dir; Search Service restart'ları arasında resumable değildir.
- Broker araçları production workload identity ve audited operations API yerine lokal Docker/RabbitMQ access'e dayanır.
- Broker quarantine ayrı quarantine store değil, silmeme yoluyla retention'dır. Destructive discard command yoktur.
- Index lifecycle cleanup, durable checkpoint'ler, automated alert'ler ve recovery command'ları için production authorization/audit sonraki milestone'lara aittir.
