[CmdletBinding()]
param(
    [ValidateRange(1, 2147483647)]
    [int] $RequestsPerApi = 100,

    [string] $AccountsPath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-accounts.json'),
    [string] $CasesPath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-cases.json'),
    [string] $FixtureContextPath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-context.json'),

    [string] $DatabaseContainer = 'regional-event-perf-local-perf-mysql-1',
    [string] $BaseFixturePath = (Join-Path $PSScriptRoot 'seed/k6-local.seed.sql'),
    [string] $BootstrapFixturePath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-bootstrap.sql'),

    [ValidateSet('Container', 'Rds', 'RemoteApi')]
    [string] $FixtureDatabaseMode = 'Container',
    [string] $FixtureDatabaseHost,
    [ValidateRange(1, 65535)]
    [int] $FixtureDatabasePort = 3306,
    [string] $FixtureDatabaseName,
    [string] $FixtureDatabaseUser,
    [string] $FixtureDatabasePasswordEnvironmentVariable = 'PERF_FIXTURE_DB_PASSWORD',
    [switch] $AllowRdsFixtureReset,
    [string] $FixtureResetTokenEnvironmentVariable = 'PERF_FIXTURE_RESET_TOKEN',

    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $ResultDate = (Get-Date).ToString('yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$scenarioPath = Join-Path $k6Root 'scenarios/authenticated-read-response-time.js'
$resultDirectory = Join-Path $k6Root "results/$ResultDate"

Write-Host 'The target application must be running with PORTONE_FAKE_ENABLED=true and IMAGE_STORAGE_FAKE_ENABLED=true.'

function Get-FixtureDatabaseConfiguration {
    if ($FixtureDatabaseMode -eq 'Container') {
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
        return [PSCustomObject]@{
            Mode = 'Container'
            Container = $DatabaseContainer
            Database = $values.MYSQL_DATABASE
            User = $values.MYSQL_USER
            Password = $values.MYSQL_PASSWORD
        }
    }

    if (-not $AllowRdsFixtureReset) {
        throw 'Rds fixture mode requires -AllowRdsFixtureReset because it applies fixture SQL to the remote database.'
    }
    foreach ($parameterName in @('FixtureDatabaseHost', 'FixtureDatabaseName', 'FixtureDatabaseUser')) {
        $value = Get-Variable -Name $parameterName -ValueOnly
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Rds fixture mode requires -$parameterName."
        }
    }
    if ([string]::IsNullOrWhiteSpace($FixtureDatabasePasswordEnvironmentVariable)) {
        throw 'Rds fixture mode requires -FixtureDatabasePasswordEnvironmentVariable.'
    }
    $password = [Environment]::GetEnvironmentVariable($FixtureDatabasePasswordEnvironmentVariable, 'Process')
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "Rds fixture mode requires the $FixtureDatabasePasswordEnvironmentVariable process environment variable."
    }
    $mysqlCommand = Get-Command mysql -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $mysqlCommand) {
        throw 'Rds fixture mode requires the MySQL CLI (mysql) on the k6 runner.'
    }
    return [PSCustomObject]@{
        Mode = 'Rds'
        Host = $FixtureDatabaseHost
        Port = $FixtureDatabasePort
        Database = $FixtureDatabaseName
        User = $FixtureDatabaseUser
        Password = $password
        MySqlPath = $mysqlCommand.Path
    }
}

function Get-RemoteFixtureResetConfiguration {
    if ([string]::IsNullOrWhiteSpace($FixtureResetTokenEnvironmentVariable)) {
        throw 'RemoteApi fixture mode requires -FixtureResetTokenEnvironmentVariable.'
    }
    $token = [Environment]::GetEnvironmentVariable($FixtureResetTokenEnvironmentVariable, 'Process')
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "RemoteApi fixture mode requires the $FixtureResetTokenEnvironmentVariable process environment variable."
    }
    $resetUrl = "$($BaseUrl.TrimEnd('/'))/internal/performance/fixtures/reset"
    $uri = $null
    if (-not [Uri]::TryCreate($resetUrl, [UriKind]::Absolute, [ref] $uri)) {
        throw "RemoteApi fixture mode requires an absolute -BaseUrl: $BaseUrl"
    }
    return [PSCustomObject]@{
        ResetUrl = $uri.AbsoluteUri
        Token = $token
    }
}

function Invoke-DatabaseSql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql
    )

    $fixtureSql = "SET FOREIGN_KEY_CHECKS = 0;$([Environment]::NewLine)$Sql$([Environment]::NewLine)SET FOREIGN_KEY_CHECKS = 1;"
    if ($database.Mode -eq 'Container') {
        $output = $fixtureSql |
            docker exec -i -e "MYSQL_PWD=$($database.Password)" $database.Container `
                mysql "-u$($database.User)" $database.Database 2>&1
    } else {
        $previousMySqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $database.Password, 'Process')
        try {
            $mysqlArguments = @(
                '--protocol=TCP',
                "--host=$($database.Host)",
                "--port=$($database.Port)",
                "--user=$($database.User)",
                $database.Database
            )
            $output = $fixtureSql | & $database.MySqlPath @mysqlArguments 2>&1
        } finally {
            [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousMySqlPassword, 'Process')
        }
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Database fixture preparation failed with exit code $LASTEXITCODE.`n$($output -join [Environment]::NewLine)"
    }
}

function Invoke-FixtureReset {
    if ($FixtureDatabaseMode -ne 'RemoteApi') {
        Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 -LiteralPath $BaseFixturePath)
        Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 -LiteralPath $BootstrapFixturePath)
        return
    }

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri $remoteFixtureReset.ResetUrl `
            -Headers @{
                Accept = 'application/json'
                'X-Performance-Fixture-Token' = $remoteFixtureReset.Token
            }
    } catch {
        throw "Remote fixture reset request failed: $($_.Exception.Message)"
    }
    if ($response.statusCode -ne 200 `
        -or $response.code -ne 'SUCCESS' `
        -or $null -eq $response.data `
        -or [string]::IsNullOrWhiteSpace($response.data.fixtureVersion)) {
        throw 'Remote fixture reset response must contain HTTP 200, code SUCCESS, and data.fixtureVersion.'
    }
    Write-Host "Remote fixture reset completed: $($response.data.fixtureVersion)"
}

function Get-JsonArrayFile {
    param([string] $Path, [string] $Name)

    $value = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
    $parsed = $value | ConvertFrom-Json
    if ($parsed -isnot [System.Array] -or $parsed.Count -eq 0) {
        throw "$Name must be a non-empty JSON array: $Path"
    }
    return $value
}

function Get-JsonObjectFile {
    param([string] $Path, [string] $Name)

    $value = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
    $parsed = $value | ConvertFrom-Json
    if ($parsed -is [System.Array] -or $null -eq $parsed) {
        throw "$Name must be a JSON object: $Path"
    }
    return $value
}

function Get-ControllerGetEndpoints {
    $controllerRoot = Join-Path $k6Root '../../src/main/java'
    $endpoints = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

    Get-ChildItem -LiteralPath $controllerRoot -Recurse -File -Filter '*Controller.java' |
        ForEach-Object {
            $source = Get-Content -Raw -Encoding UTF8 -LiteralPath $_.FullName
            $classMatch = [regex]::Match(
                $source,
                '@RequestMapping\((?<mapping>[\s\S]*?)\)\s*public class'
            )
            $basePaths = if ($classMatch.Success) {
                @([regex]::Matches($classMatch.Groups['mapping'].Value, '"(?<path>/[^"]*)"') |
                    ForEach-Object { $_.Groups['path'].Value })
            } else {
                @('')
            }
            [regex]::Matches($source, '@GetMapping(?:\((?<mapping>[^)]*)\))?') |
                ForEach-Object {
                    $methodPaths = @([regex]::Matches($_.Groups['mapping'].Value, '"(?<path>/[^"]*)"') |
                        ForEach-Object { $_.Groups['path'].Value })
                    if ($methodPaths.Count -eq 0) {
                        $methodPaths = @('')
                    }
                    foreach ($basePath in $basePaths) {
                        foreach ($methodPath in $methodPaths) {
                            $path = "$basePath$methodPath"
                            if ($path.StartsWith('/api/v1/')) {
                                [void] $endpoints.Add("GET $path")
                            }
                        }
                    }
                }
        }

    return $endpoints
}

function Get-ReadCases {
    param([string] $CasesJson)

    $readCases = @($CasesJson | ConvertFrom-Json | Where-Object { $_.method -eq 'GET' })
    if ($readCases.Count -eq 0) {
        throw 'CasesPath does not contain a GET case.'
    }
    return $readCases | ConvertTo-Json -Compress -Depth 20
}

function Get-ReadFixtureSetupCases {
    param([string] $CasesJson)

    $requiredCaseIds = @(
        'operator-create-representative-image-upload',
        'operator-create-content',
        'region-admin-approve-content',
        'operator-create-content-session',
        'visitor-issue-visit-coupon',
        'visitor-create-coupon-available-hold',
        'platform-admin-create-refund',
        'operator-create-application'
    )
    $casesById = @{}
    $parsedCases = @($CasesJson | ConvertFrom-Json)
    foreach ($scenarioCase in $parsedCases) {
        $casesById[[string] $scenarioCase.id] = $scenarioCase
    }

    $missingCaseIds = [System.Collections.Generic.List[string]]::new()
    foreach ($requiredCaseId in $requiredCaseIds) {
        if (-not $casesById.ContainsKey($requiredCaseId)) {
            $missingCaseIds.Add($requiredCaseId)
        }
    }
    if ($missingCaseIds.Count -gt 0) {
        throw "CasesPath is missing read fixture setup cases: $($missingCaseIds -join ', ')"
    }
    $setupCases = foreach ($requiredCaseId in $requiredCaseIds) {
        $casesById[$requiredCaseId]
    }
    return @($setupCases) | ConvertTo-Json -Compress -Depth 20
}

function Assert-CompleteGetCoverage {
    param([string] $ReadCasesJson)

    $covered = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    ($ReadCasesJson | ConvertFrom-Json) | ForEach-Object {
        if ([string]::IsNullOrWhiteSpace($_.route)) {
            throw "Each read case must declare route. Missing route: $($_.id)"
        }
        [void] $covered.Add("GET $($_.route)")
    }

    $missing = Get-ControllerGetEndpoints | Where-Object { -not $covered.Contains($_) } | Sort-Object
    if ($missing.Count -gt 0) {
        throw "Read cases do not cover these GET controller mappings:`n$($missing -join [Environment]::NewLine)"
    }
}

$accounts = Get-JsonArrayFile -Path $AccountsPath -Name 'AccountsPath'
$allCases = Get-JsonArrayFile -Path $CasesPath -Name 'CasesPath'
$readCases = Get-ReadCases -CasesJson $allCases
$readFixtureSetupCases = Get-ReadFixtureSetupCases -CasesJson $allCases
$fixtureContext = Get-JsonObjectFile -Path $FixtureContextPath -Name 'FixtureContextPath'
Assert-CompleteGetCoverage -ReadCasesJson $readCases
$database = $null
$remoteFixtureReset = $null
if ($FixtureDatabaseMode -eq 'RemoteApi') {
    $remoteFixtureReset = Get-RemoteFixtureResetConfiguration
} else {
    $database = Get-FixtureDatabaseConfiguration
}
Invoke-FixtureReset

New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
$k6Environment = @{
    PERF_BASE_URL = $BaseUrl.TrimEnd('/')
    PERF_SUMMARY_DIRECTORY = $resultDirectory
    PERF_SUMMARY_BASENAME = 'authenticated-read-response-time'
    PERF_API_TEST_ACCOUNTS_JSON = $accounts
    PERF_AUTHENTICATED_READ_CASES_JSON = $readCases
    PERF_AUTHENTICATED_READ_SETUP_CASES_JSON = $readFixtureSetupCases
    PERF_API_FIXTURE_CONTEXT_JSON = $fixtureContext
    PERF_REQUESTS_PER_API = $RequestsPerApi
}
$previousEnvironment = @{}
foreach ($name in $k6Environment.Keys) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $k6Environment[$name], 'Process')
}

try {
    & $K6Command run $scenarioPath
    if ($LASTEXITCODE -ne 0) {
        throw "Authenticated read response time scenario failed with exit code $LASTEXITCODE."
    }
} finally {
    foreach ($name in $k6Environment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
}

Write-Host "Authenticated read response time scenario completed for $RequestsPerApi requests per API. Results: $resultDirectory"
