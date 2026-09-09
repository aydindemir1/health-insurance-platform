[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$AccessToken,

    [Parameter(Mandatory = $true)]
    [guid]$RunId,

    [ValidatePattern('^https?://')]
    [string]$GatewayBaseUrl = 'http://localhost:9080'
)

$ErrorActionPreference = 'Stop'
$headers = @{
    Authorization = "Bearer $AccessToken"
    'X-Correlation-ID' = [guid]::NewGuid().ToString()
}
$uri = "$($GatewayBaseUrl.TrimEnd('/'))/api/v1/admin/search-rebuilds/$RunId/rollback"
$result = Invoke-RestMethod -Method POST -Uri $uri -Headers $headers -ContentType 'application/json'
[pscustomobject]@{
    runId = $result.runId
    activeIndex = $result.predecessorIndex
    retainedCandidate = $result.candidateIndex
    status = $result.status
}
