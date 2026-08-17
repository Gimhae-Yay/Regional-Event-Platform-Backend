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

    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $ResultDate = (Get-Date).ToString('yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$scenarioPath = Join-Path $k6Root 'scenarios/authenticated-read-response-time.js'
$resultDirectory = Join-Path $k6Root "results/$ResultDate"

Write-Host 'The target application must be running with PORTONE_FAKE_ENABLED=true and IMAGE_STORAGE_FAKE_ENABLED=true.'

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
$fixtureContext = Get-JsonObjectFile -Path $FixtureContextPath -Name 'FixtureContextPath'
Assert-CompleteGetCoverage -ReadCasesJson $readCases
$database = Get-ContainerDatabaseConfiguration
Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 -LiteralPath $BaseFixturePath)
Invoke-DatabaseSql -Sql (Get-Content -Raw -Encoding UTF8 -LiteralPath $BootstrapFixturePath)

New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
$k6Environment = @{
    PERF_BASE_URL = $BaseUrl.TrimEnd('/')
    PERF_SUMMARY_DIRECTORY = $resultDirectory
    PERF_SUMMARY_BASENAME = 'authenticated-read-response-time'
    PERF_API_TEST_ACCOUNTS_JSON = $accounts
    PERF_AUTHENTICATED_READ_CASES_JSON = $readCases
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
