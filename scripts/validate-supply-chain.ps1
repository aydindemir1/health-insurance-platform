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
if (($staging | Select-String -Pattern 'name: harbor-registry' -AllMatches).Matches.Count -lt 7) {
    throw 'Every staging ServiceAccount must reference the runtime Harbor pull secret.'
}
if ($staging -notmatch 'health-insurance\.aydindemir\.dev/source-revision: [0-9a-f]{40}') {
    throw 'Staging resources must carry the promoted source revision annotation.'
}

$argo = (kubectl kustomize (Join-Path $root 'deploy/gitops/argocd')) -join "`n"
if ($LASTEXITCODE -ne 0 -or $argo -notmatch 'kind: Application' -or $argo -notmatch 'kind: AppProject') {
    throw 'Argo CD resources do not render.'
}
if ($argo -match '(?ms)namespaceResourceWhitelist:.*?group: [''"]?\*[''"]?.*?kind: [''"]?\*[''"]?') {
    throw 'Argo CD AppProject must not allow every namespace resource kind.'
}
foreach ($kind in @('ConfigMap', 'Deployment', 'HorizontalPodAutoscaler',
        'LimitRange', 'NetworkPolicy', 'PodDisruptionBudget', 'ResourceQuota',
        'Service', 'ServiceAccount')) {
    if ($argo -notmatch "kind: $kind") {
        throw "Argo CD AppProject allowlist is missing required kind: $kind"
    }
}
if ($argo -match 'kind: (Secret|Role|RoleBinding)') {
    throw 'Argo CD AppProject must not manage Secrets or namespace RBAC.'
}

$argoInstaller = Get-Content (Join-Path $root 'deploy/gitops/install-local-argocd.ps1') -Raw
foreach ($control in @("imagePullPolicy = 'IfNotPresent'", 'initialDelaySeconds = 60',
        'timeoutSeconds = 10', "@('argocd-repo-server', 'argocd-server')",
        "'reposerver.git.request.timeout' = '60s'")) {
    if (-not $argoInstaller.Contains($control)) {
        throw "Argo CD local runtime hardening is missing: $control"
    }
}

$jenkinsfile = Get-Content (Join-Path $root 'Jenkinsfile') -Raw
$harborBootstrap = Get-Content (Join-Path $root 'infra/cicd/harbor/bootstrap-local-harbor.ps1') -Raw
$harborStart = Get-Content (Join-Path $root 'infra/cicd/harbor/start-local-harbor.ps1') -Raw
foreach ($control in @('nexus-publisher', 'harbor-publisher', '${GIT_COMMIT}', 'PUBLISH_ARTIFACTS',
        'build-provenance.json', '-Dclassifiers=provenance', 'artifactSha256',
        'org.opencontainers.image.revision=${GIT_COMMIT}',
        'org.opencontainers.image.source=${GIT_URL}')) {
    if (-not $jenkinsfile.Contains($control)) { throw "Missing supply-chain control: $control" }
}

if ($jenkinsfile -match '(?i):latest') { throw 'The publication pipeline must not use latest tags.' }
foreach ($control in @('$robotDurationSeconds = 90 * 24 * 60 * 60',
        "metadata = @{ public = 'false'; auto_scan = 'false' }",
        'up --detach --no-build --force-recreate jenkins')) {
    if (-not $harborBootstrap.Contains($control)) {
        throw "Harbor bootstrap is missing required control: $control"
    }
}
foreach ($control in @('rmdir /data/secret/registry/root.crt',
        'chown 999:999 /data/database')) {
    if (-not $harborStart.Contains($control)) {
        throw "Harbor Docker Desktop preflight is missing: $control"
    }
}
Write-Host 'Nexus publication contract: OK'
Write-Host 'Harbor private-project, bounded-robot and immutable-tag contract: OK'
Write-Host 'Argo CD restricted GitOps resources: OK'
Write-Host 'Argo CD local image and probe stability controls: OK'
Write-Host 'Commit-to-artifact-to-image-to-deployment traceability contract: OK'
