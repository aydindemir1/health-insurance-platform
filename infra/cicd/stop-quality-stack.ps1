[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$directory = $PSScriptRoot
docker compose --env-file (Join-Path $directory '.env') `
    -f (Join-Path $directory 'compose.quality.yaml') stop
exit $LASTEXITCODE
