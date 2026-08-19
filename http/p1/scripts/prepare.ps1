[CmdletBinding()]
param(
    [switch]$SkipCompose
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$cleanupFile = Join-Path $PSScriptRoot 'cleanup.sql'
$seedFile = Join-Path $PSScriptRoot 'seed.sql'
$p0PrepareScript = Join-Path $projectRoot 'http/p0/scripts/prepare.ps1'

function Invoke-MySqlFile {
    param(
        [Parameter(Mandatory)][string]$File,
        [Parameter(Mandatory)][string]$Description
    )

    Get-Content -Raw -Encoding UTF8 $File |
        docker compose exec -T mysql mysql -uregional_event -pregional_event regional_event
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed. (exit code: $LASTEXITCODE)"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI was not found. Install and start Docker Desktop.'
}

foreach ($file in @($cleanupFile, $seedFile, $p0PrepareScript)) {
    if (-not (Test-Path -LiteralPath $file)) {
        throw "P1 preparation file was not found: $file"
    }
}

Push-Location $projectRoot
try {
    if (-not $SkipCompose) {
        & docker compose up -d mysql redis
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose command failed. (exit code: $LASTEXITCODE)"
        }
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

    Invoke-MySqlFile -File $cleanupFile -Description 'P1 MySQL cleanup'
    & $p0PrepareScript -SkipCompose
    if ($LASTEXITCODE -ne 0) {
        throw "P0 preparation script failed. (exit code: $LASTEXITCODE)"
    }
    Invoke-MySqlFile -File $seedFile -Description 'P1 MySQL seed application'

    Write-Host 'P1 shared seed and Redis reset completed.'
}
finally {
    Pop-Location
}
