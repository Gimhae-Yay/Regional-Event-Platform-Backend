[CmdletBinding()]
param(
    [string] $K6Command = 'k6',
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Duration = '10s',
    [int] $Vus = 1,
    [ValidateRange(0, 2147483647)]
    [int] $Iterations = 0,
    [string] $ResultDate = (Get-Date).ToString('yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
$k6Root = $PSScriptRoot
$scenarioPath = Join-Path $k6Root 'scenarios/all-api-response-time.js'
$resultDirectory = Join-Path $k6Root "results/$ResultDate"
$controllerRoot = Join-Path $k6Root '../../src/main/java'

function Get-QuotedPaths {
    param(
        [string] $Text
    )

    return [regex]::Matches($Text, '"(?<path>/[^"]*)"') |
        ForEach-Object { $_.Groups['path'].Value }
}

function Get-ApiResponseTargets {
    $targets = [System.Collections.Generic.List[object]]::new()
    Get-ChildItem -LiteralPath $controllerRoot -Recurse -File -Filter '*Controller.java' |
        ForEach-Object {
            $source = Get-Content -Raw -LiteralPath $_.FullName
            $classMapping = [regex]::Match(
                $source,
                '@RequestMapping\((?<mapping>[\s\S]*?)\)\s*public class'
            )
            $basePaths = if ($classMapping.Success) {
                @(Get-QuotedPaths -Text $classMapping.Groups['mapping'].Value)
            } else {
                @('')
            }
            if ($basePaths.Count -eq 0) {
                $basePaths = @('')
            }

            [regex]::Matches(
                $source,
                '@(?<method>Get|Post|Put|Patch|Delete)Mapping(?:\((?<mapping>[^)]*)\))?'
            ) | ForEach-Object {
                $method = $_.Groups['method'].Value.ToUpper()
                $methodPaths = @(Get-QuotedPaths -Text $_.Groups['mapping'].Value)
                if ($methodPaths.Count -eq 0) {
                    $methodPaths = @('')
                }
                foreach ($basePath in $basePaths) {
                    foreach ($methodPath in $methodPaths) {
                        $path = "$basePath$methodPath" -replace '\{[^}]+\}', '900001'
                        $targets.Add([PSCustomObject]@{
                            endpoint = "$method $path"
                            method = $method
                            path = $path
                            body = if ($method -in @('POST', 'PUT', 'PATCH')) { @{} } else { $null }
                        })
                    }
                }
            }
        }

    return @($targets | Sort-Object endpoint -Unique)
}

$targets = Get-ApiResponseTargets
if ($targets.Count -eq 0) {
    throw 'No controller mappings were found.'
}

New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
$targetsJson = $targets | ConvertTo-Json -Compress -Depth 4
& $K6Command run `
    '-e' "PERF_BASE_URL=$($BaseUrl.TrimEnd('/'))" `
    '-e' "PERF_DURATION=$Duration" `
    '-e' "PERF_VUS=$Vus" `
    '-e' "PERF_ITERATIONS=$Iterations" `
    '-e' "PERF_SUMMARY_DIRECTORY=$resultDirectory" `
    '-e' 'PERF_SUMMARY_BASENAME=all-api-response-time' `
    '-e' "PERF_API_RESPONSE_TARGETS_JSON=$targetsJson" `
    $scenarioPath
if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
    throw "All API response time scenario failed with exit code $LASTEXITCODE."
}

Write-Host "All API response time scenario completed for $($targets.Count) targets. Results: $resultDirectory"
