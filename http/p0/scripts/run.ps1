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
    throw "P0 preparation script failed. (exit code: $LASTEXITCODE)"
}

if ($PrepareOnly) {
    exit 0
}

Wait-ForApplication

$variables = @{
    p0RegionAdminAccessToken = Get-AccessToken -Email 'p0-region-admin@example.test' -Password $password
    p0OtherRegionAdminAccessToken = Get-AccessToken -Email 'p0-other-region-admin@example.test' -Password $password
    p0OperatorAccessToken = Get-AccessToken -Email 'p0-operator@example.test' -Password $password
    p0OtherOperatorAccessToken = Get-AccessToken -Email 'p0-other-operator@example.test' -Password $password
    p0VisitorAccessToken = Get-AccessToken -Email 'p0-visitor@example.test' -Password $password
    p0OtherVisitorAccessToken = Get-AccessToken -Email 'p0-other-visitor@example.test' -Password $password
    p0PublishedContentId = '900001'
    p0ScheduledSessionId = '910001'
    p0SoldOutContentId = '900001'
    p0SoldOutSessionId = '910002'
    p0StartedSessionId = '910003'
    p0ExpiredHoldId = '940002'
    p0ReservationId = '930001'
    p0CheckedInReservationId = '930003'
    p0QrReservationId = '930006'
    p0CancelledReservationNo = 'RLOCALCANCEL'
    p0CompletedSessionReservationNo = 'RLOCALCOMPLETED'
    p0ManualReservationNo = 'RLOCALMANUAL2'
    p0VisitId = '950001'
    p0OtherVisitId = '950002'
    p0ReviewId = '960004'
    p0OtherReviewId = '960001'
    p0QrExceptionId = '990001'
}

$httpFiles = Get-ChildItem -LiteralPath $httpDirectory -Filter '*.http' -File | Sort-Object Name
if ($httpFiles.Count -eq 0) {
    throw "P0 HTTP files were not found: $httpDirectory"
}

$dockerArguments = @('run', '--rm', '-v', "${projectRoot}:/workdir", '-w', '/workdir', 'jetbrains/intellij-http-client', '-D', '-L', 'BASIC', '--no-progress')
foreach ($entry in $variables.GetEnumerator()) {
    $dockerArguments += '-V'
    $dockerArguments += "$($entry.Key)=$($entry.Value)"
}
foreach ($httpFile in $httpFiles) {
    $dockerArguments += "http/p0/$($httpFile.Name)"
}

Push-Location $projectRoot
try {
    & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "P0 HTTP scenario execution failed. (exit code: $LASTEXITCODE)"
    }
}
finally {
    Pop-Location
    $httpClient.Dispose()
}
