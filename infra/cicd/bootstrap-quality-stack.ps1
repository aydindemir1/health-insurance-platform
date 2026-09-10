[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$directory = $PSScriptRoot
$environmentFile = Join-Path $directory '.env'
$composeFile = Join-Path $directory 'compose.quality.yaml'

if (-not (Test-Path $environmentFile)) {
    throw 'Run start-quality-stack.ps1 first.'
}

$lines = [System.Collections.Generic.List[string]](Get-Content $environmentFile)
$passwordLine = $lines | Where-Object { $_ -match '^SONAR_ADMIN_PASSWORD=' } | Select-Object -First 1
$configuredPassword = if ($passwordLine) { $passwordLine.Substring($passwordLine.IndexOf('=') + 1) } else { '' }
if ($configuredPassword -notmatch '[A-Z]' -or $configuredPassword -notmatch '[a-z]' -or
    $configuredPassword -notmatch '[0-9]' -or $configuredPassword -notmatch '[^A-Za-z0-9]') {
    $password = 'Aa1!' + [Convert]::ToHexString(
        [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)
    ).ToLowerInvariant()
    if ($passwordLine) {
        $lines[$lines.IndexOf($passwordLine)] = "SONAR_ADMIN_PASSWORD=$password"
    } else {
        $lines.Add("SONAR_ADMIN_PASSWORD=$password")
    }
    [System.IO.File]::WriteAllLines($environmentFile, $lines)
}

$values = @{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}

function New-BasicCredential([string]$user, [string]$password) {
    $secure = ConvertTo-SecureString $password -AsPlainText -Force
    [PSCredential]::new($user, $secure)
}

$sonarUrl = 'http://localhost:9000'
$sonarCredential = New-BasicCredential 'admin' $values.SONAR_ADMIN_PASSWORD
$validation = Invoke-RestMethod -Uri "$sonarUrl/api/authentication/validate" `
    -Authentication Basic -AllowUnencryptedAuthentication -Credential $sonarCredential

if (-not $validation.valid) {
    $defaultCredential = New-BasicCredential 'admin' 'admin'
    Invoke-RestMethod -Method Post -Uri "$sonarUrl/api/users/change_password" `
        -Authentication Basic -AllowUnencryptedAuthentication -Credential $defaultCredential `
        -Body @{ login = 'admin'; previousPassword = 'admin'; password = $values.SONAR_ADMIN_PASSWORD } | Out-Null
}

try {
    Invoke-RestMethod -Method Post -Uri "$sonarUrl/api/user_tokens/revoke" `
        -Authentication Basic -AllowUnencryptedAuthentication -Credential $sonarCredential `
        -Body @{ name = 'jenkins-local' } | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
}

$generated = Invoke-RestMethod -Method Post -Uri "$sonarUrl/api/user_tokens/generate" `
    -Authentication Basic -AllowUnencryptedAuthentication -Credential $sonarCredential `
    -Body @{ name = 'jenkins-local'; type = 'GLOBAL_ANALYSIS_TOKEN' }

$updated = [System.Collections.Generic.List[string]]::new()
$tokenWritten = $false
foreach ($line in Get-Content $environmentFile) {
    if ($line -match '^SONAR_TOKEN=') {
        $updated.Add("SONAR_TOKEN=$($generated.token)")
        $tokenWritten = $true
    } else {
        $updated.Add($line)
    }
}
if (-not $tokenWritten) { $updated.Add("SONAR_TOKEN=$($generated.token)") }
[System.IO.File]::WriteAllLines($environmentFile, $updated)

$webhooks = Invoke-RestMethod -Uri "$sonarUrl/api/webhooks/list" `
    -Authentication Basic -AllowUnencryptedAuthentication -Credential $sonarCredential
if (-not ($webhooks.webhooks | Where-Object { $_.name -eq 'jenkins-local' })) {
    Invoke-RestMethod -Method Post -Uri "$sonarUrl/api/webhooks/create" `
        -Authentication Basic -AllowUnencryptedAuthentication -Credential $sonarCredential `
        -Body @{ name = 'jenkins-local'; url = 'http://jenkins:8080/sonarqube-webhook/' } | Out-Null
}

docker compose --env-file $environmentFile -f $composeFile up --detach --build --force-recreate jenkins
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'SonarQube token, Jenkins credential and webhook bootstrap: OK'
