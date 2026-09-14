[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$cicdDirectory = Split-Path $PSScriptRoot -Parent
$environmentFile = Join-Path $cicdDirectory '.env'
$qualityCompose = Join-Path $cicdDirectory 'compose.quality.yaml'
$harborUrl = 'http://localhost:8088'
$projectName = 'health-insurance'
$robotName = 'jenkins-publisher'
$robotDurationSeconds = 90 * 24 * 60 * 60

$values = [ordered]@{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
if (-not $values.HARBOR_ADMIN_PASSWORD) { throw 'Run start-local-harbor.ps1 first.' }

$admin = [PSCredential]::new(
    'admin',
    (ConvertTo-SecureString $values.HARBOR_ADMIN_PASSWORD -AsPlainText -Force)
)
$request = @{
    Authentication = 'Basic'
    AllowUnencryptedAuthentication = $true
    Credential = $admin
    ContentType = 'application/json'
}

try {
    Invoke-RestMethod @request -Method Post -Uri "$harborUrl/api/v2.0/projects" -Body (@{
        project_name = $projectName
        metadata = @{ public = 'false'; auto_scan = 'false' }
    } | ConvertTo-Json -Depth 4) | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 409) { throw }
}

if ($values.HARBOR_ROBOT_ID) {
    try {
        Invoke-RestMethod @request -Method Delete -Uri "$harborUrl/api/v2.0/robots/$($values.HARBOR_ROBOT_ID)" | Out-Null
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
    }
}

$robot = Invoke-RestMethod @request -Method Post -Uri "$harborUrl/api/v2.0/robots" -Body (@{
    name = $robotName
    description = 'Jenkins push/pull account for immutable project images'
    duration = $robotDurationSeconds
    disable = $false
    level = 'project'
    permissions = @(@{
        kind = 'project'
        namespace = $projectName
        access = @(
            @{ resource = 'repository'; action = 'pull' }
            @{ resource = 'repository'; action = 'push' }
            @{ resource = 'artifact'; action = 'read' }
            @{ resource = 'artifact'; action = 'create' }
        )
    })
} | ConvertTo-Json -Depth 8)

$values.HARBOR_USERNAME = $robot.name.Replace('$', '$$')
$values.HARBOR_PASSWORD = $robot.secret.Replace('$', '$$')
$values.HARBOR_ROBOT_ID = $robot.id
[IO.File]::WriteAllLines(
    $environmentFile,
    @($values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" })
)

docker compose --env-file $environmentFile -f $qualityCompose up --detach --no-build --force-recreate jenkins
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Harbor private project and 90-day least-privilege Jenkins robot: OK (automatic scanning disabled)'
