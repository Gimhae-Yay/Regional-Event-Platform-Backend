[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$PrepareOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$prepareScript = Join-Path $PSScriptRoot 'prepare.ps1'
$httpDirectory = Join-Path $projectRoot 'http/p0'
$password = 'Test!23456'
Add-Type -AssemblyName System.Net.Http
$httpClient = [System.Net.Http.HttpClient]::new()

function Get-AccessToken {
    param(
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$Password
    )

    $body = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
    $content = [System.Net.Http.StringContent]::new(
        $body,
        [System.Text.Encoding]::UTF8,
        'application/json'
    )
    $response = $httpClient.PostAsync("$BaseUrl/api/v1/auth/login", $content).GetAwaiter().GetResult()
    try {
        if (-not $response.IsSuccessStatusCode) {
            throw "로그인 요청에 실패했습니다. status=$([int]$response.StatusCode), email=$Email"
        }

        $authorizationValues = [System.Collections.Generic.IEnumerable[string]]$null
        $response.Headers.TryGetValues('Authorization', [ref]$authorizationValues) | Out-Null
        $authorization = @($authorizationValues)[0]
    }
    finally {
        $response.Dispose()
        $content.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($authorization) -or -not $authorization.StartsWith('Bearer ')) {
        throw "로그인 응답에서 Bearer 토큰을 찾지 못했습니다: $Email"
    }
    return $authorization.Substring('Bearer '.Length)
}

function Wait-ForApplication {
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            $response = $httpClient.GetAsync("$BaseUrl/api/v1/regions").GetAwaiter().GetResult()
            if ($response.IsSuccessStatusCode) {
                $response.Dispose()
                return
            }
            $response.Dispose()
        }
        catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "로컬 애플리케이션 응답을 확인하지 못했습니다: $BaseUrl/api/v1/regions"
}

& $prepareScript
if ($LASTEXITCODE -ne 0) {
    throw "P0 준비 스크립트 실행에 실패했습니다. (exit code: $LASTEXITCODE)"
}

if ($PrepareOnly) {
    exit 0
}

Wait-ForApplication

$variables = @{
    p0RegionAdminAccessToken = Get-AccessToken -Email 'p0-region-admin@example.test' -Password $password
    p0OtherRegionAdminAccessToken = Get-AccessToken -Email 'p0-other-region-admin@example.test' -Password $password
    p0OperatorAccessToken = Get-AccessToken -Email 'p0-operator@example.test' -Password $password
    p0OtherOperatorAccessToken = Get-AccessToken -Email 'p0-other-operator@example.test' -Password $password
    p0VisitorAccessToken = Get-AccessToken -Email 'p0-visitor@example.test' -Password $password
    p0OtherVisitorAccessToken = Get-AccessToken -Email 'p0-other-visitor@example.test' -Password $password
    p0PublishedContentId = '900001'
    p0ScheduledSessionId = '910001'
    p0SoldOutContentId = '900001'
    p0SoldOutSessionId = '910002'
    p0StartedSessionId = '910003'
    p0ReservationHoldId = '940001'
    p0ExpiredHoldId = '940002'
    p0ReservationId = '930001'
    p0CheckedInReservationId = '930003'
    p0QrReservationId = '930006'
    p0CancelledReservationNo = 'RLOCALCANCEL'
    p0CompletedSessionReservationNo = 'RLOCALCOMPLETED'
    p0ManualReservationNo = 'RLOCALMANUAL2'
    p0VisitId = '950001'
    p0OtherVisitId = '950002'
    p0ReviewId = '960004'
    p0OtherReviewId = '960001'
    p0QrExceptionId = '990001'
}

$httpFiles = Get-ChildItem -LiteralPath $httpDirectory -Filter '*.http' -File | Sort-Object Name
if ($httpFiles.Count -eq 0) {
    throw "P0 HTTP 파일을 찾을 수 없습니다: $httpDirectory"
}

$dockerArguments = @('run', '--rm', '-v', "${projectRoot}:/workdir", '-w', '/workdir', 'jetbrains/intellij-http-client', '-D', '-L', 'BASIC', '--no-progress')
foreach ($entry in $variables.GetEnumerator()) {
    $dockerArguments += '-V'
    $dockerArguments += "$($entry.Key)=$($entry.Value)"
}
foreach ($httpFile in $httpFiles) {
    $dockerArguments += "http/p0/$($httpFile.Name)"
}

Push-Location $projectRoot
try {
    & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "P0 HTTP 시나리오 실행에 실패했습니다. (exit code: $LASTEXITCODE)"
    }
}
finally {
    Pop-Location
    $httpClient.Dispose()
}
