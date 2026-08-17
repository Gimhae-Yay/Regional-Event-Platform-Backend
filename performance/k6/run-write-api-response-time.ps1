[CmdletBinding()]
param(
    [ValidateRange(1, 2147483647)]
    [int] $RequestsPerCase = 100,

    [string] $AccountsPath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-accounts.json'),
    [string] $CasesPath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-cases.json'),
    [string] $FixtureContextPath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-context.json'),

    [string] $DatabaseContainer = 'regional-event-perf-local-perf-mysql-1',
    [string] $BaseFixturePath = (Join-Path $PSScriptRoot 'seed/k6-local.seed.sql'),
    [string] $BootstrapFixturePath = (Join-Path $PSScriptRoot 'fixtures/api-success-coverage-bootstrap.sql'),

    [ValidateSet('Container', 'Rds')]
    [string] $FixtureDatabaseMode = 'Container',
    [string] $FixtureDatabaseHost,
    [ValidateRange(1, 65535)]
    [int] $FixtureDatabasePort = 3306,
    [string] $FixtureDatabaseName,
    [string] $FixtureDatabaseUser,
    [string] $FixtureDatabasePasswordEnvironmentVariable = 'PERF_FIXTURE_DB_PASSWORD',
    [switch] $AllowRdsFixtureReset,

    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $ResultDate = (Get-Date).ToString('yyyy-MM-dd'),
    [switch] $KeepRawMetrics
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$scenarioPath = Join-Path $k6Root 'scenarios/api-success-coverage.js'
$resultDirectory = Join-Path $k6Root "results/$ResultDate/write-api-response-time"

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

function Get-ControllerWriteEndpoints {
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
            [regex]::Matches($source, '@(?<method>Post|Put|Patch|Delete)Mapping(?:\((?<mapping>[^)]*)\))?') |
                ForEach-Object {
                    $method = $_.Groups['method'].Value.ToUpperInvariant()
                    $methodPaths = @([regex]::Matches($_.Groups['mapping'].Value, '"(?<path>/[^"]*)"') |
                        ForEach-Object { $_.Groups['path'].Value })
                    if ($methodPaths.Count -eq 0) {
                        $methodPaths = @('')
                    }
                    foreach ($basePath in $basePaths) {
                        foreach ($methodPath in $methodPaths) {
                            $path = "$basePath$methodPath"
                            if ($path.StartsWith('/api/v1/')) {
                                [void] $endpoints.Add("$method $path")
                            }
                        }
                    }
                }
        }

    return $endpoints
}

function Get-WriteCases {
    param([string] $CasesJson)

    $allCases = @($CasesJson | ConvertFrom-Json)
    $writeCases = @($allCases | Where-Object { $_.method -in @('POST', 'PUT', 'PATCH', 'DELETE') })
    if ($writeCases.Count -eq 0) {
        throw 'CasesPath does not contain a write case.'
    }
    $qrTokenSetupCase = @($allCases | Where-Object { $_.id -eq 'visitor-reservation-qr' })
    if ($qrTokenSetupCase.Count -ne 1) {
        throw 'CasesPath must contain exactly one visitor-reservation-qr fixture setup case.'
    }
    $qrTokenSetupCase[0] | Add-Member -NotePropertyName 'excludeFromResponseTime' -NotePropertyValue $true
    return @($writeCases + $qrTokenSetupCase[0]) | ConvertTo-Json -Compress -Depth 20
}

function Assert-CompleteWriteCoverage {
    param([string] $WriteCasesJson)

    $covered = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    ($WriteCasesJson | ConvertFrom-Json) | ForEach-Object {
        if ([string]::IsNullOrWhiteSpace($_.route)) {
            throw "Each write case must declare route. Missing route: $($_.id)"
        }
        [void] $covered.Add("$($_.method.ToUpperInvariant()) $($_.route)")
    }
    # api-success-coverage.js logs in every required account during setup().
    [void] $covered.Add('POST /api/v1/auth/login')

    $missing = Get-ControllerWriteEndpoints | Where-Object { -not $covered.Contains($_) } | Sort-Object
    if ($missing.Count -gt 0) {
        throw "Write cases do not cover these controller mappings:`n$($missing -join [Environment]::NewLine)"
    }
}

function Set-ProcessEnvironment {
    param([hashtable] $Values)

    $previous = @{}
    foreach ($name in $Values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $Values[$name], 'Process')
    }
    return $previous
}

function Restore-ProcessEnvironment {
    param([hashtable] $Previous)

    foreach ($name in $Previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $Previous[$name], 'Process')
    }
}

function Get-Percentile {
    param(
        [double[]] $Values,
        [double] $Percentile
    )

    if ($Values.Count -eq 0) {
        return $null
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($sorted.Count * $Percentile) - 1
    return $sorted[[Math]::Max(0, $index)]
}

function Write-AggregatedSummary {
    param(
        [string[]] $MetricPaths,
        [hashtable] $ExpectedRequestCounts,
        [string[]] $ExcludedCaseIds,
        [string] $OutputPath
    )

    $durationsByEndpoint = @{}
    foreach ($metricPath in $MetricPaths) {
        Get-Content -Encoding UTF8 -LiteralPath $metricPath |
            ForEach-Object {
                $record = $_ | ConvertFrom-Json
                if ($record.type -ne 'Point' -or $record.metric -ne 'http_req_duration') {
                    return
                }
                if ($ExcludedCaseIds -contains [string] $record.data.tags.case) {
                    return
                }
                $endpoint = To-CanonicalEndpoint -Endpoint ([string] $record.data.tags.endpoint)
                if ([string]::IsNullOrWhiteSpace($endpoint) -or $endpoint -eq 'POST /api/v1/auth/login') {
                    return
                }
                if (-not $durationsByEndpoint.ContainsKey($endpoint)) {
                    $durationsByEndpoint[$endpoint] = [System.Collections.Generic.List[double]]::new()
                }
                $durationsByEndpoint[$endpoint].Add([double] $record.data.value)
            }
    }

    $unexpectedEndpoints = $durationsByEndpoint.Keys | Where-Object { -not $ExpectedRequestCounts.ContainsKey($_) }
    if ($unexpectedEndpoints) {
        throw "Unexpected write endpoint metrics were recorded: $($unexpectedEndpoints -join ', ')"
    }
    foreach ($endpoint in $ExpectedRequestCounts.Keys) {
        $actualCount = if ($durationsByEndpoint.ContainsKey($endpoint)) { $durationsByEndpoint[$endpoint].Count } else { 0 }
        if ($actualCount -ne $ExpectedRequestCounts[$endpoint]) {
            throw "Write endpoint $endpoint expected $($ExpectedRequestCounts[$endpoint]) requests but recorded $actualCount."
        }
    }

    $rows = $durationsByEndpoint.Keys |
        Sort-Object |
        ForEach-Object {
            $endpoint = $_
            $values = $durationsByEndpoint[$endpoint]
            [PSCustomObject]@{
                Endpoint = $endpoint
                Requests = $values.Count
                AverageMilliseconds = [Math]::Round((($values | Measure-Object -Average).Average), 2)
                P95Milliseconds = [Math]::Round((Get-Percentile -Values $values.ToArray() -Percentile 0.95), 2)
                MaximumMilliseconds = [Math]::Round((($values | Measure-Object -Maximum).Maximum), 2)
            }
        }
    $markdown = @(
        '# k6 Write API Response Time Summary',
        '',
        "- Requests per write case: $RequestsPerCase",
        "- Independent fixture rounds: $RequestsPerCase",
        '',
        '| Endpoint | Requests | Avg | P95 | Max |',
        '| --- | ---: | ---: | ---: | ---: |'
    )
    $markdown += $rows | ForEach-Object {
        "| $($_.Endpoint) | $($_.Requests) | $($_.AverageMilliseconds)ms | $($_.P95Milliseconds)ms | $($_.MaximumMilliseconds)ms |"
    }
    [System.IO.File]::WriteAllLines($OutputPath, $markdown, (New-Object System.Text.UTF8Encoding($false)))
}

function To-CanonicalEndpoint {
    param([string] $Endpoint)

    if ($Endpoint -match '^(?<method>[A-Z]+) (?<path>/.*)$' -and -not $Matches.path.StartsWith('/api/v1/')) {
        return "$($Matches.method) /api/v1$($Matches.path)"
    }
    return $Endpoint
}

function New-TemporaryRawResultDirectory {
    $directory = Join-Path ([System.IO.Path]::GetTempPath()) "regional-event-k6-$([Guid]::NewGuid())"
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    return $directory
}

function Remove-TemporaryRawResultDirectory {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) `
        -or [System.IO.Path]::GetFileName($fullPath) -notmatch '^regional-event-k6-[0-9a-f-]+$') {
        throw "Refusing to remove a non-k6 temporary result directory: $fullPath"
    }
    Remove-Item -LiteralPath $fullPath -Recurse -Force
}

$accounts = Get-JsonArrayFile -Path $AccountsPath -Name 'AccountsPath'
$allCases = Get-JsonArrayFile -Path $CasesPath -Name 'CasesPath'
$writeCases = Get-WriteCases -CasesJson $allCases
$fixtureContext = Get-JsonObjectFile -Path $FixtureContextPath -Name 'FixtureContextPath'
Assert-CompleteWriteCoverage -WriteCasesJson $writeCases
$database = Get-FixtureDatabaseConfiguration
New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
$rawResultDirectory = if ($KeepRawMetrics) { $resultDirectory } else { New-TemporaryRawResultDirectory }

$expectedRequestCounts = @{}
$excludedCaseIds = @()
($writeCases | ConvertFrom-Json) | ForEach-Object {
    if ($_.excludeFromResponseTime) {
        $excludedCaseIds += $_.id
        return
    }
    $endpoint = To-CanonicalEndpoint -Endpoint "$($_.method.ToUpperInvariant()) $($_.path)"
    if (-not $expectedRequestCounts.ContainsKey($endpoint)) {
        $expectedRequestCounts[$endpoint] = 0
    }
    $expectedRequestCounts[$endpoint] += $RequestsPerCase
}

$metricPaths = [System.Collections.Generic.List[string]]::new()
$completed = $false
try {
    for ($round = 1; $round -le $RequestsPerCase; $round += 1) {
        $roundDirectory = Join-Path $rawResultDirectory ("round-{0:D3}" -f $round)
        $metricPath = Join-Path $roundDirectory 'metrics.json'
        New-Item -ItemType Directory -Force -Path $roundDirectory | Out-Null
        Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 -LiteralPath $BaseFixturePath)
        Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 -LiteralPath $BootstrapFixturePath)

        $environment = @{
            PERF_BASE_URL = $BaseUrl.TrimEnd('/')
            PERF_SUMMARY_DIRECTORY = $roundDirectory
            PERF_SUMMARY_BASENAME = 'write-api-response-time'
            PERF_API_TEST_ACCOUNTS_JSON = $accounts
            PERF_API_SUCCESS_CASES_JSON = $writeCases
            PERF_API_FIXTURE_CONTEXT_JSON = $fixtureContext
        }
        $previousEnvironment = Set-ProcessEnvironment -Values $environment
        try {
            & $K6Command run '--out' "json=$metricPath" $scenarioPath
            if ($LASTEXITCODE -ne 0) {
                throw "Write API response time scenario failed in round $round with exit code $LASTEXITCODE."
            }
        } finally {
            Restore-ProcessEnvironment -Previous $previousEnvironment
        }
        $metricPaths.Add($metricPath)
    }

    $summaryPath = Join-Path $resultDirectory 'write-api-response-time-summary.md'
    Write-AggregatedSummary `
        -MetricPaths $metricPaths.ToArray() `
        -ExpectedRequestCounts $expectedRequestCounts `
        -ExcludedCaseIds $excludedCaseIds `
        -OutputPath $summaryPath
    $completed = $true
} finally {
    if ($completed -and -not $KeepRawMetrics) {
        Remove-TemporaryRawResultDirectory -Path $rawResultDirectory
    }
}
Write-Host "Write API response time scenario completed for $RequestsPerCase independent fixture rounds. Results: $resultDirectory"
