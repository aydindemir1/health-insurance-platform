[CmdletBinding()]
param(
    [switch]$PublishArtifacts,
    [string]$NexusUrl = 'http://nexus:8081',
    [string]$HarborRegistry = 'host.docker.internal:8088',
    [string]$HarborApiUrl = 'http://host.docker.internal:8088',
    [ValidateSet('High', 'Medium', 'Low')]
    [string]$HarborMaxAllowedSeverity = 'High'
)

$ErrorActionPreference = 'Stop'
$directory = $PSScriptRoot
$environmentFile = Join-Path $directory '.env'
$jobFile = Join-Path $directory 'jenkins/job.xml'
$jobName = 'health-insurance-platform'
$jenkinsUrl = 'http://localhost:8086'

$values = @{}
Get-Content $environmentFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
}

$pair = "$($values.JENKINS_ADMIN_USERNAME):$($values.JENKINS_ADMIN_PASSWORD)"
$client = [System.Net.Http.HttpClient]::new()
$encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Basic', $encoded)
$crumb = $client.GetStringAsync("$jenkinsUrl/crumbIssuer/api/json").Result | ConvertFrom-Json
$client.DefaultRequestHeaders.Add($crumb.crumbRequestField, $crumb.crumb)

$xml = Get-Content $jobFile -Raw
$existing = $client.GetAsync("$jenkinsUrl/job/$jobName/api/json").Result
if ($existing.IsSuccessStatusCode) {
    $target = "$jenkinsUrl/job/$jobName/config.xml"
} else {
    $target = "$jenkinsUrl/createItem?name=$jobName"
}

$content = [System.Net.Http.StringContent]::new($xml, [Text.Encoding]::UTF8, 'application/xml')
$configured = $client.PostAsync($target, $content).Result
if (-not $configured.IsSuccessStatusCode) {
    throw "Jenkins job configuration failed: $([int]$configured.StatusCode)"
}

$parameters = [ordered]@{
    PUBLISH_ARTIFACTS = $PublishArtifacts.IsPresent.ToString().ToLowerInvariant()
    NEXUS_URL = $NexusUrl
    HARBOR_REGISTRY = $HarborRegistry
    HARBOR_API_URL = $HarborApiUrl
    HARBOR_PROJECT = 'health-insurance'
    HARBOR_MAX_ALLOWED_SEVERITY = $HarborMaxAllowedSeverity
}
$query = ($parameters.GetEnumerator() | ForEach-Object {
    "{0}={1}" -f [Uri]::EscapeDataString($_.Key), [Uri]::EscapeDataString($_.Value)
}) -join '&'
$triggered = $client.PostAsync("$jenkinsUrl/job/$jobName/buildWithParameters?$query", $null).Result
if (-not $triggered.IsSuccessStatusCode) {
    throw "Jenkins build trigger failed: $([int]$triggered.StatusCode)"
}

Write-Host "Local Jenkins pipeline queued: $jenkinsUrl/job/$jobName/"
