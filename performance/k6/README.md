# k6 API 응답시간 실행 가이드

## 대상 러너

| 러너 | 범위 | fixture 초기화 |
| --- | --- | --- |
| `run-authenticated-read-response-time.ps1` | 인증이 필요한 GET API 59개 | 실행 전 1회 |
| `run-write-api-response-time.ps1` | 쓰기 API 케이스 70개 | 각 독립 라운드 전 1회 |

두 러너는 역할별 계정으로 로그인하고 유효한 fixture ID와 요청 본문을 사용한다. `-BaseUrl`에는 `/api/v1`을 붙이지 않는다. 러너가 내부적으로 `/api/v1`을 추가한다.

## 공통 사전 조건

- `k6`가 실행 PATH에 있어야 한다.
- 대상 API는 `PORTONE_FAKE_ENABLED=true`, `IMAGE_STORAGE_FAKE_ENABLED=true`로 실행해야 한다.
- 대상 API의 계정·schema·fixture는 `fixtures/api-success-coverage-accounts.json`, `fixtures/api-success-coverage-context.json`, `seed/k6-local.seed.sql`, `fixtures/api-success-coverage-bootstrap.sql`과 호환되어야 한다.
- 조회·쓰기 러너를 동시에 실행하지 않는다. 둘 다 같은 fixture DB를 초기화한다.

## 로컬 Docker 실행

기본값은 `Container` 모드다. `DatabaseContainer`의 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`를 읽어 fixture SQL을 적용한다.

```powershell
.\performance\k6\run-authenticated-read-response-time.ps1 `
  -RequestsPerApi 100

.\performance\k6\run-write-api-response-time.ps1 `
  -RequestsPerCase 100
```

기본 컨테이너 이름과 API 주소가 다르면 명시한다.

```powershell
.\performance\k6\run-authenticated-read-response-time.ps1 `
  -DatabaseContainer 'regional-event-perf-local-perf-mysql-1' `
  -BaseUrl 'http://127.0.0.1:18081' `
  -RequestsPerApi 100
```

## 성능 환경 RDS 실행

RDS 모드는 **성능 전용 DB에만** 사용한다. 쓰기 러너는 매 라운드 fixture를 초기화하므로 운영 RDS, 공유 개발 RDS, 복구가 필요한 DB에는 실행하면 안 된다.

1. k6 실행기를 API와 RDS에 사설망으로 접근 가능한 환경에 둔다.
2. 실행기에 MySQL CLI(`mysql`)를 설치한다.
3. RDS 보안 그룹은 k6 실행기만 허용하고, DB 사용자는 성능 전용 스키마의 fixture SQL에 필요한 최소 권한만 부여한다.
4. Secret Manager, CI Secret 또는 실행 환경의 Secret 주입으로 `PERF_FIXTURE_DB_PASSWORD`를 **프로세스 환경 변수**에 설정한다. 명령행 인자로 비밀번호를 전달하지 않는다.

```powershell
# Secret 주입 예시: 실제 Secret 값은 콘솔·스크립트·로그에 기록하지 않는다.
$env:PERF_FIXTURE_DB_PASSWORD = '<Secret injection result>'

.\performance\k6\run-authenticated-read-response-time.ps1 `
  -BaseUrl 'https://perf-api.example.com' `
  -FixtureDatabaseMode Rds `
  -FixtureDatabaseHost 'perf-db.xxxx.ap-northeast-2.rds.amazonaws.com' `
  -FixtureDatabasePort 3306 `
  -FixtureDatabaseName 'regional_event_perf' `
  -FixtureDatabaseUser 'perf_runner' `
  -AllowRdsFixtureReset `
  -RequestsPerApi 100

.\performance\k6\run-write-api-response-time.ps1 `
  -BaseUrl 'https://perf-api.example.com' `
  -FixtureDatabaseMode Rds `
  -FixtureDatabaseHost 'perf-db.xxxx.ap-northeast-2.rds.amazonaws.com' `
  -FixtureDatabasePort 3306 `
  -FixtureDatabaseName 'regional_event_perf' `
  -FixtureDatabaseUser 'perf_runner' `
  -AllowRdsFixtureReset `
  -RequestsPerCase 100
```

`Rds` 모드는 `-AllowRdsFixtureReset`, host, name, user, 비밀번호 환경 변수가 모두 없으면 k6 실행 전에 실패한다. 기본 비밀번호 환경 변수 이름은 `PERF_FIXTURE_DB_PASSWORD`이고, 다른 이름을 쓸 때만 `-FixtureDatabasePasswordEnvironmentVariable`을 지정한다.

## 결과 확인

결과는 `performance/k6/results/<yyyy-MM-dd>/` 아래에 생성된다.

- 조회: `authenticated-read-response-time-summary.md`
- 쓰기: `write-api-response-time/write-api-response-time-summary.md` 및 각 `round-*/metrics.json`

쓰기 결과는 케이스별 평균·p95·최대 응답시간을 포함한다. HTTP 실패율만 보지 말고 정상 응답 check, endpoint별 상태 코드, fixture 초기화 실패 여부를 함께 확인한다.

## 실행 전 점검

- `GET /actuator/health`가 대상 `-BaseUrl`에서 정상 응답하는지 확인한다.
- `-BaseUrl`의 API와 fixture DB가 같은 성능 환경을 가리키는지 확인한다.
- RDS 실행 전 대상 DB가 성능 전용이고 초기화해도 되는지 확인한다.
- 실행 뒤 생성된 요약본과 원시 `metrics.json`을 함께 보관한다.
