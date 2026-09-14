[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Context
)

$ErrorActionPreference = 'Stop'
$current = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or $current -ne $Context) {
    throw "Refusing secret update: current context '$current' does not equal '$Context'."
}
if ($current -match 'prod|production') { throw 'Production-like contexts are forbidden.' }

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$environmentFile = Join-Path $root 'infra/cicd/.env'
if (-not (Test-Path $environmentFile)) { throw 'Ignored infra/cicd/.env is missing.' }

$values = @{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if (-not $values.HARBOR_USERNAME -or -not $values.HARBOR_PASSWORD) {
    throw 'Harbor robot credential is missing from the ignored runtime environment.'
}

$username = $values.HARBOR_USERNAME.Replace('$$', '$')
$password = $values.HARBOR_PASSWORD.Replace('$$', '$')
$registry = 'host.minikube.internal:8088'
$authBytes = [Text.Encoding]::UTF8.GetBytes("${username}:${password}")
$dockerConfig = @{
    auths = @{
        $registry = @{
            username = $username
            password = $password
            auth = [Convert]::ToBase64String($authBytes)
        }
    }
} | ConvertTo-Json -Depth 5 -Compress

$secret = @{
    apiVersion = 'v1'
    kind = 'Secret'
    metadata = @{ name = 'harbor-registry'; namespace = 'health-insurance' }
    type = 'kubernetes.io/dockerconfigjson'
    data = @{
        '.dockerconfigjson' = [Convert]::ToBase64String(
            [Text.Encoding]::UTF8.GetBytes($dockerConfig)
        )
    }
} | ConvertTo-Json -Depth 6 -Compress

$secret | kubectl apply --context $Context -f - | Out-Null
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Runtime-only Harbor image pull secret configured: OK'
