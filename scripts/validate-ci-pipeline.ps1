[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jenkinsfile = Get-Content (Join-Path $root 'Jenkinsfile') -Raw
$sonar = Get-Content (Join-Path $root 'sonar-project.properties') -Raw
$backendCi = Get-Content (Join-Path $root '.github/workflows/backend-ci.yml') -Raw
$frontendCi = Get-Content (Join-Path $root '.github/workflows/frontend-ci.yml') -Raw
$gatewayCi = Get-Content (Join-Path $root '.github/workflows/gateway-ci.yml') -Raw

$requiredPipelineTokens = @(
    "agent { label 'java21-node24-docker' }",
    "withSonarQubeEnv('health-sonarqube')",
    'waitForQualityGate abortPipeline: true',
    "stage('Prime Maven runtime')",
    'npm ci',
    'npm run lint',
    'npm test',
    'npm run build',
    "credentialsId: 'nexus-publisher'",
    "credentialsId: 'harbor-publisher'",
    'params.PUBLISH_ARTIFACTS',
    '${GIT_COMMIT}'
)

$services = @(
    'authorization-service',
    'policy-service',
    'claims-billing-service',
    'notification-worker',
    'search-service'
)

foreach ($token in $requiredPipelineTokens) {
    if (-not $jenkinsfile.Contains($token)) {
        throw "Jenkinsfile is missing required control: $token"
    }
}

foreach ($service in $services) {
    if (-not $jenkinsfile.Contains("services/$service")) {
        throw "Jenkinsfile does not verify $service"
    }
    if (-not $sonar.Contains("services/$service/src/main/java")) {
        throw "SonarQube sources do not include $service"
    }
}

$githubWorkflows = @($backendCi, $frontendCi, $gatewayCi)
foreach ($workflow in $githubWorkflows) {
    foreach ($control in @('permissions:', 'contents: read', 'concurrency:',
            'cancel-in-progress: true', 'timeout-minutes:', '$GITHUB_SHA')) {
        if (-not $workflow.Contains($control)) {
            throw "GitHub Actions workflow is missing required control: $control"
        }
    }
}

if (-not $frontendCi.Contains('npm run build:budget')) {
    throw 'Frontend CI must enforce the production bundle budget.'
}

$apisixDigest = 'apache/apisix:3.18.0-debian@sha256:84e6b5e787e9f889ebff88161cb9a16599bafcffa236c6b54c7f779a0655940d'
if (-not $gatewayCi.Contains($apisixDigest)) {
    throw 'Gateway CI must pin the verified APISIX image digest.'
}

if ($jenkinsfile -match '(?i)(password|token|secret)\s*=\s*["''][^"'']+["'']') {
    throw 'Jenkinsfile appears to contain a literal credential.'
}

if ($sonar -match '(?im)^sonar\.(login|token|password)\s*=') {
    throw 'SonarQube credentials must not be stored in sonar-project.properties.'
}

$mavenSettings = Get-Content (Join-Path $root '.jenkins/maven-settings.xml') -Raw
if (-not $mavenSettings.Contains('${env.NEXUS_USERNAME}') -or
    -not $mavenSettings.Contains('${env.NEXUS_PASSWORD}')) {
    throw 'Maven settings must resolve Nexus credentials from the Jenkins environment.'
}

Write-Host 'Jenkins stages: OK (backend, frontend, SonarQube, blocking Quality Gate)'
Write-Host 'GitHub Actions: OK (least privilege, concurrency, timeout, revision trace, bundle budget, APISIX digest)'
Write-Host 'Credential policy: OK (no committed Jenkins/SonarQube credential values)'
