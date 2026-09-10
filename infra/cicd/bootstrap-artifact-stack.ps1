[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$directory = $PSScriptRoot
$environmentFile = Join-Path $directory '.env'
$qualityCompose = Join-Path $directory 'compose.quality.yaml'
$nexusContainer = 'health-insurance-artifacts-nexus-1'
$nexusUrl = 'http://localhost:8087'

$initialPassword = (docker exec $nexusContainer sh -c 'cat /nexus-data/admin.password').Trim()
if ($LASTEXITCODE -ne 0 -or -not $initialPassword) { throw 'Nexus initial password is unavailable.' }

$publisherPassword = 'Aa1!' + [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(24)
).ToLowerInvariant()
$adminPassword = 'Aa1!' + [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(24)
).ToLowerInvariant()
$admin = [PSCredential]::new('admin', (ConvertTo-SecureString $initialPassword -AsPlainText -Force))

$privileges = @()
foreach ($repository in @('maven-releases', 'maven-snapshots')) {
    foreach ($action in @('add', 'browse', 'edit', 'read')) {
        $privileges += "nx-repository-view-maven2-$repository-$action"
    }
}
$role = @{
    id = 'health-publisher'; name = 'Health artifact publisher'
    description = 'Publish and read project Maven artifacts only'
    privileges = $privileges; roles = @()
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post -Uri "$nexusUrl/service/rest/v1/security/roles" `
        -Authentication Basic -AllowUnencryptedAuthentication -Credential $admin `
        -ContentType 'application/json' -Body $role | Out-Null
} catch {
    Invoke-RestMethod -Method Put -Uri "$nexusUrl/service/rest/v1/security/roles/health-publisher" `
        -Authentication Basic -AllowUnencryptedAuthentication -Credential $admin `
        -ContentType 'application/json' -Body $role | Out-Null
}

$user = @{
    userId = 'jenkins-publisher'; firstName = 'Jenkins'; lastName = 'Publisher'
    emailAddress = 'jenkins-publisher@example.invalid'; password = $publisherPassword
    status = 'active'; roles = @('health-publisher')
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$nexusUrl/service/rest/v1/security/users" `
    -Authentication Basic -AllowUnencryptedAuthentication -Credential $admin `
    -ContentType 'application/json' -Body $user | Out-Null

Invoke-RestMethod -Method Put -Uri "$nexusUrl/service/rest/v1/security/users/admin/change-password" `
    -Authentication Basic -AllowUnencryptedAuthentication -Credential $admin `
    -ContentType 'text/plain' -Body $adminPassword | Out-Null

$values = [ordered]@{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
$values.NEXUS_ADMIN_PASSWORD = $adminPassword
$values.NEXUS_USERNAME = 'jenkins-publisher'
$values.NEXUS_PASSWORD = $publisherPassword
$lines = $values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
[IO.File]::WriteAllLines($environmentFile, $lines)

docker compose --env-file $environmentFile -f $qualityCompose up --detach --build --force-recreate jenkins
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Nexus least-privilege publisher and Jenkins credential: OK'
