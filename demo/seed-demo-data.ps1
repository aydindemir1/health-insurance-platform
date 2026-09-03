[CmdletBinding()]
param(
    [string]$HospitalToken = $env:DEMO_HOSPITAL_TOKEN,
    [string]$InsuranceToken = $env:DEMO_INSURANCE_TOKEN,
    [string]$ClaimApproverToken = $env:DEMO_CLAIM_APPROVER_TOKEN,
    [string]$PolicyBaseUrl = "http://localhost:8082/api/v1",
    [string]$AuthorizationBaseUrl = "http://localhost:8081/api/v1",
    [string]$ClaimsBaseUrl = "http://localhost:8083/api/v1",
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
        Headers = @{ Authorization = "Bearer $Token" }
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

$settledAuthorization = New-PreAuthorization $data.preAuthorizations.settledClaim
$settledAuthorization = Invoke-DemoApi -Method POST `
    -Uri "$AuthorizationBaseUrl/pre-authorizations/$($settledAuthorization.id)/approval" `
    -Token $InsuranceToken -Body @{ reason = "Synthetic demo: policy and medical rules verified" }
$settled = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims" `
    -Token $HospitalToken -Body @{
        preAuthorizationId = $settledAuthorization.id
        invoiceNumber = "INV-DEMO-SETTLED-$RunId"
        invoicedAmount = 2400.00
        currency = "TRY"
    }
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
$disputed = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims" `
    -Token $HospitalToken -Body @{
        preAuthorizationId = $disputedAuthorization.id
        invoiceNumber = "INV-DEMO-DISPUTED-$RunId"
        invoicedAmount = 3200.00
        currency = "TRY"
    }
$null = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims/$($disputed.claim.id)/review" `
    -Token $ClaimApproverToken
$disputed = Invoke-DemoApi -Method POST -Uri "$ClaimsBaseUrl/claims/$($disputed.claim.id)/approval" `
    -Token $ClaimApproverToken -Body @{ amount = 2750.00; currency = "TRY" }

$summary = [ordered]@{
    dataClassification = $data.dataClassification
    runId = $RunId
    policyNumber = $policy.policyNumber
    pendingPreAuthorizationId = $pending.id
    rejectedPreAuthorizationId = $rejected.id
    settledPreAuthorizationId = $settledAuthorization.id
    settledClaimId = $settled.claim.id
    settledInvoiceId = $settled.invoice.id
    settledInvoiceStatus = $settledInvoice.status
    disputedPreAuthorizationId = $disputedAuthorization.id
    disputedClaimId = $disputed.claim.id
    disputedInvoiceId = $disputed.invoice.id
    disputedInvoiceStatus = $disputed.invoice.status
}

$summary | ConvertTo-Json
