# MySQL 멱등성 만료 정리 테스트의 시간 경계 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 원인 확인 |
| 영향 | PR #204 GitHub Actions `빌드 및 테스트` 실패로 병합 검증이 차단됨 |
| 최초 확인 시각·시간대 | 2026-07-31 16:35 KST |
| 관련 요구사항·이슈 | PR #204, 이슈 #183, PRD `RSV-03` |
| revision·브랜치 | `bfa729cddb942ce4a704c42d5050bdf6ccfde96a`, `feature/183-content-session-review-status` |
| 환경·프로필 | GitHub Actions Amazon Corretto 21.0.12 / 로컬 Amazon Corretto 21.0.11, MySQL 8.0.42 Testcontainers |

## 기대 결과와 실제 결과

### 기대 결과

고정 시각 `2026-08-02T00:00:00Z`보다 과거인 종결 멱등성 기록만 삭제하고, 처리 중인 기록은 보존하며 전체 빌드가 통과해야 한다.

### 실제 결과

GitHub Actions에서 `IdempotencyServiceMySqlTest.만료된_종결_기록만_정리하고_처리_중_기록은_보존한다`가 `expires_at` 갱신 중 `MysqlDataTruncation`으로 실패해 190개 테스트 중 1개가 실패했다.

## 재현 절차

### 선행 조건

- Docker 실행 가능
- MySQL 8.0.42 Testcontainers 이미지 사용 가능
- `feature/183-content-session-review-status`의 `bfa729c` revision

### 명령·요청·입력

1. `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyServiceMySqlTest.만료된_종결_기록만_정리하고_처리_중_기록은_보존한다'`
2. 테스트가 `expires_at`에 전달하는 `Timestamp.from(Instant.EPOCH)`의 MySQL 처리 결과를 확인한다.

### 재현 결과

- 실행 횟수: CI 1회, 로컬 1회
- 성공 횟수: 0회
- 실패 횟수: CI 1회
- 종료 코드·HTTP 상태: CI 종료 코드 1, 로컬은 Docker 부재로 대상 테스트 1건 skip

## 수집한 증거

- GitHub Actions run `30612073295`는 `IdempotencyServiceMySqlTest.java:231`에서 `DataIntegrityViolationException`과 원인 `MysqlDataTruncation`을 보고했다.
- 실패 입력은 `Timestamp.from(Instant.EPOCH)`이고 대상 컬럼은 V1 스키마의 `TIMESTAMP(6) NOT NULL`이다.
- 테스트는 `NOW = 2026-08-02T00:00:00Z`로 Clock을 고정하므로 DB 표현 범위 안의 과거 시각을 만들 수 있다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-31 16:35 KST | 관찰 | GitHub Actions 로그 확인 | 실패 테스트와 예외 위치가 특정되어야 한다. | 멱등성 만료 정리 테스트의 231행에서 `MysqlDataTruncation` 확인 | 채택 |
| 2026-07-31 16:35 KST | 가설 | `Instant.EPOCH`이 MySQL `TIMESTAMP` 표현 경계와 충돌한다. | 동일 테스트가 로컬 MySQL 8.0.42에서도 같은 입력에서 실패하고, 유효한 과거 시각으로만 바꾸면 통과해야 한다. | 검증 대기 | 대기 |
| 2026-07-31 16:35 KST | 가설 | PR #204의 V10 회차 마이그레이션이 멱등성 테이블을 손상시킨다. | V10이 `idempotency_record` 또는 해당 시간 컬럼을 변경한 증거가 있어야 한다. | V10 변경 대상은 `content_session`이며 V1의 멱등성 컬럼 정의는 유지됨 | 기각 |
| 2026-07-31 16:36 KST | 검증 | CI 테스트 결과 artifact 확인 | JDBC 원인 메시지에 거부된 실제 값이 기록되어야 한다. | MySQL 8.0.42가 `1970-01-01 00:00:00`을 `Incorrect datetime value`로 거부한 XML 확인 | 채택 |
| 2026-07-31 16:36 KST | 검증 | 로컬 단일 테스트 실행 | Docker가 있으면 동일 MySQL 조건에서 재현되어야 한다. | Docker daemon 부재로 Testcontainers 테스트 1건 skip | 대기 |
| 2026-07-31 16:37 KST | 변경 | 만료 입력을 `NOW.minusSeconds(1)`로 교체 | 만료 조건을 유지하면서 MySQL 표현 범위를 벗어나지 않아야 한다. | 테스트 픽스처 한 줄만 변경 | 채택 |
| 2026-07-31 16:37 KST | 검증 | 단일 테스트, 전체 build, diff 검사 | 로컬 검증이 통과하고 무관한 변경이 없어야 한다. | 단일 테스트는 Docker 부재로 skip, build와 diff 검사 통과 | 채택 |

## 가설과 검증

### 가설 1: MySQL TIMESTAMP 표현 범위 밖의 테스트 데이터

- 근거: `expires_at TIMESTAMP(6)`에 Unix epoch를 기록할 때 MySQL JDBC가 `MysqlDataTruncation`을 반환했다.
- 참일 때의 예측: `Instant.EPOCH` 입력은 실패하고, 고정 Clock보다 과거이면서 MySQL 표현 범위 안인 입력은 삭제 계약을 동일하게 검증하며 통과한다.
- 반증 조건: 같은 MySQL 8.0.42 환경에서 epoch 입력이 정상 저장되거나 유효한 과거 입력도 같은 예외로 실패한다.
- 검증 방법: 원래 단일 테스트를 재현한 뒤 입력만 `NOW.minusSeconds(1)`로 바꾸어 같은 테스트를 재실행한다.
- 결과: CI artifact에서 MySQL 8.0.42가 epoch 값을 직접 거부한 사실을 확인했다. 수정 후 동일 MySQL 검증은 PR CI 재실행이 필요하다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 만료된 종결 기록을 만들기 위해 테스트가 `TIMESTAMP(6)` 컬럼에 `1970-01-01T00:00:00Z`를 기록했다.
- 결함이 있는 코드·설정·데이터·계약: 운영 코드가 아니라 `IdempotencyServiceMySqlTest`의 DB 표현 범위를 고려하지 않은 시간 픽스처다.
- 증상으로 이어진 메커니즘: MySQL 8.0.42가 해당 값을 `Incorrect datetime value`로 거부해 정리 로직 호출 전에 JDBC 갱신이 실패했다.
- 기존 방어가 막지 못한 이유: 로컬 Docker가 없는 환경에서는 `disabledWithoutDocker = true` 때문에 MySQL 통합 테스트가 skip되어 전체 build 성공만으로 픽스처의 DB 호환성을 검증하지 못했다.
- 결론의 증거: CI 테스트 XML의 실제 거부 값·컬럼·예외, V1의 `TIMESTAMP(6)` 정의, 테스트 소스의 `Timestamp.from(Instant.EPOCH)`가 일치한다.

## 해결 또는 완화

- 선택한 방법: 고정 Clock보다 1초 과거인 `NOW.minusSeconds(1)`을 사용해 만료 의미를 유지하면서 DB 표현 범위 안의 값을 제공한다.
- 변경 파일: `src/test/java/io/regionevent/regioneventbackend/domain/idempotency/service/IdempotencyServiceMySqlTest.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | CI MySQL 8.0.42에서 `Incorrect datetime value: '1970-01-01 00:00:00'` | PR CI 재실행 대기 | 원인 확인 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 단일 MySQL 통합 테스트 | skip | 로컬 Docker daemon 부재, PR CI에서 검증 필요 |
| `./gradlew build` | 통과 | MySQL Testcontainers 테스트는 로컬에서 skip |
| `git diff --check` | 통과 | 변경 정합성 |

## 재발 방지와 문서 반영

테스트 픽스처가 실제 DB 컬럼 표현 범위와 고정 Clock의 만료 의미를 함께 만족하도록 고정 시각 기준 상대값을 사용했다.

## 잔여 위험과 후속 작업

로컬에 Docker daemon이 없어 수정 후 동일 MySQL 8.0.42 실행은 PR CI 재실행으로 확인해야 한다. CI 통과 전에는 상태를 `해결`로 올리지 않는다.

## 관련 자료

- GitHub Actions run `30612073295`
- GitHub Actions test report artifact `8785877698`
- `src/test/java/io/regionevent/regioneventbackend/domain/idempotency/service/IdempotencyServiceMySqlTest.java`
- `src/main/resources/db/migration/V1__initial_p0_schema.sql`
