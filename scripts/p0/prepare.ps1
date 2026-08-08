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
        throw "docker compose 실행에 실패했습니다. (exit code: $LASTEXITCODE)"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI를 찾을 수 없습니다. Docker Desktop을 설치하고 실행하세요.'
}

if (-not (Test-Path -LiteralPath $seedFile)) {
    throw "P0 시드 파일을 찾을 수 없습니다: $seedFile"
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
        throw 'MySQL이 30초 안에 준비 상태가 되지 않았습니다.'
    }

    Get-Content -Raw -Encoding UTF8 $seedFile |
        docker compose exec -T mysql mysql -uregional_event -pregional_event regional_event
    if ($LASTEXITCODE -ne 0) {
        throw "P0 MySQL 시드 적용에 실패했습니다. (exit code: $LASTEXITCODE)"
    }

    & docker compose exec -T redis redis-cli FLUSHDB | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "P0 Redis 초기화에 실패했습니다. (exit code: $LASTEXITCODE)"
    }

    Write-Host 'P0 공통 시드와 Redis 초기화가 완료되었습니다.'
}
finally {
    Pop-Location
}
