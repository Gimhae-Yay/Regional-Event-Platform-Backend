# MySQL 멱등성 만료 테스트의 유효하지 않은 TIMESTAMP 값

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #205의 GitHub Actions `빌드 및 테스트` 체크 실패 |
| 최초 확인 시각·시간대 | 2026-07-31 16:35 KST |
| 관련 요구사항·이슈 | RSV-03, #200, PR #205 |
| revision·브랜치 | `424262e`, `feature/200-content-review-target` |
| 환경·프로필 | GitHub Actions, Amazon Corretto 21.0.12, MySQL 8.0.42 Testcontainers, 기본 프로필 |

## 기대 결과와 실제 결과

### 기대 결과

만료된 종결 멱등성 기록만 삭제하고 `PROCESSING` 기록은 보존하는 MySQL 통합 테스트와 전체 빌드가 통과해야 한다.

### 실제 결과

만료 시각을 설정하는 테스트 준비 단계에서 MySQL이 `expires_at = '1970-01-01 00:00:00'`을 거부해 테스트가 본 검증에 도달하지 못했다.

## 재현 절차

### 선행 조건

- Docker를 사용할 수 있는 환경
- MySQL 8.0.42 Testcontainers
- PR #205 head `424262e`

### 명령·요청·입력

1. `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyServiceMySqlTest.만료된_종결_기록만_정리하고_처리_중_기록은_보존한다'`
2. 실패한 SQL과 `expires_at` 입력값을 확인한다.

### 재현 결과

- 실행 횟수: GitHub Actions 2회, 로컬 대상 테스트 1회
- 성공 횟수: GitHub Actions 1회
- 실패 횟수: GitHub Actions 1회
- 종료 코드·HTTP 상태: 수정 전 GitHub Actions 종료 코드 1, 수정 후 종료 코드 0, 로컬은 Docker 데몬 부재로 1건 skip

## 수집한 증거

- GitHub Actions run `30612368321`은 185개 테스트 중 1개가 실패했다.
- 실패 테스트는 `IdempotencyServiceMySqlTest.만료된_종결_기록만_정리하고_처리_중_기록은_보존한다`이다.
- 테스트 결과 artifact에는 `Incorrect datetime value: '1970-01-01 00:00:00' for column 'expires_at'`가 기록됐다.
- 실패 위치는 테스트의 직접 SQL 갱신이며 `Timestamp.from(Instant.EPOCH)`을 전달한다.
- `expires_at`의 실제 스키마 타입은 `TIMESTAMP(6) NOT NULL`이다.
- PR #205의 변경 파일 6개에는 멱등성 운영 코드, 테스트, 마이그레이션이 포함되지 않는다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-31 16:35 KST | 관찰 | Actions 로그와 테스트 artifact 확인 | 실패 SQL과 DB 오류가 특정된다. | `Instant.EPOCH`의 MySQL `TIMESTAMP` 저장 실패를 확인했다. | 채택 |
| 2026-07-31 16:35 KST | 가설 | 테스트 데이터가 DB 시간 범위를 위반했다. | 유효한 과거 시각이면 같은 정리 계약을 검증하면서 준비 SQL이 성공한다. | artifact의 MySQL 오류와 실제 스키마·테스트 입력을 대조했다. | 채택 |
| 2026-07-31 16:36 KST | 검증 | 원래 대상 테스트를 로컬에서 실행했다. | Docker가 사용 가능하면 MySQL 8.0에서 같은 오류가 발생한다. | Docker 데몬 부재로 Testcontainers 테스트 1건이 skip됐다. | 기각 |
| 2026-07-31 16:37 KST | 변경 | 테스트 Clock과 만료 시각을 같은 고정 기준으로 맞췄다. | `NOW - 1초`는 MySQL 저장 범위 안이면서 정리 기준보다 과거다. | 테스트 컴파일과 Docker 비의존 회귀를 포함한 전체 빌드가 통과했다. | 채택 |
| 2026-07-31 16:40 KST | 검증 | 수정 커밋의 GitHub Actions를 재실행했다. | MySQL 8.0.42에서 대상 테스트와 전체 빌드가 통과한다. | CI run `30613533039`가 성공했다. | 채택 |

## 가설과 검증

### 가설 1: 테스트의 만료 시각이 MySQL TIMESTAMP 범위를 위반한다

- 근거: MySQL 8.0.42가 테스트 준비 SQL의 `1970-01-01 00:00:00` 값을 거부했다.
- 참일 때의 예측: 서비스 기준 시각보다 과거이면서 MySQL이 저장할 수 있는 시각을 사용하면 정리 대상 한 건만 삭제된다.
- 반증 조건: 유효한 과거 시각으로 바꾼 뒤에도 같은 데이터 변환 오류가 발생하거나 정리 계약이 실패한다.
- 검증 방법: 원래 테스트를 먼저 재현하고 단일 입력값만 변경해 동일 테스트와 전체 빌드를 실행한다.
- 결과: CI artifact에서 입력값과 데이터 변환 실패를 확인했고, 스키마·테스트 소스 대조 결과 운영 코드가 아닌 테스트 준비 데이터가 계약을 위반했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: MySQL 통합 테스트에서 종결 기록을 강제로 만료시키는 준비 SQL 실행
- 결함이 있는 코드·설정·데이터·계약: `IdempotencyServiceMySqlTest`가 `TIMESTAMP(6)` 컬럼에 `Instant.EPOCH`을 넣었고, 서비스에 시스템 Clock을 주입해 테스트 상수 `NOW`와 시간 기준도 일치하지 않았다.
- 증상으로 이어진 메커니즘: JDBC가 epoch를 `1970-01-01 00:00:00`으로 전달하고 MySQL 8.0.42가 유효한 `TIMESTAMP` 값이 아니라고 거부해 정리 로직 호출 전에 테스트가 중단됐다.
- 기존 방어가 막지 못한 이유: 해당 테스트는 Docker를 사용할 수 없는 로컬 환경에서 `disabledWithoutDocker = true`로 skip돼 MySQL 방언 오류가 PR 전 로컬 빌드에서 드러나지 않았다.
- 결론의 증거: Actions test artifact의 실제 SQL 예외, `V1__initial_p0_schema.sql`의 `TIMESTAMP(6)` 정의, 실패 라인의 `Timestamp.from(Instant.EPOCH)` 입력이 일치한다.

## 해결 또는 완화

- 선택한 방법: 테스트 Clock을 `NOW`로 고정하고 만료 레코드의 시각을 `NOW.minusSeconds(1)`로 설정한다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/domain/idempotency/service/IdempotencyServiceMySqlTest.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | GitHub Actions에서 MySQL 데이터 변환 실패 | MySQL 8.0.42를 사용하는 GitHub Actions 전체 빌드 성공 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 대상 MySQL 통합 테스트 | 로컬 skip, 원격 성공 | 로컬 Docker 데몬 부재, GitHub Actions MySQL 8.0.42에서 검증 |
| `./gradlew build` | 성공 | 전체 회귀, MySQL Testcontainers 테스트는 skip |
| GitHub Actions run `30613533039` | 성공 | MySQL 8.0.42를 포함한 전체 빌드 및 테스트 |
| `git diff --check` | 성공 | 변경 정합성 |

## 재발 방지와 문서 반영

테스트 데이터가 실제 MySQL 컬럼 범위를 따르도록 하고 서비스와 테스트의 시간 기준을 고정 Clock 하나로 통일했다.

## 잔여 위험과 후속 작업

기능상 잔여 위험은 확인되지 않았다. Actions의 Node.js 20 deprecation 경고는 이번 테스트 실패 및 수정 범위와 무관하다.

## 관련 자료

- GitHub Actions run `30612368321`
- GitHub Actions run `30613533039`
- PR #205
