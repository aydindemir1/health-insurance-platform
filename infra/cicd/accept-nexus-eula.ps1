[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [switch]$AcceptEula
)

$ErrorActionPreference = 'Stop'
$environmentFile = Join-Path $PSScriptRoot '.env'
$values = @{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}

$credential = [PSCredential]::new(
    'admin',
    (ConvertTo-SecureString $values.NEXUS_ADMIN_PASSWORD -AsPlainText -Force)
)
$uri = 'http://localhost:8087/service/rest/v1/system/eula'
$state = Invoke-RestMethod -Uri $uri -Authentication Basic `
    -AllowUnencryptedAuthentication -Credential $credential

if (-not $state.accepted) {
    $body = @{ accepted = $true; disclaimer = $state.disclaimer } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri $uri -Authentication Basic `
        -AllowUnencryptedAuthentication -Credential $credential `
        -ContentType 'application/json' -Body $body | Out-Null
}

$verified = Invoke-RestMethod -Uri $uri -Authentication Basic `
    -AllowUnencryptedAuthentication -Credential $credential
if (-not $verified.accepted) { throw 'Nexus Community Edition EULA acceptance failed.' }

Write-Host 'Nexus Community Edition EULA accepted: OK'
