[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.artifacts.yaml'
docker compose -f $composeFile up --detach
exit $LASTEXITCODE
