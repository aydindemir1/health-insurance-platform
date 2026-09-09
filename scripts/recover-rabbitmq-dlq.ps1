[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Inspect', 'Replay', 'Quarantine')]
    [string]$Action,

    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Username,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Password,
    [ValidateRange(1, 10)][int]$MaxMessages = 1,
    [ValidateSet('Transient', 'Permanent', 'Unknown')][string]$Classification = 'Unknown',
    [ValidateRange(1, 3)][int]$RecoveryAttempt = 1,
    [string]$ManagementBaseUrl = 'http://localhost:15672',
    [switch]$ConfirmReplay
)

$ErrorActionPreference = 'Stop'
$pair = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $pair" }
$queue = 'health.notifications.delivery.v1.dlq'
$body = @{
    count = $MaxMessages
    ackmode = 'ack_requeue_true'
    encoding = 'auto'
    truncate = 50000
} | ConvertTo-Json -Compress
$encodedQueue = [Uri]::EscapeDataString($queue)
$messages = @(Invoke-RestMethod -Method POST `
    -Uri "$($ManagementBaseUrl.TrimEnd('/'))/api/queues/%2F/$encodedQueue/get" `
    -Headers $headers -ContentType 'application/json' -Body $body) |
    Where-Object { $null -ne $_ -and $null -ne $_.payload }
$safeMessages = @($messages | ForEach-Object {
    $bytes = [Text.Encoding]::UTF8.GetBytes([string]$_.payload)
    [pscustomobject]@{
        sha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
        byteLength = $bytes.Length
        redelivered = [bool]$_.redelivered
        exchange = $_.exchange
        routingKey = $_.routing_key
    }
})

if ($Action -eq 'Inspect' -or $Action -eq 'Quarantine') {
    [pscustomobject]@{
        action = $Action
        queue = $queue
        classification = $Classification
        messages = $safeMessages
        note = 'Payloads were hashed in memory; originals remain quarantined in the DLQ.'
    }
    return
}
if ($Classification -ne 'Transient') {
    throw 'Replay requires Classification=Transient after dependency recovery is proven.'
}
if (-not $ConfirmReplay) {
    throw 'Replay is a state-changing operation. Re-run with -ConfirmReplay after inspection.'
}

$recoveryId = [guid]::NewGuid().ToString()
foreach ($message in $messages) {
    $publishBody = @{
        properties = @{
            delivery_mode = 2
            message_id = $message.properties.message_id
            correlation_id = $message.properties.correlation_id
            headers = @{
                'x-recovery-id' = $recoveryId
                'x-recovery-attempt' = $RecoveryAttempt
                'x-recovery-source' = $queue
            }
        }
        routing_key = 'pre-authorization.decision'
        payload = [string]$message.payload
        payload_encoding = 'string'
    } | ConvertTo-Json -Depth 6 -Compress
    $published = Invoke-RestMethod -Method POST `
        -Uri "$($ManagementBaseUrl.TrimEnd('/'))/api/exchanges/%2F/health.notifications/publish" `
        -Headers $headers -ContentType 'application/json' -Body $publishBody
    if (-not $published.routed) { throw 'RabbitMQ replay was not routed; DLQ original remains intact.' }
}

[pscustomobject]@{
    action = 'Replay'
    sourceQueue = $queue
    targetExchange = 'health.notifications'
    recoveryId = $recoveryId
    recoveryAttempt = $RecoveryAttempt
    replayed = $safeMessages
    note = 'Original DLQ messages remain quarantined; downstream taskId idempotency protects copy replay.'
}
