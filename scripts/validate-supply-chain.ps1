[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

docker compose -f (Join-Path $root 'infra/cicd/compose.artifacts.yaml') config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Nexus Compose model is invalid.' }

$staging = (kubectl kustomize (Join-Path $root 'deploy/gitops/environments/staging')) -join "`n"
if ($LASTEXITCODE -ne 0 -or $staging -notmatch 'host\.minikube\.internal:8088/health-insurance') {
    throw 'Staging GitOps overlay does not render the local Harbor contract.'
}
if (($staging | Select-String -Pattern 'image: .*:[0-9a-f]{40}' -AllMatches).Matches.Count -lt 6) {
    throw 'Staging GitOps overlay must use immutable full Git SHA image tags.'
}

$argo = (kubectl kustomize (Join-Path $root 'deploy/gitops/argocd')) -join "`n"
if ($LASTEXITCODE -ne 0 -or $argo -notmatch 'kind: Application' -or $argo -notmatch 'kind: AppProject') {
    throw 'Argo CD resources do not render.'
}

$jenkinsfile = Get-Content (Join-Path $root 'Jenkinsfile') -Raw
foreach ($control in @('nexus-publisher', 'harbor-publisher', '${GIT_COMMIT}', 'PUBLISH_ARTIFACTS')) {
    if (-not $jenkinsfile.Contains($control)) { throw "Missing supply-chain control: $control" }
}

if ($jenkinsfile -match '(?i):latest') { throw 'The publication pipeline must not use latest tags.' }
Write-Host 'Nexus publication contract: OK'
Write-Host 'Harbor immutable-tag contract: OK'
Write-Host 'Argo CD restricted GitOps resources: OK'
