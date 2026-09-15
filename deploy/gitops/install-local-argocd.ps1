[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Context
)

$ErrorActionPreference = 'Stop'
$current = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or $current -ne $Context) {
    throw "Refusing installation: current context '$current' does not equal '$Context'."
}
if ($current -match 'prod|production') { throw 'Production-like contexts are forbidden.' }

kubectl get nodes --context $Context | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'The selected Kubernetes cluster is not reachable.' }

$namespace = kubectl create namespace argocd --dry-run=client -o yaml
$namespace | kubectl apply --context $Context -f -
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl apply --context $Context --server-side --force-conflicts -n argocd `
    -f 'https://raw.githubusercontent.com/argoproj/argo-cd/v3.5.2/manifests/install.yaml'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$commandParametersPatch = @{
    data = @{ 'reposerver.git.request.timeout' = '60s' }
} | ConvertTo-Json -Depth 4 -Compress
kubectl patch --context $Context -n argocd configmap argocd-cmd-params-cm `
    --type merge --patch $commandParametersPatch | Out-Null
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$workloads = kubectl get --context $Context -n argocd deployments,statefulsets -o json | ConvertFrom-Json
foreach ($workload in $workloads.items) {
    $containers = @($workload.spec.template.spec.containers | ForEach-Object {
        @{ name = $_.name; imagePullPolicy = 'IfNotPresent' }
    })
    $pullPolicyPatch = @{
        spec = @{ template = @{ spec = @{ containers = $containers } } }
    } | ConvertTo-Json -Depth 8 -Compress
    kubectl patch --context $Context -n argocd `
        $workload.kind.ToLowerInvariant() $workload.metadata.name `
        --type strategic --patch $pullPolicyPatch | Out-Null
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

foreach ($deployment in @('argocd-repo-server', 'argocd-server')) {
    $probePatch = @{
        spec = @{ template = @{ spec = @{ containers = @(@{
            name = $deployment
            readinessProbe = @{ timeoutSeconds = 5; failureThreshold = 6 }
            livenessProbe = @{ initialDelaySeconds = 60; timeoutSeconds = 10; failureThreshold = 6 }
        }) } } }
    } | ConvertTo-Json -Depth 8 -Compress
    kubectl patch --context $Context -n argocd deployment $deployment `
        --type strategic --patch $probePatch | Out-Null
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

kubectl wait --context $Context -n argocd --for=condition=Available deployment `
    --all --timeout=5m
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl apply --context $Context -k (Join-Path $PSScriptRoot 'argocd')
exit $LASTEXITCODE
