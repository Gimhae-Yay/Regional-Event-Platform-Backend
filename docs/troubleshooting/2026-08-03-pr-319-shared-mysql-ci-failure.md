# PR #319 공유 MySQL 테스트 CI 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | Issue #299 구현의 PR CI가 일시 실패해 회귀 검증과 성능 판정이 지연됨 |
| 최초 확인 시각·시간대 | 2026-08-03 13:41 KST |
| 관련 요구사항·이슈 | Issue #299, PRD FR-01~FR-11 및 AC-01~AC-18 |
| revision·브랜치 | `e0deca4dc2fcc4e778c1b7c3234c8302104de021`, `test/299-shared-mysql-containers` |
| 환경·프로필 | GitHub Actions `ubuntu-latest`, Amazon Corretto 21, MySQL 8.0.42 |

## 기대 결과와 실제 결과

### 기대 결과

`./gradlew --no-daemon clean build`가 공유 MySQL 컨테이너를 한 번 시작하고 전체 테스트를 통과한다.

### 실제 결과

GitHub Actions run 30784997559의 `빌드 및 테스트` 단계가 약 2분 22초 후 실패했다.
`CreateVisitReviewUseCaseMySqlIntegrationTest`부터 `Too many connections`가 발생했고, 이후
`LoginUseCaseMySqlTest`, `RefreshAccessTokenUseCaseIntegrationTest`,
`MySqlDatabaseCleanerIntegrationTest`가 같은 원인으로 실패했다.

## 재현 절차

### 선행 조건

- PR #319 head revision `e0deca4`
- GitHub Actions Docker 사용 가능

### 명령·요청·입력

1. `dev` 대상 PR #319를 생성한다.
2. CI workflow의 `./gradlew --no-daemon clean build`를 실행한다.

### 재현 결과

- 실행 횟수: 1회
- 성공 횟수: 0회
- 실패 횟수: 1회
- 종료 코드·HTTP 상태: GitHub Actions `failure`

## 수집한 증거

- GitHub Actions run 30784997559
- 실패 job 91596766105
- 최초 실패의 직접 원인은 `SharedMySqlTestContainer.openConnection()`에서 발생한
  `java.sql.SQLNonTransientConnectionException: Too many connections`이다.
- CI 종료 시점 로그에서 Hikari pool 번호가 33까지 증가했다.
- 공유 datasource 등록에는 `spring.datasource.hikari.maximum-pool-size` 제한이 없었다.
- 대상 동시성 테스트의 최대 worker 수는 `RefreshAccessTokenUseCaseIntegrationTest`의 3개다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-03 13:41 KST | 관찰 | PR CI 빌드 단계 실패 | JUnit artifact와 로그에 실패 테스트·예외가 존재함 | 4개 클래스 6개 테스트 실패 | 채택 |
| 2026-08-03 13:43 KST | 검증 | 공유 DB 상태 전파 가설 | fixture·제약 관련 예외가 발생함 | 모든 실패의 직접 원인은 `Too many connections` | 기각 |
| 2026-08-03 13:44 KST | 가설 | 테스트 컨텍스트별 Hikari 풀이 공유 MySQL 연결 한도를 소진함 | 풀 제한 부재, 다수 pool, 후반 테스트부터 연결 거부 | 풀 제한 없음, HikariPool-33 관찰, 후반 4개 클래스 실패 | 채택 |
| 2026-08-03 13:44 KST | 변경 | 공통 테스트 datasource 최대 풀 크기를 4로 제한 | 연결 수 초과 없이 최대 worker 3개 테스트를 유지함 | `SharedMySqlTestContainer`에 테스트 전용 상한 적용 | 채택 |
| 2026-08-03 13:47 KST | 검증 | 원래 CI 절차 재실행 | 전체 테스트 성공, 연결 수 초과 미발생 | run 30785254416 attempt 1 성공, 700개 테스트 성공 | 채택 |
| 2026-08-03 13:51 KST | 검증 | 동일 SHA 전체 CI 반복 | 순서 오염과 간헐 실패 없이 재성공 | run 30785254416 attempt 2 성공, 700개 테스트 성공 | 채택 |

## 가설과 검증

### 가설 1: 공유 DB 상태가 테스트 간에 전파됨

- 근거: 개별 컨테이너를 단일 JVM 컨테이너로 전환했다.
- 참일 때의 예측: FK fixture, MySQL 세션 또는 CHECK 제약과 관련된 후속 테스트가 실패한다.
- 반증 조건: 실패가 컴파일·인프라 외부 오류이거나 첫 MySQL 테스트에서 발생한다.
- 검증 방법: JUnit XML의 실패 테스트, 실행 순서와 예외를 확인한다.
- 결과: fixture나 제약 예외가 아니라 MySQL 연결 수 초과가 확인됐다.
- 판정: 기각

### 가설 2: 테스트 컨텍스트별 Hikari 풀이 MySQL 연결 한도를 소진함

- 근거: 개별 컨테이너에서는 클래스마다 연결 한도가 분리됐지만 공유 컨테이너에서는 모든 풀이 한도를 공유한다.
- 참일 때의 예측: 여러 컨텍스트 실행 뒤 후반 테스트부터 새 연결과 Flyway 연결이 거부된다.
- 반증 조건: 첫 공유 컨텍스트부터 실패하거나 datasource pool에 작은 상한이 이미 설정돼 있다.
- 검증 방법: datasource 설정, Hikari pool 수, 최초 실패 시점과 동시성 worker 수를 대조한다.
- 결과: pool 상한이 없고 HikariPool-33까지 생성된 뒤 후반 테스트에서 새 연결이 거부됐다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 여러 MySQL 통합 테스트 컨텍스트가 하나의 MySQL 컨테이너를 순차 사용함
- 결함이 있는 코드·설정·데이터·계약: `SharedMySqlTestContainer`가 테스트 datasource의 Hikari 최대 풀 크기를 제한하지 않음
- 증상으로 이어진 메커니즘: 컨텍스트별 Hikari 풀이 유지되면서 공유 MySQL의 연결 한도를 소진하고 cleaner·Flyway의 신규 연결이 거부됨
- 기존 방어가 막지 못한 이유: #298은 컨테이너 수명주기와 DB 정리만 검증했고, 전체 컨텍스트가 한 서버의 연결 한도를 공유하는 조건은 아직 적용되지 않았음
- 결론의 증거: CI의 `Too many connections`, 후반 테스트 집중 실패, pool 제한 부재와 다수 Hikari pool 로그

## 해결 또는 완화

- 선택한 방법: 공통 테스트 datasource의 Hikari 최대 풀 크기를 최대 동시 worker 3개보다 하나 큰 4로 제한한다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/support/mysql/SharedMySqlTestContainer.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | run 30784997559 실패 | run 30785254416 attempt 1·2 성공 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew --no-daemon clean build` | 로컬 성공, MySQL 테스트 skip | 로컬 Docker 데몬 부재 |
| GitHub Actions `./gradlew --no-daemon clean build` attempt 1 | 성공, 700개·skip 0·failure 0·error 0 | MySQL 시작 1회 |
| GitHub Actions `./gradlew --no-daemon clean build` attempt 2 | 성공, 700개·skip 0·failure 0·error 0 | MySQL 시작 1회 |

## 재발 방지와 문서 반영

공통 MySQL 테스트 datasource가 컨텍스트 수와 무관하게 서버 연결 한도를 소진하지 않도록 최대 풀
크기를 공통 등록한다. 전체 MySQL 테스트를 같은 서버에 연결하는 변경은 단일 클래스 성공만으로
판정하지 않고 전체 CI 반복 결과로 검증한다.

## 잔여 위험과 후속 작업

- 로컬 Docker 데몬 부재로 로컬 MySQL 재현은 수행하지 못했고 GitHub Actions 2회 성공으로 대체했다.
- 풀 크기 4는 현재 최대 worker 3개를 기준으로 하며 동시성 테스트 worker 수가 늘면 함께 재검토해야 한다.

## 관련 자료

- [GitHub Actions run 30784997559](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30784997559)
- [해결 검증 attempt 1](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30785254416/attempts/1)
- [반복 검증 attempt 2](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/30785254416/attempts/2)
- [PR #319](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/pull/319)
- [Issue #299](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/299)
