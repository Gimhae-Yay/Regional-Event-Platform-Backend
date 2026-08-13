[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('reservation-hold', 'qr-checkin', 'manual-checkin')]
    [string] $Scenario,

    [Parameter(Mandatory = $true)]
    [string[]] $DatabaseArguments,

    [string] $DatabaseCommand = 'mysql',
    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://localhost:8080',
    [int] $Vus = 0,
    [string] $MaxDuration = '10s',
    [string] $SummaryDirectory = 'performance/k6/results'
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$seedPath = Join-Path $k6Root 'seed/k6-local.seed.sql'
$verifierPath = Join-Path $k6Root 'verify-concurrency.sql'
$password = 'Password1!'
$visitorEmails = @(
    'k6-visitor@example.com',
    'k6-qr-visitor@example.com',
    'k6-manual-visitor@example.com',
    'k6-concurrency-visitor-04@example.com',
    'k6-concurrency-visitor-05@example.com',
    'k6-concurrency-visitor-06@example.com',
    'k6-concurrency-visitor-07@example.com',
    'k6-concurrency-visitor-08@example.com',
    'k6-concurrency-visitor-09@example.com',
    'k6-concurrency-visitor-10@example.com'
)

function Invoke-DatabaseSql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql,

        [Parameter(Mandatory = $true)]
        [string] $Context
    )

    $Sql | & $DatabaseCommand @DatabaseArguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Context failed with exit code $LASTEXITCODE."
    }
}

function Get-AccessToken {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email
    )

    $body = @{
        email = $Email
        password = $password
    } | ConvertTo-Json -Compress
    $response = Invoke-WebRequest `
        -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/login" `
        -Method Post `
        -ContentType 'application/json' `
        -Headers @{ Accept = 'application/json' } `
        -Body $body `
        -UseBasicParsing
    $authorization = [string] $response.Headers['Authorization']
    if (-not $authorization.StartsWith('Bearer ')) {
        throw "Login response for $Email did not include a Bearer Authorization header."
    }
    return $authorization
}

function Set-RunEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Values
    )

    $previous = @{}
    foreach ($entry in $Values.GetEnumerator()) {
        $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string] $entry.Value, 'Process')
    }
    return $previous
}

function Restore-RunEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Previous
    )

    foreach ($entry in $Previous.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

if ($Vus -eq 0) {
    $Vus = if ($Scenario -eq 'reservation-hold') { 10 } else { 2 }
}
if ($Vus -lt 2) {
    throw 'Vus must be greater than or equal to 2.'
}
if ($Scenario -eq 'reservation-hold' -and $Vus -gt $visitorEmails.Count) {
    throw "reservation-hold supports at most $($visitorEmails.Count) VUs with the current seed."
}

Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 $seedPath) -Context 'k6 seed'

$scenarioFile = $null
$environment = @{
    PERF_BASE_URL = $BaseUrl.TrimEnd('/')
    PERF_SUMMARY_DIRECTORY = $SummaryDirectory
    PERF_SUMMARY_BASENAME = "$Scenario-$([DateTimeOffset]::UtcNow.ToString('yyyyMMddHHmmss'))"
}

switch ($Scenario) {
    'reservation-hold' {
        $tokens = $visitorEmails[0..($Vus - 1)] | ForEach-Object { Get-AccessToken -Email $_ }
        $scenarioFile = Join-Path $k6Root 'scenarios/reservation-hold-concurrency.js'
        $environment.PERF_RESERVATION_HOLD_CONCURRENCY_VUS = $Vus
        $environment.PERF_RESERVATION_HOLD_CONCURRENCY_DURATION = $MaxDuration
        $environment.PERF_RESERVATION_HOLD_SESSION_ID = '900003'
        $environment.PERF_VISITOR_ACCESS_TOKENS = $tokens -join ','
    }
    'qr-checkin' {
        $operatorToken = Get-AccessToken -Email 'k6-operator@example.com'
        $scenarioFile = Join-Path $k6Root 'scenarios/qr-checkin-concurrency.js'
        $environment.PERF_QR_CHECKIN_CONCURRENCY_VUS = $Vus
        $environment.PERF_QR_CHECKIN_CONCURRENCY_DURATION = $MaxDuration
        $environment.PERF_RESERVATION_ID = '900001'
        $environment.PERF_VISITOR_ACCESS_TOKENS = Get-AccessToken -Email 'k6-qr-visitor@example.com'
        $environment.PERF_OPERATOR_ACCESS_TOKENS = $operatorToken
    }
    'manual-checkin' {
        $operatorToken = Get-AccessToken -Email 'k6-operator@example.com'
        $scenarioFile = Join-Path $k6Root 'scenarios/manual-checkin-concurrency.js'
        $environment.PERF_MANUAL_CHECKIN_CONCURRENCY_VUS = $Vus
        $environment.PERF_MANUAL_CHECKIN_CONCURRENCY_DURATION = $MaxDuration
        $environment.PERF_RESERVATION_NO = 'K6MN20260806000001'
        $environment.PERF_OPERATOR_ACCESS_TOKENS = $operatorToken
    }
}

$previousEnvironment = Set-RunEnvironment -Values $environment
$k6ExitCode = 1
try {
    & $K6Command run $scenarioFile
    $k6ExitCode = $LASTEXITCODE
} finally {
    Restore-RunEnvironment -Previous $previousEnvironment
}

$verifierSql = @"
SET @perf_scenario = '$Scenario';
SET @perf_expected_requests = $Vus;
$(Get-Content -Raw -Encoding UTF8 $verifierPath)
"@
$verifierExitCode = 0
try {
    Invoke-DatabaseSql -Sql $verifierSql -Context 'concurrency DB invariant verification'
} catch {
    $verifierExitCode = 1
    Write-Warning $_.Exception.Message
}

if ($k6ExitCode -ne 0 -or $verifierExitCode -ne 0) {
    throw "Concurrency run failed. k6 exit code=$k6ExitCode, DB verifier exit code=$verifierExitCode."
}

Write-Host "Concurrency run passed. scenario=$Scenario, VUs=$Vus"
