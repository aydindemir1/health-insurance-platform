[CmdletBinding()]
param(
    [string]$HospitalToken = $env:DEMO_HOSPITAL_TOKEN,
    [string]$InsuranceToken = $env:DEMO_INSURANCE_TOKEN,
    [string]$ClaimApproverToken = $env:DEMO_CLAIM_APPROVER_TOKEN,
    [string]$PolicyBaseUrl = "http://localhost:8082/api/v1",
    [string]$AuthorizationBaseUrl = "http://localhost:8081/api/v1",
    [string]$ClaimsBaseUrl = "http://localhost:8083/api/v1",
    [string]$SearchBaseUrl = "http://localhost:8084/api/v1",
    [switch]$VerifyNotificationDelivery,
    [string]$RunId = (Get-Date -Format "yyyyMMddHHmmss")
)

$ErrorActionPreference = "Stop"

foreach ($required in @{
    HospitalToken = $HospitalToken
    InsuranceToken = $InsuranceToken
    ClaimApproverToken = $ClaimApproverToken
}.GetEnumerator()) {
    if ([string]::IsNullOrWhiteSpace($required.Value)) {
        throw "$($required.Key) is required. Pass it as a parameter or use the corresponding DEMO_*_TOKEN environment variable."
    }
}

$data = Get-Content -LiteralPath (Join-Path $PSScriptRoot "demo-data.json") -Raw | ConvertFrom-Json
$policyNumber = "$($data.policy.numberPrefix)-$RunId"

function Invoke-DemoApi {
    param(
        [Parameter(Mandatory)] [ValidateSet("GET", "POST")] [string]$Method,
        [Parameter(Mandatory)] [string]$Uri,
        [Parameter(Mandatory)] [string]$Token,
        [object]$Body
    )
    $arguments = @{
        Method = $Method
        Uri = $Uri
        Headers = @{
            Authorization = "Bearer $Token"
            "X-Correlation-ID" = "demo-$RunId-$([guid]::NewGuid().ToString('N'))"
        }
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $arguments.Body = $Body | ConvertTo-Json -Depth 10
    }
    Invoke-RestMethod @arguments
}

function New-PreAuthorization {
    param([object]$Definition)
    Invoke-DemoApi -Method POST -Uri "$AuthorizationBaseUrl/pre-authorizations" `
        -Token $HospitalToken -Body @{
            memberId = $data.memberId
            policyNumber = $policyNumber
            serviceCode = $Definition.serviceCode
            diagnosisCode = $Definition.diagnosisCode
            requestedAmount = $Definition.amount
            currency = "TRY"
        }
}

function Wait-EventDrivenClaim {
    param([Parameter(Mandatory)] [string]$PreAuthorizationId)
    $deadline = (Get-Date).AddSeconds(20)
    do {
        try {
            return Invoke-DemoApi -Method GET `
                -Uri "$ClaimsBaseUrl/claims/by-pre-authorization/$PreAuthorizationId" `
                -Token $HospitalToken
        }
        catch {
            if ($_.Exception.Response.StatusCode.value__ -ne 404) { throw }
            Start-Sleep -Milliseconds 250
        }
    } while ((Get-Date) -lt $deadline)
    throw "Kafka event did not create a claim for pre-authorization $PreAuthorizationId within 20 seconds."
}

function Wait-NotificationDelivery {
    param([Parameter(Mandatory)] [string]$PreAuthorizationId)

    if (-not $VerifyNotificationDelivery) {
        return "NOT_VERIFIED"
    }

    $composeRoot = Split-Path -Parent $PSScriptRoot
    $databaseUserOutput = & docker compose --project-directory $composeRoot `
        exec -T notification-worker-db printenv POSTGRES_USER
    $databaseUser = if ($null -eq $databaseUserOutput) { "" } else { ($databaseUserOutput | Out-String).Trim() }
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($databaseUser)) {
        throw "Could not resolve the Notification Worker database user from Docker Compose."
    }

    $deadline = (Get-Date).AddSeconds(30)
    do {
        $statusOutput = & docker compose --project-directory $composeRoot `
            exec -T notification-worker-db psql -U $databaseUser -d notification_worker `
            -Atc "select status from notification_deliveries where business_reference_id = '$PreAuthorizationId' order by received_at desc limit 1"
        if ($LASTEXITCODE -ne 0) {
            throw "Could not query notification delivery for pre-authorization $PreAuthorizationId."
        }
        $status = if ($null -eq $statusOutput) { "" } else { ($statusOutput | Out-String).Trim() }
        if ($status -eq "DELIVERED") {
            return $status
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)

    throw "RabbitMQ notification was not delivered for pre-authorization $PreAuthorizationId within 30 seconds."
}

function Wait-SearchProjection {
    param([Parameter(Mandatory)] [string]$PolicyNumber)
    $escapedPolicyNumber = [uri]::EscapeDataString($PolicyNumber)
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $result = Invoke-DemoApi -Method GET `
            -Uri "$SearchBaseUrl/search?q=$escapedPolicyNumber&page=0&size=50" `
            -Token $InsuranceToken
        if ($result.totalElements -ge 4) {
            return $result
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Elasticsearch did not expose the expected projections for policy $PolicyNumber within 30 seconds."
}

$policy = Invoke-DemoApi -Method POST -Uri "$PolicyBaseUrl/policies" `
    -Token $InsuranceToken -Body @{
        policyNumber = $policyNumber
        memberId = $data.memberId
        validFrom = $data.policy.validFrom
        validUntil = $data.policy.validUntil
        coverages = $data.policy.coverages
    }

$pending = New-PreAuthorization $data.preAuthorizations.pending
$rejected = New-PreAuthorization $data.preAuthorizations.rejected
$rejected = Invoke-DemoApi -Method POST `
    -Uri "$AuthorizationBaseUrl/pre-authorizations/$($rejected.id)/rejection" `
    -Token $InsuranceToken -Body @{ reason = "Synthetic demo: supporting document is incomplete" }
$rejectedNotificationStatus = Wait-NotificationDelivery $rejected.id

$settledAuthorization = New-PreAuthorization $data.preAuthorizations.settledClaim
$settledAuthorization = Invoke-DemoApi -Method POST `
    -Uri "$AuthorizationBaseUrl/pre-authorizations/$($settledAuthorization.id)/approval" `
    -Token $InsuranceToken -Body @{ reason = "Synthetic demo: policy and medical rules verified" }
$settledNotificationStatus = Wait-NotificationDelivery $settledAuthorization.id
$settled = Wait-EventDrivenClaim $settledAuthorization.id
$null = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims/$($settled.claim.id)/review" `
    -Token $ClaimApproverToken
$settled = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims/$($settled.claim.id)/approval" `
    -Token $ClaimApproverToken -Body @{ amount = 2000.00; currency = "TRY" }
$settledInvoice = Invoke-DemoApi -Method POST `
    -Uri "$ClaimsBaseUrl/invoices/$($settled.invoice.id)/dispute-resolution" `
    -Token $InsuranceToken -Body @{ amount = 2000.00; currency = "TRY" }
$settledInvoice = Invoke-DemoApi -Method POST `
    -Uri "$ClaimsBaseUrl/invoices/$($settled.invoice.id)/payments" `
    -Token $InsuranceToken -Body @{
        paymentReference = "PAY-DEMO-1-$RunId"
        amount = 750.00
        currency = "TRY"
    }
$settledInvoice = Invoke-DemoApi -Method POST `
    -Uri "$ClaimsBaseUrl/invoices/$($settled.invoice.id)/payments" `
    -Token $InsuranceToken -Body @{
        paymentReference = "PAY-DEMO-2-$RunId"
        amount = 1250.00
        currency = "TRY"
    }

$disputedAuthorization = New-PreAuthorization $data.preAuthorizations.disputedClaim
$disputedAuthorization = Invoke-DemoApi -Method POST `
    -Uri "$AuthorizationBaseUrl/pre-authorizations/$($disputedAuthorization.id)/approval" `
    -Token $InsuranceToken -Body @{ reason = "Synthetic demo: approved for claim submission" }
$disputedNotificationStatus = Wait-NotificationDelivery $disputedAuthorization.id
$disputed = Wait-EventDrivenClaim $disputedAuthorization.id
$null = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims/$($disputed.claim.id)/review" `
    -Token $ClaimApproverToken
$disputed = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims/$($disputed.claim.id)/approval" `
    -Token $ClaimApproverToken -Body @{ amount = 2750.00; currency = "TRY" }

$searchResult = Wait-SearchProjection $policyNumber

$summary = [ordered]@{
    dataClassification = $data.dataClassification
    runId = $RunId
    policyNumber = $policy.policyNumber
    pendingPreAuthorizationId = $pending.id
    rejectedPreAuthorizationId = $rejected.id
    rejectedNotificationStatus = $rejectedNotificationStatus
    settledPreAuthorizationId = $settledAuthorization.id
    settledNotificationStatus = $settledNotificationStatus
    settledClaimId = $settled.claim.id
    settledInvoiceId = $settled.invoice.id
    settledInvoiceStatus = $settledInvoice.status
    disputedPreAuthorizationId = $disputedAuthorization.id
    disputedNotificationStatus = $disputedNotificationStatus
    disputedClaimId = $disputed.claim.id
    disputedInvoiceId = $disputed.invoice.id
    disputedInvoiceStatus = $disputed.invoice.status
    indexedOperationsRecords = $searchResult.totalElements
}

$summary | ConvertTo-Json
