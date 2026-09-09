[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Inspect', 'Replay', 'Quarantine')]
    [string]$Action,

    [Parameter(Mandatory = $true)]
    [ValidateSet(
        'health.authorization.pre-authorization.v1.DLT',
        'health.claims.search-projection.v1.DLT')]
    [string]$DltTopic,

    [ValidateRange(1, 10)]
    [int]$MaxMessages = 1,

    [ValidateSet('Transient', 'Permanent', 'Unknown')]
    [string]$Classification = 'Unknown',

    [ValidateRange(1, 3)]
    [int]$RecoveryAttempt = 1,

    [switch]$ConfirmReplay
)

$ErrorActionPreference = 'Stop'
$originalTopic = switch ($DltTopic) {
    'health.authorization.pre-authorization.v1.DLT' { 'health.authorization.pre-authorization.v1' }
    'health.claims.search-projection.v1.DLT' { 'health.claims.search-projection.v1' }
}

$lines = @(docker compose run --rm --no-deps `
    --entrypoint /opt/kafka/bin/kafka-console-consumer.sh kafka-cli `
    --bootstrap-server kafka:19092 --topic $DltTopic --from-beginning `
    --max-messages $MaxMessages --timeout-ms 5000 `
    --property print.key=true --property print.value=true --property 'key.separator=###' 2>$null)
$records = @($lines | Where-Object { $_ -match '###' } | ForEach-Object {
    $parts = ([string]$_).Split('###', 2)
    $bytes = [Text.Encoding]::UTF8.GetBytes($parts[1])
    [pscustomobject]@{
        key = $parts[0]
        payload = $parts[1]
        digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
        byteLength = $bytes.Length
    }
})

$safeRecords = @($records | ForEach-Object {
    [pscustomobject]@{ key = $_.key; sha256 = $_.digest; byteLength = $_.byteLength }
})

if ($Action -eq 'Inspect' -or $Action -eq 'Quarantine') {
    [pscustomobject]@{
        action = $Action
        topic = $DltTopic
        classification = $Classification
        records = $safeRecords
        note = if ($Action -eq 'Quarantine') {
            'Records remain in the DLT; deploy compatible handling before replay.'
        } else { 'Payloads were hashed in memory and were not printed.' }
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
foreach ($record in $records) {
    $headers = "x-recovery-id:$recoveryId,x-recovery-attempt:$RecoveryAttempt,x-recovery-source:$DltTopic"
    $line = "$headers|||$($record.key)###$($record.payload)"
    $line | docker compose run --rm --no-deps -T `
        --entrypoint /opt/kafka/bin/kafka-console-producer.sh kafka-cli `
        --bootstrap-server kafka:19092 --topic $originalTopic `
        --property parse.headers=true --property 'headers.delimiter=,' `
        --property 'headers.separator=|||' --property parse.key=true `
        --property 'key.separator=###' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Replay publish failed for digest $($record.digest)" }
}

[pscustomobject]@{
    action = 'Replay'
    sourceTopic = $DltTopic
    targetTopic = $originalTopic
    recoveryId = $recoveryId
    recoveryAttempt = $RecoveryAttempt
    replayed = $safeRecords
    note = 'Original DLT records are retained as quarantine evidence; consumers remain idempotent.'
}
