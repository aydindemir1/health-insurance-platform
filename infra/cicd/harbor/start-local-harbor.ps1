[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$version = 'v2.15.2'
$directory = $PSScriptRoot
$environmentFile = Join-Path (Split-Path $directory -Parent) '.env'
$runtimeRoot = Join-Path $directory ".runtime/$version"
$archive = Join-Path $runtimeRoot 'harbor-online-installer.tgz'
$harborDirectory = Join-Path $runtimeRoot 'harbor'

New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
if (-not (Test-Path (Join-Path $harborDirectory 'harbor.yml.tmpl'))) {
    Invoke-WebRequest "https://github.com/goharbor/harbor/releases/download/$version/harbor-online-installer-$version.tgz" `
        -OutFile $archive
    tar -xzf $archive -C $runtimeRoot
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$values = [ordered]@{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}
foreach ($name in @('HARBOR_ADMIN_PASSWORD', 'HARBOR_DB_PASSWORD')) {
    if (-not $values.Contains($name)) {
        $values[$name] = 'Aa1!' + [Convert]::ToHexString(
            [Security.Cryptography.RandomNumberGenerator]::GetBytes(24)
        ).ToLowerInvariant()
    }
}
[IO.File]::WriteAllLines($environmentFile, ($values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }))

$output = [Collections.Generic.List[string]]::new()
$skipHttps = $false
$inHttp = $false
$inDatabase = $false
foreach ($line in Get-Content (Join-Path $harborDirectory 'harbor.yml.tmpl')) {
    if ($line -match '^https:') { $skipHttps = $true; continue }
    if ($skipHttps) {
        if ($line -match '^# # Harbor') { $skipHttps = $false; $output.Add($line) }
        continue
    }
    if ($line -match '^hostname:') { $output.Add('hostname: host.docker.internal'); continue }
    if ($line -match '^http:') { $inHttp = $true; $output.Add($line); continue }
    if ($inHttp -and $line -match '^  port:') { $output.Add('  port: 8088'); $inHttp = $false; continue }
    if ($line -match '^harbor_admin_password:') { $output.Add("harbor_admin_password: $($values.HARBOR_ADMIN_PASSWORD)"); continue }
    if ($line -match '^database:') { $inDatabase = $true; $output.Add($line); continue }
    if ($inDatabase -and $line -match '^  password:') { $output.Add("  password: $($values.HARBOR_DB_PASSWORD)"); continue }
    if ($inDatabase -and $line -match '^[A-Za-z_]') { $inDatabase = $false }
    if ($line -match '^data_volume:') { $output.Add('data_volume: /data'); continue }
    $output.Add($line)
}
[IO.File]::WriteAllLines((Join-Path $harborDirectory 'harbor.yml'), $output)

$inputDirectory = Join-Path $harborDirectory 'input'
$configDirectory = Join-Path $harborDirectory 'common/config'
New-Item -ItemType Directory -Force -Path $inputDirectory, $configDirectory | Out-Null
Copy-Item (Join-Path $harborDirectory 'harbor.yml') (Join-Path $inputDirectory 'harbor.yml') -Force

docker run --rm --privileged `
    --volume "$($inputDirectory.Replace('\', '/')):/input" `
    --volume "/data:/data" `
    --volume "$($harborDirectory.Replace('\', '/')):/compose_location" `
    --volume "$($configDirectory.Replace('\', '/')):/config" `
    --volume "/:/hostfs/" `
    "goharbor/prepare:$version" prepare --with-trivy
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose -f (Join-Path $harborDirectory 'docker-compose.yml') up --detach
exit $LASTEXITCODE
