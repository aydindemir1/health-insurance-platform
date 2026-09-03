[CmdletBinding()]
param(
    [string]$AdminUsername = $env:DEMO_KEYCLOAK_ADMIN_USERNAME,
    [string]$AdminPassword = $env:DEMO_KEYCLOAK_ADMIN_PASSWORD,
    [string]$DemoUserPassword = $env:DEMO_USER_PASSWORD,
    [string]$KeycloakNetworkUrl = "http://127.0.0.1:8080",
    [string]$KeycloakPublicUrl = "http://localhost:8080",
    [string]$RunId = (Get-Date -Format "yyyyMMddHHmmss")
)

$ErrorActionPreference = "Stop"

foreach ($required in @{
    AdminUsername = $AdminUsername
    AdminPassword = $AdminPassword
    DemoUserPassword = $DemoUserPassword
}.GetEnumerator()) {
    if ([string]::IsNullOrWhiteSpace($required.Value)) {
        throw "$($required.Key) is required. Supply its DEMO_* environment variable; never store the value in the repository."
    }
}

$hostHeader = ([uri]$KeycloakPublicUrl).Authority
$adminTokenResponse = Invoke-RestMethod -Method POST `
    -Uri "$KeycloakNetworkUrl/realms/master/protocol/openid-connect/token" `
    -Headers @{ Host = $hostHeader } `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        client_id = "admin-cli"
        grant_type = "password"
        username = $AdminUsername
        password = $AdminPassword
    }
$adminHeaders = @{
    Authorization = "Bearer $($adminTokenResponse.access_token)"
    Host = $hostHeader
}
$adminBaseUrl = "$KeycloakNetworkUrl/admin/realms/health-insurance"
$seederClientId = "health-insurance-demo-seeder"

function Invoke-KeycloakAdmin {
    param(
        [Parameter(Mandatory)] [ValidateSet("GET", "POST", "PUT")] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        [object]$Body
    )
    $arguments = @{
        Method = $Method
        Uri = "$adminBaseUrl/$Path"
        Headers = $adminHeaders
    }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json"
        $arguments.Body = ConvertTo-Json -InputObject $Body -Depth 10
    }
    try {
        Invoke-RestMethod @arguments
    } catch {
        throw "Keycloak admin request failed for '$Method $Path': $($_.Exception.Message)"
    }
}

$clientConfiguration = @{
    clientId = $seederClientId
    enabled = $true
    publicClient = $true
    standardFlowEnabled = $false
    directAccessGrantsEnabled = $true
    serviceAccountsEnabled = $false
    protocolMappers = @(
        @{
            name = "provider-id"
            protocol = "openid-connect"
            protocolMapper = "oidc-usermodel-attribute-mapper"
            consentRequired = $false
            config = @{
                "user.attribute" = "providerId"
                "claim.name" = "provider_id"
                "jsonType.label" = "String"
                "id.token.claim" = "true"
                "access.token.claim" = "true"
                "userinfo.token.claim" = "true"
            }
        }
    )
}
[array]$clients = Invoke-KeycloakAdmin -Method GET -Path "clients?clientId=$seederClientId"
if ($clients.Count -eq 0) {
    Invoke-KeycloakAdmin -Method POST -Path "clients" -Body $clientConfiguration | Out-Null
    [array]$clients = Invoke-KeycloakAdmin -Method GET -Path "clients?clientId=$seederClientId"
}
$clientInternalId = ($clients | Select-Object -First 1).id
if ([string]::IsNullOrWhiteSpace($clientInternalId)) {
    throw "Keycloak did not return an identifier for the local demo seeder client."
}
Invoke-KeycloakAdmin -Method PUT -Path "clients/$clientInternalId" `
    -Body $clientConfiguration | Out-Null
[array]$clientMappers = Invoke-KeycloakAdmin -Method GET `
    -Path "clients/$clientInternalId/protocol-mappers/models"
if (-not ($clientMappers | Where-Object name -eq "provider-id")) {
    Invoke-KeycloakAdmin -Method POST `
        -Path "clients/$clientInternalId/protocol-mappers/models" `
        -Body $clientConfiguration.protocolMappers[0] | Out-Null
}

$userProfile = Invoke-KeycloakAdmin -Method GET -Path "users/profile"
if (-not ($userProfile.attributes | Where-Object name -eq "providerId")) {
    $userProfile.attributes = @($userProfile.attributes) + @{
        name = "providerId"
        displayName = "Provider ID"
        permissions = @{
            view = @("admin", "user")
            edit = @("admin")
        }
        multivalued = $false
    }
    Invoke-KeycloakAdmin -Method PUT -Path "users/profile" -Body $userProfile | Out-Null
}

function Set-DemoUser {
    param(
        [Parameter(Mandatory)] [string]$Username,
        [Parameter(Mandatory)] [string]$Role,
        [hashtable]$Attributes = @{}
    )

    [array]$users = Invoke-KeycloakAdmin -Method GET -Path "users?exact=true&username=$Username"
    $userConfiguration = @{
        username = $Username
        firstName = "Synthetic"
        lastName = "Demo"
        email = "$Username@example.invalid"
        enabled = $true
        emailVerified = $true
        requiredActions = @()
        attributes = $Attributes
    }
    if ($users.Count -eq 0) {
        Invoke-KeycloakAdmin -Method POST -Path "users" -Body $userConfiguration | Out-Null
        [array]$users = Invoke-KeycloakAdmin -Method GET -Path "users?exact=true&username=$Username"
    }
    if ($users.Count -ne 1) {
        throw "Expected exactly one local Keycloak user named $Username."
    }

    $userId = ($users | Select-Object -First 1).id
    if ([string]::IsNullOrWhiteSpace($userId)) {
        throw "Keycloak did not return an identifier for local user $Username."
    }
    Invoke-KeycloakAdmin -Method PUT -Path "users/$userId" `
        -Body $userConfiguration | Out-Null
    Invoke-KeycloakAdmin -Method PUT -Path "users/$userId/reset-password" -Body @{
        type = "password"
        value = $DemoUserPassword
        temporary = $false
    } | Out-Null

    $roleRepresentation = Invoke-KeycloakAdmin -Method GET -Path "roles/$Role"
    Invoke-KeycloakAdmin -Method POST -Path "users/$userId/role-mappings/realm" `
        -Body @($roleRepresentation) | Out-Null
}

Set-DemoUser -Username "hospital-demo" -Role "HOSPITAL_USER" -Attributes @{
    providerId = @("30000000-0000-0000-0000-000000000001")
}
Set-DemoUser -Username "insurance-demo" -Role "INSURANCE_SPECIALIST"
Set-DemoUser -Username "claim-approver-demo" -Role "CLAIM_APPROVER"

function Get-DemoAccessToken {
    param([Parameter(Mandatory)] [string]$Username)
    $response = Invoke-RestMethod -Method POST `
        -Uri "$KeycloakNetworkUrl/realms/health-insurance/protocol/openid-connect/token" `
        -Headers @{ Host = $hostHeader } `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            client_id = $seederClientId
            grant_type = "password"
            username = $Username
            password = $DemoUserPassword
        }
    $response.access_token
}

$hospitalToken = Get-DemoAccessToken -Username "hospital-demo"
$insuranceToken = Get-DemoAccessToken -Username "insurance-demo"
$claimApproverToken = Get-DemoAccessToken -Username "claim-approver-demo"

& (Join-Path $PSScriptRoot "seed-demo-data.ps1") `
    -HospitalToken $hospitalToken `
    -InsuranceToken $insuranceToken `
    -ClaimApproverToken $claimApproverToken `
    -RunId $RunId
