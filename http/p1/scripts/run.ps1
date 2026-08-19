[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$PrepareOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$prepareScript = Join-Path $PSScriptRoot 'prepare.ps1'
$httpDirectory = Split-Path -Parent $PSScriptRoot
$password = 'Test!23456'
Add-Type -AssemblyName System.Net.Http
$httpClient = [System.Net.Http.HttpClient]::new()

function Get-AccessToken {
    param(
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$Password
    )

    $body = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
    $content = [System.Net.Http.StringContent]::new(
        $body,
        [System.Text.Encoding]::UTF8,
        'application/json'
    )
    $response = $httpClient.PostAsync("$BaseUrl/api/v1/auth/login", $content).GetAwaiter().GetResult()
    try {
        if (-not $response.IsSuccessStatusCode) {
            throw "Login request failed. status=$([int]$response.StatusCode), email=$Email"
        }

        $loginResponse = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        $accessToken = $loginResponse.data.accessToken
    }
    finally {
        $response.Dispose()
        $content.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($accessToken)) {
        throw "Access token was not found in the login response: $Email"
    }
    return $accessToken
}

function Wait-ForApplication {
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            $response = $httpClient.GetAsync("$BaseUrl/api/v1/regions").GetAwaiter().GetResult()
            if ($response.IsSuccessStatusCode) {
                $response.Dispose()
                return
            }
            $response.Dispose()
        }
        catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "Local application did not respond: $BaseUrl/api/v1/regions"
}

& $prepareScript
if ($LASTEXITCODE -ne 0) {
    throw "P1 preparation script failed. (exit code: $LASTEXITCODE)"
}

if ($PrepareOnly) {
    exit 0
}

Wait-ForApplication

$variables = @{
    p1RegionAdminAccessToken = Get-AccessToken -Email 'p0-region-admin@example.test' -Password $password
    p1OperatorAccessToken = Get-AccessToken -Email 'p0-operator@example.test' -Password $password
    p1VisitorAccessToken = Get-AccessToken -Email 'p0-visitor@example.test' -Password $password
    p1OtherVisitorAccessToken = Get-AccessToken -Email 'p0-other-visitor@example.test' -Password $password
    p1PlatformAdminAccessToken = Get-AccessToken -Email 'p1-platform-admin@example.test' -Password $password
    p1SuperAdminAccessToken = Get-AccessToken -Email 'p1-super-admin@example.test' -Password $password
    p1RegionId = '900001'
    p1PublishedContentId = '900001'
    p1PublishedStampbookRewardCouponPolicyId = '900101'
    p1PublishedStampbookId = '900201'
    p1PendingStampbookId = '900202'
    p1PublishedMissionRewardCouponPolicyId = '900102'
    p1PublishedMissionId = '900501'
    p1PendingMissionId = '900502'
    p1MissionParticipationId = '900601'
    p1VisitorVisitId = '950001'
    p1VisitorCouponId = '900701'
    p1PaymentHoldId = '941001'
    p1PaymentId = '961001'
    p1PaidReservationId = '931001'
    p1RefundId = '981001'
    p1PaymentDiscrepancyId = '971001'
    p1RefundFailureId = '981001'
    p1RegionAdminCandidateUserId = '12'
    p1PortOneWebhookId = 'p1-local-webhook'
    p1PortOneWebhookTimestamp = '2026-09-01T00:00:00Z'
    p1PortOneWebhookSignature = 'local-signature-not-for-production'
    p1PortOneStoreId = 'local-store'
    p1PortOneOrderId = 'p1-local-order-961001'
    p1PortOneTransactionId = 'p1-local-transaction-961001'
}

$httpFiles = Get-ChildItem -LiteralPath $httpDirectory -Filter '*.http' -File | Sort-Object Name
if ($httpFiles.Count -eq 0) {
    throw "P1 HTTP files were not found: $httpDirectory"
}

$dockerArguments = @('run', '--rm', '-v', "${projectRoot}:/workdir", '-w', '/workdir', 'jetbrains/intellij-http-client', '-D', '-L', 'BASIC', '--no-progress')
foreach ($entry in $variables.GetEnumerator()) {
    $dockerArguments += '-V'
    $dockerArguments += "$($entry.Key)=$($entry.Value)"
}
foreach ($httpFile in $httpFiles) {
    $dockerArguments += "http/p1/$($httpFile.Name)"
}

Push-Location $projectRoot
try {
    & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "P1 HTTP scenario execution failed. (exit code: $LASTEXITCODE)"
    }
}
finally {
    Pop-Location
    $httpClient.Dispose()
}
