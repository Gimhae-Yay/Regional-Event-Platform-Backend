[CmdletBinding()]
param(
    [ValidateSet(
        'all',
        'payment',
        'paid-cancel',
        'reward-claim',
        'coupon-issue',
        'webhook',
        'mission-progress',
        'mission-readonly'
    )]
    [string] $Scenario = 'all',

    [string] $DatabaseContainer = 'regional-event-perf-local-perf-mysql-1',
    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $WebhookSecret = 'whsec_cGVyZi13ZWJob29rLXNlY3JldC0zMi1ieXRlcyEhISE=',
    [string] $ResultDate = (Get-Date).ToString('yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$seedPath = Join-Path $k6Root 'seed/k6-local.seed.sql'
$verifierPath = Join-Path $k6Root 'verify-new-scenarios.sql'
$resultDirectory = Join-Path $k6Root "results/$ResultDate"
$password = 'Password1!'

$scenarioNames = if ($Scenario -eq 'all') {
    @(
        'payment',
        'paid-cancel',
        'reward-claim',
        'coupon-issue',
        'webhook',
        'mission-progress',
        'mission-readonly'
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

function New-WebhookFixture {
    $webhookId = "k6-$([Guid]::NewGuid())"
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $body = @{
        type = 'Transaction.Paid'
        timestamp = [DateTimeOffset]::UtcNow.ToString('o')
        data = @{
            storeId = 'k6-store'
            paymentId = 'k6-order-900010'
            transactionId = 'k6-portone-payment-900010'
        }
    } | ConvertTo-Json -Compress -Depth 3

    $secretValue = $WebhookSecret -replace '^whsec_', ''
    $secretBytes = [Convert]::FromBase64String($secretValue)
    $messageBytes = [Text.Encoding]::UTF8.GetBytes("$webhookId.$timestamp.$body")
    $hmac = [Security.Cryptography.HMACSHA256]::new($secretBytes)
    try {
        $signature = 'v1,' + [Convert]::ToBase64String($hmac.ComputeHash($messageBytes))
    } finally {
        $hmac.Dispose()
    }
    return @{
        Id = $webhookId
        Timestamp = $timestamp
        Body = $body
        Signature = $signature
    }
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
    Write-Host "Preparing scenario: $scenarioName"
    Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 $seedPath) | Out-Null

    $visitorToken = Get-AccessToken -Email 'k6-visitor@example.com'
    $environment = @{
        PERF_BASE_URL = $BaseUrl.TrimEnd('/')
        PERF_SUMMARY_DIRECTORY = $resultDirectory
        PERF_SUMMARY_BASENAME = $scenarioName
        PERF_DURATION = '5s'
        PERF_VUS = '2'
        PERF_VISITOR_ACCESS_TOKENS = $visitorToken
    }
    $scenarioFile = $null
    $webhookId = ''

    switch ($scenarioName) {
        'payment' {
            $scenarioFile = 'scenarios/payment-idempotency-concurrency.js'
            $environment.PERF_PAYMENT_IDEMPOTENCY_CONCURRENCY_VUS = '50'
            $environment.PERF_PAYMENT_HOLD_ID = '900010'
            $environment.PERF_PAYMENT_IDEMPOTENCY_KEY = [Guid]::NewGuid().ToString()
        }
        'paid-cancel' {
            $scenarioFile = 'scenarios/paid-reservation-cancel-concurrency.js'
            $environment.PERF_PAID_RESERVATION_ID = '900010'
        }
        'reward-claim' {
            $scenarioFile = 'scenarios/mission-reward-claim-concurrency.js'
            $environment.PERF_MISSION_PARTICIPATION_ID = '900010'
        }
        'coupon-issue' {
            $scenarioFile = 'scenarios/coupon-issue-concurrency.js'
            $environment.PERF_COUPON_POLICY_ID = '900010'
            $environment.PERF_COUPON_ISSUE_SOURCE_TYPE = 'VISIT'
            $environment.PERF_COUPON_ISSUE_SOURCE_ID = '900010'
        }
        'webhook' {
            $scenarioFile = 'scenarios/portone-webhook-spike.js'
            $webhook = New-WebhookFixture
            $webhookId = $webhook.Id
            $environment.PERF_PORTONE_WEBHOOK_BODY = $webhook.Body
            $environment.PERF_PORTONE_WEBHOOK_ID = $webhook.Id
            $environment.PERF_PORTONE_WEBHOOK_TIMESTAMP = $webhook.Timestamp
            $environment.PERF_PORTONE_WEBHOOK_SIGNATURE = $webhook.Signature
            $environment.PERF_PORTONE_WEBHOOK_SPIKE_VUS = '20'
            $environment.PERF_PORTONE_WEBHOOK_SPIKE_ITERATIONS = '60'
        }
        'mission-progress' {
            $scenarioFile = 'scenarios/checkin-mission-progress-concurrency.js'
            $environment.PERF_CHECKIN_MISSION_PROGRESS_CONCURRENCY_VUS = '50'
            $environment.PERF_RESERVATION_ID = '900001'
            $environment.PERF_MISSION_PARTICIPATION_ID = '900011'
            $environment.PERF_VISITOR_ACCESS_TOKENS = Get-AccessToken -Email 'k6-qr-visitor@example.com'
            $environment.PERF_OPERATOR_ACCESS_TOKENS = Get-AccessToken -Email 'k6-operator@example.com'
        }
        'mission-readonly' {
            $scenarioFile = 'scenarios/public-mission-readonly.js'
            $environment.PERF_REGION_ID = '900001'
            $environment.PERF_MISSION_ID = '900010'
        }
    }

    $summaryPath = Join-Path $resultDirectory "$scenarioName-summary.md"
    $previousEnvironment = Set-ScenarioEnvironment -Values $environment
    try {
        & $K6Command run (Join-Path $k6Root $scenarioFile)
        $k6ExitCode = $LASTEXITCODE
    } finally {
        Restore-ScenarioEnvironment -Previous $previousEnvironment
    }

    $escapedWebhookId = $webhookId.Replace("'", "''")
    $verifierSql = @"
SET @perf_scenario = '$scenarioName';
SET @perf_webhook_id = '$escapedWebhookId';
$(Get-Content -Raw -Encoding UTF8 $verifierPath)
"@
    $verifierOutput = @()
    $verifierExitCode = 0
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

    if ($k6ExitCode -ne 0 -or $verifierExitCode -ne 0) {
        $failures.Add("$scenarioName(k6=$k6ExitCode,db=$verifierExitCode)")
    }
}

if ($failures.Count -gt 0) {
    throw "New k6 scenario execution failed: $($failures -join ', ')"
}

Write-Host "All new k6 scenarios passed. Results: $resultDirectory"
