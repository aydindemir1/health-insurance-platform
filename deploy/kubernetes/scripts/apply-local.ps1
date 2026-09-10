[CmdletBinding()]
param(
    [string]$Context = 'kind-health-insurance',
    [string]$EnvironmentFile = '.env',
    [switch]$AllowNonLocalContext
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$namespace = 'health-insurance'

if (-not $AllowNonLocalContext -and $Context -notmatch '^(kind-|minikube$)') {
    throw "Refusing non-local context '$Context'. Use -AllowNonLocalContext only after review."
}

$environmentPath = Join-Path $repositoryRoot $EnvironmentFile
if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Environment file not found: $environmentPath"
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed.Split('=', 2)
    if ($parts.Count -eq 2) {
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
}

function Get-RequiredValue {
    param([string]$Name)
    $processValue = [Environment]::GetEnvironmentVariable($Name)
    $value = if ([string]::IsNullOrWhiteSpace($processValue)) { $values[$Name] } else { $processValue }
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Required value is missing: $Name" }
    return $value
}

function Set-OpaqueSecret {
    param([string]$Name, [hashtable]$StringValues)
    $encoded = @{}
    foreach ($entry in $StringValues.GetEnumerator()) {
        $encoded[$entry.Key] = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($entry.Value))
    }
    $manifest = @{
        apiVersion = 'v1'
        kind = 'Secret'
        metadata = @{ name = $Name; namespace = $namespace }
        type = 'Opaque'
        data = $encoded
    } | ConvertTo-Json -Depth 5 -Compress
    $manifest | & kubectl --context $Context apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply Secret: $Name" }
}

Push-Location $repositoryRoot
try {
    & kubectl --context $Context apply -f deploy/kubernetes/base/namespace.yaml | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to apply namespace.' }

    Set-OpaqueSecret 'authorization-db-credentials' @{
        username = Get-RequiredValue 'AUTHORIZATION_DB_USERNAME'
        password = Get-RequiredValue 'AUTHORIZATION_DB_PASSWORD'
    }
    Set-OpaqueSecret 'policy-db-credentials' @{
        username = Get-RequiredValue 'POLICY_DB_USERNAME'
        password = Get-RequiredValue 'POLICY_DB_PASSWORD'
    }
    Set-OpaqueSecret 'claims-billing-db-credentials' @{
        username = Get-RequiredValue 'CLAIMS_BILLING_DB_USERNAME'
        password = Get-RequiredValue 'CLAIMS_BILLING_DB_PASSWORD'
    }
    Set-OpaqueSecret 'notification-db-credentials' @{
        username = Get-RequiredValue 'NOTIFICATION_DB_USERNAME'
        password = Get-RequiredValue 'NOTIFICATION_DB_PASSWORD'
    }
    Set-OpaqueSecret 'rabbitmq-credentials' @{
        username = Get-RequiredValue 'RABBITMQ_USERNAME'
        password = Get-RequiredValue 'RABBITMQ_PASSWORD'
    }
    $apisixCompatibilityValue = [Environment]::GetEnvironmentVariable('APISIX_OIDC_CLIENT_SECRET')
    if ([string]::IsNullOrWhiteSpace($apisixCompatibilityValue)) {
        $apisixCompatibilityValue = $values['APISIX_OIDC_CLIENT_SECRET']
    }
    if ([string]::IsNullOrWhiteSpace($apisixCompatibilityValue)) {
        $apisixCompatibilityValue = [guid]::NewGuid().ToString('N')
    }
    Set-OpaqueSecret 'apisix-oidc-credentials' @{
        'client-secret' = $apisixCompatibilityValue
    }

    & kubectl --context $Context apply -k deploy/kubernetes/overlays/local
    if ($LASTEXITCODE -ne 0) { throw 'Local overlay apply failed.' }
    Write-Host "Local Kubernetes overlay applied to '$Context'. Secret values were not printed."
}
finally {
    Pop-Location
}
