[CmdletBinding()]
param(
    [switch]$SkipCompose
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$seedFile = Join-Path $PSScriptRoot 'seed.sql'

function Invoke-DockerCompose {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed. (exit code: $LASTEXITCODE)"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI was not found. Install and start Docker Desktop.'
}

if (-not (Test-Path -LiteralPath $seedFile)) {
    throw "P0 seed file was not found: $seedFile"
}

Push-Location $projectRoot
try {
    if (-not $SkipCompose) {
        Invoke-DockerCompose -Arguments @('up', '-d', 'mysql', 'redis')
    }

    $mysqlReady = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        & docker compose exec -T -e MYSQL_PWD=regional_event mysql mysqladmin ping -h localhost -uregional_event --silent 2>$null
        if ($LASTEXITCODE -eq 0) {
            $mysqlReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $mysqlReady) {
        throw 'MySQL did not become ready within 30 seconds.'
    }

    Get-Content -Raw -Encoding UTF8 $seedFile |
        docker compose exec -T mysql mysql -uregional_event -pregional_event regional_event
    if ($LASTEXITCODE -ne 0) {
        throw "P0 MySQL seed application failed. (exit code: $LASTEXITCODE)"
    }

    & docker compose exec -T redis redis-cli FLUSHDB | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "P0 Redis reset failed. (exit code: $LASTEXITCODE)"
    }

    Write-Host 'P0 shared seed and Redis reset completed.'
}
finally {
    Pop-Location
}
