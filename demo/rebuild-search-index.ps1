[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$AccessToken,

    [ValidateRange(1, 9999)]
    [int]$SchemaVersion = 2,

    [ValidateRange(1, 200)]
    [int]$PageSize = 100,

    [ValidatePattern('^https?://')]
    [string]$GatewayBaseUrl = 'http://localhost:9080',

    [switch]$SkipActivation
)

$ErrorActionPreference = 'Stop'
$baseUrl = $GatewayBaseUrl.TrimEnd('/')
$headers = @{
    Authorization = "Bearer $AccessToken"
    'X-Correlation-ID' = [guid]::NewGuid().ToString()
}
$ids = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal)

function Invoke-RecoveryApi {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('GET', 'POST')][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body
    )
    $parameters = @{
        Method = $Method
        Uri = "$baseUrl$Path"
        Headers = $headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    Invoke-RestMethod @parameters
}

function Send-ProjectionBatch {
    param(
        [Parameter(Mandatory = $true)][guid]$RunId,
        [Parameter(Mandatory = $true)][object[]]$Records
    )
    if ($Records.Count -eq 0) { return }
    foreach ($record in $Records) {
        if (-not $ids.Add([string]$record.id)) {
            throw "Duplicate deterministic projection id returned by owners: $($record.id)"
        }
    }
    Invoke-RecoveryApi -Method POST -Path "/api/v1/admin/search-rebuilds/$RunId/records" `
        -Body @{ records = @($Records) } | Out-Null
}

$created = Invoke-RecoveryApi -Method POST -Path '/api/v1/admin/search-rebuilds' `
    -Body @{ schemaVersion = $SchemaVersion }
$runId = [guid]$created.runId

try {
    $page = 0
    do {
        $response = Invoke-RecoveryApi -Method GET `
            -Path "/api/v1/admin/search-projections/pre-authorizations?page=$page&size=$PageSize"
        $records = @($response.content | ForEach-Object {
            [ordered]@{
                id = $_.id
                type = $_.type
                sourceId = $_.sourceId
                preAuthorizationId = $_.preAuthorizationId
                memberId = $_.memberId
                providerId = $_.providerId
                policyNumber = $_.policyNumber
                serviceCode = $_.serviceCode
                status = $_.status
                invoiceStatus = $null
                invoiceNumber = $null
                amount = $_.amount
                approvedAmount = $_.approvedAmount
                paidAmount = $null
                currency = $_.currency
                reason = $_.reason
                sourceRevision = $_.sourceRevision
                occurredAt = $_.occurredAt
            }
        })
        Send-ProjectionBatch -RunId $runId -Records $records
        $page++
    } while ($page -lt [int]$response.totalPages)

    $page = 0
    do {
        $response = Invoke-RecoveryApi -Method GET `
            -Path "/api/v1/admin/search-projections/claims?page=$page&size=$PageSize"
        $records = @($response.content | ForEach-Object {
            [ordered]@{
                id = "CLAIM:$($_.claimId)"
                type = 'CLAIM'
                sourceId = $_.claimId
                preAuthorizationId = $_.preAuthorizationId
                memberId = $_.memberId
                providerId = $_.providerId
                policyNumber = $_.policyNumber
                serviceCode = $_.serviceCode
                status = $_.claimStatus
                invoiceStatus = $_.invoiceStatus
                invoiceNumber = $_.invoiceNumber
                amount = $_.claimedAmount
                approvedAmount = $_.approvedAmount
                paidAmount = $_.paidAmount
                currency = $_.currency
                reason = $null
                sourceRevision = $_.sourceRevision
                occurredAt = $_.occurredAt
            }
        })
        Send-ProjectionBatch -RunId $runId -Records $records
        $page++
    } while ($page -lt [int]$response.totalPages)

    if ($SkipActivation) {
        [pscustomobject]@{
            runId = $runId
            candidateIndex = $created.candidateIndex
            predecessorIndex = $created.predecessorIndex
            status = 'PREPARING'
            distinctDocuments = $ids.Count
        }
        return
    }

    $activated = Invoke-RecoveryApi -Method POST `
        -Path "/api/v1/admin/search-rebuilds/$runId/activation" `
        -Body @{ expectedDocumentCount = $ids.Count }
    [pscustomobject]@{
        runId = $runId
        candidateIndex = $activated.candidateIndex
        predecessorIndex = $activated.predecessorIndex
        status = $activated.status
        distinctDocuments = $ids.Count
    }
}
catch {
    Write-Error "Search rebuild $runId failed before activation. Candidate '$($created.candidateIndex)' was retained for inspection. $($_.Exception.Message)"
}
