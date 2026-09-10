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

kubectl wait --context $Context -n argocd --for=condition=Available deployment `
    --all --timeout=5m
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl apply --context $Context -k (Join-Path $PSScriptRoot 'argocd')
exit $LASTEXITCODE
