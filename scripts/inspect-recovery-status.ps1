[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Invoke-OutboxSummary {
    param(
        [Parameter(Mandatory = $true)][string]$Service,
        [Parameter(Mandatory = $true)][string]$Table
    )
    $sql = "select count(*), coalesce(extract(epoch from (now() - min(occurred_at)))::bigint, 0), coalesce(max(publish_attempts), 0) from $Table where published_at is null"
    $line = docker compose exec -T $Service sh -c "psql -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -At -F '|' -c `"$sql`""
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect $Table in $Service" }
    $parts = ([string]$line).Trim().Split('|')
    [pscustomobject]@{
        service = $Service
        table = $Table
        pending = [long]$parts[0]
        oldestAgeSeconds = [long]$parts[1]
        maximumAttempts = [int]$parts[2]
    }
}

$outboxes = @(
    Invoke-OutboxSummary -Service 'authorization-db' -Table 'outbox_messages'
    Invoke-OutboxSummary -Service 'authorization-db' -Table 'notification_task_outbox'
    Invoke-OutboxSummary -Service 'claims-billing-db' -Table 'claim_search_outbox'
)

$kafkaLag = docker compose run --rm --no-deps `
    --entrypoint /opt/kafka/bin/kafka-consumer-groups.sh kafka-cli `
    --bootstrap-server kafka:19092 --all-groups --describe 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Kafka consumer groups' }

$rabbitQueues = docker compose exec -T rabbitmq rabbitmqctl -q list_queues `
    name messages_ready messages_unacknowledged consumers state
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect RabbitMQ queues' }

[pscustomobject]@{
    capturedAt = [DateTimeOffset]::UtcNow
    outboxes = $outboxes
    kafkaConsumerLag = @($kafkaLag)
    rabbitQueues = @($rabbitQueues)
}
