[CmdletBinding()]
param(
    [ValidateSet(
        'all',
        'auth-session',
        'public-api-response-time',
        'public-content',
        'reservation-flow',
        'reservation-hold',
        'qr-checkin',
        'manual-checkin'
    )]
    [string] $Scenario = 'all',

    [string] $DatabaseContainer = 'regional-event-perf-local-perf-mysql-1',
    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $ResultDate = (Get-Date).ToString('yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$seedPath = Join-Path $k6Root 'seed/k6-local.seed.sql'
$verifierPath = Join-Path $k6Root 'verify-concurrency.sql'
$resultDirectory = Join-Path $k6Root "results/$ResultDate"
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

$scenarioNames = if ($Scenario -eq 'all') {
    @(
        'auth-session',
        'public-api-response-time',
        'public-content',
        'reservation-flow',
        'reservation-hold',
        'qr-checkin',
        'manual-checkin'
    )
} else {
    @($Scenario)
}

function Get-ContainerDatabaseConfiguration {
    $values = @{}
    docker inspect $DatabaseContainer --format '{{range .Config.Env}}{{println .}}{{end}}' |
        ForEach-Object {
            $parts = $_ -split '=', 2
            if ($parts.Count -eq 2) {
                $values[$parts[0]] = $parts[1]
            }
        }
    foreach ($requiredName in @('MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD')) {
        if (-not $values.ContainsKey($requiredName)) {
            throw "Database container is missing $requiredName."
        }
    }
    return $values
}

function Invoke-DatabaseSql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql
    )

    $output = $Sql |
        docker exec -i -e "MYSQL_PWD=$($database.MYSQL_PASSWORD)" $DatabaseContainer `
            mysql "-u$($database.MYSQL_USER)" $database.MYSQL_DATABASE 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Database command failed with exit code $LASTEXITCODE.`n$($output -join [Environment]::NewLine)"
    }
    return @($output)
}

function Get-AccessToken {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email
    )

    $body = @{ email = $Email; password = $password } | ConvertTo-Json -Compress
    $response = Invoke-WebRequest `
        -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/login" `
        -Method Post `
        -ContentType 'application/json' `
        -Headers @{ Accept = 'application/json' } `
        -Body $body `
        -UseBasicParsing
    $accessToken = [string] (($response.Content | ConvertFrom-Json).data.accessToken)
    if ([string]::IsNullOrWhiteSpace($accessToken)) {
        throw "Login response for $Email did not include data.accessToken."
    }
    return "Bearer $($accessToken.Trim())"
}

function Set-ScenarioEnvironment {
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

function Restore-ScenarioEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Previous
    )

    foreach ($entry in $Previous.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

function Add-VerificationResult {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SummaryPath,

        [Parameter(Mandatory = $true)]
        [string[]] $Output,

        [Parameter(Mandatory = $true)]
        [int] $ExitCode
    )

    if (-not (Test-Path -LiteralPath $SummaryPath)) {
        Set-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value '# k6 execution did not create a summary.'
    }
    Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value @(
        '',
        '## DB Invariants',
        '',
        "- Verifier exit code: $ExitCode",
        '',
        '```text',
        ($Output -join [Environment]::NewLine),
        '```',
        ''
    )
}

$database = Get-ContainerDatabaseConfiguration
New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($scenarioName in $scenarioNames) {
    Write-Host "Preparing existing scenario: $scenarioName"
    Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 $seedPath) | Out-Null

    $environment = @{
        PERF_BASE_URL = $BaseUrl.TrimEnd('/')
        PERF_SUMMARY_DIRECTORY = $resultDirectory
        PERF_SUMMARY_BASENAME = $scenarioName
        PERF_DURATION = '5s'
        PERF_VUS = '2'
    }
    $scenarioFile = $null
    $expectedRequests = 0

    switch ($scenarioName) {
        'auth-session' {
            $scenarioFile = 'scenarios/auth-session.js'
            $environment.PERF_AUTH_USERS_JSON = @(
                @{ email = 'k6-visitor@example.com'; password = $password },
                @{ email = 'k6-qr-visitor@example.com'; password = $password }
            ) | ConvertTo-Json -Compress
        }
        'public-api-response-time' {
            $scenarioFile = 'scenarios/public-api-response-time.js'
            $environment.PERF_REGION_ID = '900001'
            $environment.PERF_CONTENT_ID = '900001'
            $environment.PERF_SESSION_ID = '900001'
            $environment.PERF_MISSION_ID = '900010'
            $environment.PERF_RESERVATION_AVAILABLE = 'true'
        }
        'public-content' {
            $scenarioFile = 'scenarios/public-content-readonly.js'
            $environment.PERF_REGION_ID = '900001'
            $environment.PERF_CONTENT_ID = '900001'
            $environment.PERF_RESERVATION_AVAILABLE = 'true'
        }
        'reservation-flow' {
            $scenarioFile = 'scenarios/reservation-flow.js'
            Invoke-DatabaseSql -Sql 'UPDATE content SET reservation_price = 0 WHERE content_id = 900001;' | Out-Null
            $environment.PERF_SESSION_ID = '900001'
            $environment.PERF_VISITOR_ACCESS_TOKENS = @(
                Get-AccessToken -Email 'k6-visitor@example.com'
                Get-AccessToken -Email 'k6-qr-visitor@example.com'
            ) -join ','
        }
        'reservation-hold' {
            $scenarioFile = 'scenarios/reservation-hold-concurrency.js'
            Invoke-DatabaseSql -Sql 'UPDATE content SET reservation_price = 0 WHERE content_id = 900001;' | Out-Null
            $tokens = $visitorEmails | ForEach-Object { Get-AccessToken -Email $_ }
            $environment.PERF_RESERVATION_HOLD_CONCURRENCY_VUS = '10'
            $environment.PERF_RESERVATION_HOLD_CONCURRENCY_DURATION = '5s'
            $environment.PERF_RESERVATION_HOLD_SESSION_ID = '900003'
            $environment.PERF_VISITOR_ACCESS_TOKENS = $tokens -join ','
            $expectedRequests = 10
        }
        'qr-checkin' {
            $scenarioFile = 'scenarios/qr-checkin-concurrency.js'
            $environment.PERF_QR_CHECKIN_CONCURRENCY_VUS = '50'
            $environment.PERF_QR_CHECKIN_CONCURRENCY_DURATION = '5s'
            $environment.PERF_RESERVATION_ID = '900001'
            $environment.PERF_VISITOR_ACCESS_TOKENS = Get-AccessToken -Email 'k6-qr-visitor@example.com'
            $environment.PERF_OPERATOR_ACCESS_TOKENS = Get-AccessToken -Email 'k6-operator@example.com'
            $expectedRequests = 50
        }
        'manual-checkin' {
            $scenarioFile = 'scenarios/manual-checkin-concurrency.js'
            $environment.PERF_MANUAL_CHECKIN_CONCURRENCY_VUS = '2'
            $environment.PERF_MANUAL_CHECKIN_CONCURRENCY_DURATION = '5s'
            $environment.PERF_RESERVATION_NO = 'K6MN20260806000001'
            $environment.PERF_OPERATOR_ACCESS_TOKENS = Get-AccessToken -Email 'k6-operator@example.com'
            $expectedRequests = 2
        }
    }

    $summaryPath = Join-Path $resultDirectory "$scenarioName-summary.md"
    $previousEnvironment = Set-ScenarioEnvironment -Values $environment
    $k6ExitCode = 1
    try {
        & $K6Command run (Join-Path $k6Root $scenarioFile)
        $k6ExitCode = $LASTEXITCODE
    } finally {
        Restore-ScenarioEnvironment -Previous $previousEnvironment
    }

    $verifierExitCode = 0
    if ($expectedRequests -gt 0) {
        $verifierSql = @"
SET @perf_scenario = '$scenarioName';
SET @perf_expected_requests = $expectedRequests;
$(Get-Content -Raw -Encoding UTF8 $verifierPath)
"@
        try {
            $verifierOutput = Invoke-DatabaseSql -Sql $verifierSql
        } catch {
            $verifierExitCode = 1
            $verifierOutput = @($_.Exception.Message)
        }
        Add-VerificationResult `
            -SummaryPath $summaryPath `
            -Output $verifierOutput `
            -ExitCode $verifierExitCode
    }

    if ($k6ExitCode -ne 0 -or $verifierExitCode -ne 0) {
        $failures.Add("$scenarioName(k6=$k6ExitCode,db=$verifierExitCode)")
    }
}

if ($failures.Count -gt 0) {
    throw "Existing k6 scenario execution failed: $($failures -join ', ')"
}

Write-Host "All existing k6 scenarios passed. Results: $resultDirectory"
