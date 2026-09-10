[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$directory = $PSScriptRoot
$environmentFile = Join-Path $directory '.env'
$composeFile = Join-Path $directory 'compose.quality.yaml'

if (-not (Test-Path $environmentFile)) {
    $random = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)
    $jenkinsPassword = [Convert]::ToHexString($random).ToLowerInvariant()
    $random = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)
    $databasePassword = [Convert]::ToHexString($random).ToLowerInvariant()
    $content = @(
        'JENKINS_ADMIN_USERNAME=local-admin'
        "JENKINS_ADMIN_PASSWORD=$jenkinsPassword"
        'SONAR_DB_USERNAME=sonar'
        "SONAR_DB_PASSWORD=$databasePassword"
        'DOCKER_SOCKET_GID=0'
    )
    [System.IO.File]::WriteAllLines($environmentFile, $content)
    Write-Host 'Created ignored infra/cicd/.env with random local-only credentials.'
}

if (-not (Select-String -Path $environmentFile -Pattern '^DOCKER_SOCKET_GID=' -Quiet)) {
    [System.IO.File]::AppendAllText($environmentFile, "DOCKER_SOCKET_GID=0$([Environment]::NewLine)")
}

docker compose --env-file $environmentFile -f $composeFile up --detach --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'Jenkins: http://localhost:8086'
Write-Host 'SonarQube: http://localhost:9000'
