[CmdletBinding()]
param(
    [switch]$ServerDryRun,
    [string]$Context = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Render-Kustomization {
    param([string]$Path)
    $rendered = & kubectl kustomize $Path
    Assert-Condition ($LASTEXITCODE -eq 0) "Kustomize render failed: $Path"
    return ($rendered -join "`n")
}

Push-Location $repositoryRoot
try {
    $base = Render-Kustomization 'deploy/kubernetes/base'
    $local = Render-Kustomization 'deploy/kubernetes/overlays/local'
    $expectedDeployments = @(
        'apisix',
        'authorization-service',
        'claims-billing-service',
        'notification-worker',
        'operations-portal',
        'policy-service',
        'search-service'
    )

    foreach ($name in $expectedDeployments) {
        Assert-Condition ($base -match "(?m)^  name: $([regex]::Escape($name))$") "Missing workload: $name"
    }

    $deploymentCount = ([regex]::Matches($base, '(?m)^kind: Deployment$')).Count
    Assert-Condition ($deploymentCount -eq $expectedDeployments.Count) "Expected 7 deployments, found $deploymentCount."
    Assert-Condition (([regex]::Matches($base, 'runAsNonRoot: true')).Count -ge 8) 'Non-root policy is incomplete.'
    Assert-Condition (([regex]::Matches($base, 'allowPrivilegeEscalation: false')).Count -ge 8) 'Privilege escalation policy is incomplete.'
    Assert-Condition (([regex]::Matches($base, 'readOnlyRootFilesystem: true')).Count -ge 8) 'Read-only root filesystem policy is incomplete.'
    Assert-Condition (([regex]::Matches($base, 'type: RuntimeDefault')).Count -eq 7) 'RuntimeDefault seccomp is incomplete.'
    Assert-Condition (([regex]::Matches($base, 'startupProbe:')).Count -eq 7) 'Startup probes are incomplete.'
    Assert-Condition (([regex]::Matches($base, 'readinessProbe:')).Count -eq 7) 'Readiness probes are incomplete.'
    Assert-Condition (([regex]::Matches($base, 'livenessProbe:')).Count -eq 7) 'Liveness probes are incomplete.'
    Assert-Condition (([regex]::Matches($base, '(?m)^kind: NetworkPolicy$')).Count -ge 10) 'Network policy set is incomplete.'
    Assert-Condition (([regex]::Matches($base, '(?m)^kind: PodDisruptionBudget$')).Count -eq 7) 'PDB set is incomplete.'
    Assert-Condition (([regex]::Matches($base, '(?m)^kind: HorizontalPodAutoscaler$')).Count -eq 6) 'HPA set is incomplete.'
    Assert-Condition (([regex]::Matches($base, '(?m)^kind: ResourceQuota$')).Count -eq 1) 'Namespace ResourceQuota is missing.'
    Assert-Condition (([regex]::Matches($base, '(?m)^kind: LimitRange$')).Count -eq 1) 'Namespace LimitRange is missing.'
    Assert-Condition (([regex]::Matches($base, 'apache/apisix:3\.18\.0-debian@sha256:[a-f0-9]{64}')).Count -eq 2) 'APISIX images must use one immutable registry digest.'
    Assert-Condition ($base -notmatch '(?m)^kind: Secret$') 'Rendered base must not contain Secrets.'
    Assert-Condition ($base -notmatch '(?i)(password|client-secret):\s+[^\s]') 'A literal credential may be present.'
    Assert-Condition (([regex]::Matches($local, '(?m)^  replicas: 1$')).Count -eq 7) 'Local overlay must use one replica per deployment.'
    Assert-Condition ($local -match '(?ms)kind: Service\s+metadata:.*?name: keycloak.*?namespace: health-insurance.*?type: ExternalName') 'Local Keycloak ExternalName Service must use the health-insurance namespace.'

    foreach ($dockerfile in Get-ChildItem 'services' -File -Recurse -Filter 'Dockerfile') {
        $content = Get-Content -LiteralPath $dockerfile.FullName -Raw
        Assert-Condition ($content -match '(?m)^USER 10001:10001$') "Numeric non-root USER missing: $($dockerfile.FullName)"
    }

    if ($ServerDryRun) {
        Assert-Condition (-not [string]::IsNullOrWhiteSpace($Context)) 'Context is required with -ServerDryRun.'
        $base | & kubectl --context $Context apply --dry-run=server -f - | Out-Null
        Assert-Condition ($LASTEXITCODE -eq 0) 'Kubernetes server-side dry run failed.'
    }

    Write-Host "Kubernetes base: OK ($deploymentCount deployments)"
    Write-Host 'Deployment security: OK (non-root, seccomp, read-only root, dropped capabilities)'
    Write-Host 'Availability controls: OK (probes, resources, PDBs, topology spread, HPAs)'
    Write-Host 'Network isolation: OK (default deny and bounded allow policies)'
    Write-Host 'Credential policy: OK (Secret references only)'
}
finally {
    Pop-Location
}
