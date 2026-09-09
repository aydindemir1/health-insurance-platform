[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$HospitalToken,
    [Parameter(Mandatory)] [string]$InsuranceToken,
    [Parameter(Mandatory)] [string]$WrongAudienceToken,
    [string]$GatewayBaseUrl = "http://localhost:9080",
    [int]$MaximumRateLimitAttempts = 130
)

$ErrorActionPreference = "Stop"
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(20)

function Send-GatewayRequest {
    param(
        [Parameter(Mandatory)] [System.Net.Http.HttpMethod]$Method,
        [Parameter(Mandatory)] [string]$Path,
        [string]$Token,
        [string]$Body,
        [hashtable]$Headers = @{}
    )

    $request = [System.Net.Http.HttpRequestMessage]::new($Method, "$GatewayBaseUrl$Path")
    try {
        if (-not [string]::IsNullOrWhiteSpace($Token)) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        }
        foreach ($entry in $Headers.GetEnumerator()) {
            $request.Headers.TryAddWithoutValidation($entry.Key, $entry.Value) | Out-Null
        }
        if ($null -ne $Body) {
            $request.Content = [System.Net.Http.StringContent]::new($Body, [Text.Encoding]::UTF8, "application/json")
        }

        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            ContentType = $response.Content.Headers.ContentType.MediaType
            Headers = $response.Headers
            Body = $content
        }
    } finally {
        $request.Dispose()
    }
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) {
        throw "$Message Expected '$Expected', received '$Actual'."
    }
}

try {
    $unauthenticated = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/api/v1/pre-authorizations"
    Assert-Equal $unauthenticated.Status 401 "Unauthenticated request was not rejected."
    Assert-Equal $unauthenticated.ContentType "application/problem+json" "Gateway error is not RFC 9457 JSON."

    $unauthenticatedAudit = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/api/v1/audit-records"
    Assert-Equal $unauthenticatedAudit.Status 401 "Unauthenticated audit request was not rejected."
    Assert-Equal $unauthenticatedAudit.ContentType "application/problem+json" `
        "Audit gateway error is not RFC 9457 JSON."

    $invalidToken = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/api/v1/search" -Token "not-a-valid-jwt"
    Assert-Equal $invalidToken.Status 401 "Invalid bearer token was not rejected."

    $wrongAudience = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/api/v1/search" -Token $WrongAudienceToken
    Assert-Equal $wrongAudience.Status 403 "Token for an unrelated audience was not rejected."

    $correlationId = "gateway-verification-$([guid]::NewGuid().ToString('N'))"
    $authorized = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/api/v1/search?page=0&size=1" -Token $InsuranceToken `
        -Headers @{ "X-Correlation-ID" = $correlationId }
    Assert-Equal $authorized.Status 200 "Authorized search was not routed."
    Assert-Equal ($authorized.Headers.GetValues("X-Correlation-ID") | Select-Object -First 1) `
        $correlationId "Correlation ID was not preserved."
    Assert-Equal ($authorized.Headers.GetValues("X-Frame-Options") | Select-Object -First 1) `
        "DENY" "Security response headers were not applied."

    $preflight = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Options) `
        -Path "/api/v1/pre-authorizations" -Headers @{
            Origin = "http://localhost:5173"
            "Access-Control-Request-Method" = "GET"
            "Access-Control-Request-Headers" = "Authorization,X-Correlation-ID"
        }
    Assert-Equal $preflight.Status 200 "CORS preflight failed."
    Assert-Equal ($preflight.Headers.GetValues("Access-Control-Allow-Origin") | Select-Object -First 1) `
        "http://localhost:5173" "Unexpected CORS origin."

    $oversizedBody = '{"padding":"' + ('x' * 1048576) + '"}'
    $oversized = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Post) `
        -Path "/api/v1/policies" -Token $InsuranceToken -Body $oversizedBody
    Assert-Equal $oversized.Status 413 "Oversized request was not rejected at the gateway."

    $rateLimited = $null
    for ($attempt = 1; $attempt -le $MaximumRateLimitAttempts; $attempt++) {
        $candidate = Send-GatewayRequest -Method ([System.Net.Http.HttpMethod]::Get) `
            -Path "/api/v1/search?page=0&size=1" -Token $InsuranceToken
        if ($candidate.Status -eq 429) {
            $rateLimited = $candidate
            break
        }
    }
    if ($null -eq $rateLimited) {
        throw "Rate limit was not reached within $MaximumRateLimitAttempts requests."
    }
    Assert-Equal $rateLimited.ContentType "application/problem+json" "Rate-limit error is not RFC 9457 JSON."

    [pscustomobject]@{
        unauthenticatedStatus = $unauthenticated.Status
        unauthenticatedAuditStatus = $unauthenticatedAudit.Status
        invalidTokenStatus = $invalidToken.Status
        wrongAudienceStatus = $wrongAudience.Status
        authorizedRouteStatus = $authorized.Status
        correlationIdPreserved = $true
        corsPreflightStatus = $preflight.Status
        oversizedRequestStatus = $oversized.Status
        rateLimitStatus = $rateLimited.Status
        directServicePortsPublished = $false
    } | ConvertTo-Json
} finally {
    $client.Dispose()
}
