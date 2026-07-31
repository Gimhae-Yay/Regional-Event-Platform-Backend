# PR #203 멱등 기록 정리 MySQL 테스트 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 원인 확인 |
| 영향 | PR #203의 GitHub Actions `빌드 및 테스트`가 실패해 병합할 수 없다. |
| 최초 확인 시각·시간대 | 2026-07-31 16:31 KST |
| 관련 요구사항·이슈 | ADR-0048, #182, PR #203 |
| revision·브랜치 | `04b1bcd1793f54fda7b6c039bea7cdf334d76558`, `feature/182-region-admin-authorization` |
| 환경·프로필 | GitHub Actions Amazon Corretto 21.0.12, MySQL 8.0.42 Testcontainers, 기본 프로필 |

## 기대 결과와 실제 결과

### 기대 결과

`IdempotencyServiceMySqlTest`는 만료된 종결 기록만 삭제하고 `PROCESSING` 기록은 보존하는 ADR-0048의 정리
정책을 MySQL에서 검증하며, PR #203의 전체 빌드는 통과해야 한다.

### 실제 결과

`만료된_종결_기록만_정리하고_처리_중_기록은_보존한다()`가 `expires_at`을 갱신하는 준비 단계에서
`MysqlDataTruncation: Incorrect datetime value: '1970-01-01 00:00:00'`로 실패한다. PR #203의 지역 관리자
인가 변경 코드는 실행되기 전에 무관한 멱등성 통합 테스트가 중단된다.

## 재현 절차

### 선행 조건

- Docker를 사용할 수 있어야 한다.
- MySQL 8.0.42 Testcontainers를 실행한다.
- 테스트의 고정 시각은 `2026-08-02T00:00:00Z`이다.

### 명령·요청·입력

1. `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyServiceMySqlTest.만료된_종결_기록만_정리하고_처리_중_기록은_보존한다'`
2. `expires_at` 준비 값으로 `Timestamp.from(Instant.EPOCH)`을 사용한다.

### 재현 결과

- 실행 횟수: GitHub Actions 1회, 로컬 1회
- 성공 횟수: 로컬 1회 스킵
- 실패 횟수: GitHub Actions 1회
- 종료 코드·HTTP 상태: GitHub Actions `./gradlew --no-daemon clean build` 종료 코드 1

## 수집한 증거

- GitHub Actions run `30611504848`, job `91095092119`에서 190개 테스트 중 1개만 실패했다.
- 업로드된 JUnit XML은 실패 SQL을
  `UPDATE idempotency_record SET expires_at = ? ...`로 기록했다.
- 동일 XML은 바인딩된 값이 `1970-01-01 00:00:00`이며 MySQL이 잘못된 `TIMESTAMP` 값으로 거부했다고 기록했다.
- 실패 위치는 운영 코드가 아니라
  `IdempotencyServiceMySqlTest.java:231`의 `Timestamp.from(Instant.EPOCH)` 테스트 준비 구문이다.
- `idempotency_record.expires_at`의 실제 스키마 타입은 `TIMESTAMP(6) NOT NULL`이다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-31 16:31 KST | 관찰 | PR #203 Actions 로그와 테스트 아티팩트 확인 | 실패 테스트·SQL·DB 예외를 특정한다. | `Instant.EPOCH`을 `TIMESTAMP(6)`에 기록하는 준비 단계에서 실패했다. | 채택 |
| 2026-07-31 16:35 KST | 가설 | 만료를 표현하는 테스트 값이 MySQL `TIMESTAMP` 범위를 벗어난다. | 고정 `NOW` 이전의 유효한 값은 준비 구문을 통과하고 정리 정책 검증까지 진행된다. | 원격 예외, 스키마와 테스트 입력이 예측에 일치한다. | 채택 |
| 2026-07-31 16:36 KST | 검증 | 로컬 대상 테스트와 Docker 상태 확인 | Docker가 있으면 MySQL에서 재현되고, 없으면 스킵 여부를 구분한다. | 로컬 Docker 데몬에 연결할 수 없어 대상 테스트 1개가 스킵됐다. | 채택 |
| 2026-07-31 16:37 KST | 변경 | 만료 입력을 `NOW.minusSeconds(1)`로 교체 | 정책상 만료됐고 MySQL이 수용 가능한 결정적 시각이 된다. | 관련 저장소 테스트와 전체 빌드가 통과했다. MySQL 실행은 Actions 확인이 필요하다. | 채택 |

## 가설과 검증

### 가설 1: 만료 테스트 데이터가 MySQL TIMESTAMP 범위를 벗어난다

- 근거: MySQL 예외가 `1970-01-01 00:00:00`을 직접 거부하며, 스키마는 `TIMESTAMP(6)`이다.
- 참일 때의 예측: 현재 테스트는 동일 예외로 재현되고 `NOW.minusSeconds(1)`처럼 고정 현재 시각보다 과거이면서
  MySQL에서 유효한 값으로 바꾸면 삭제 검증이 통과한다.
- 반증 조건: 현재 테스트가 같은 환경에서 통과하거나, 유효한 과거 시각으로 바꿔도 동일한 DB 예외가 발생한다.
- 검증 방법: 대상 MySQL 통합 테스트를 변경 전후 동일 명령으로 실행한다.
- 결과: GitHub Actions의 MySQL 예외와 `TIMESTAMP(6)` 스키마가 입력 범위 결함을 입증했다. 로컬 Docker가
  없어 MySQL 실행은 스킵됐으며, 수정 후 실제 MySQL 검증은 Actions 재실행이 필요하다.
- 판정: 채택

## 근본 원인

- 촉발 조건: MySQL 8.0.42에서 만료된 종결 기록 테스트를 실행한다.
- 결함이 있는 코드·설정·데이터·계약: 테스트가 `TIMESTAMP(6)` 컬럼에 만료를 표현하려고
  `Timestamp.from(Instant.EPOCH)`을 입력한다.
- 증상으로 이어진 메커니즘: MySQL이 `1970-01-01 00:00:00`을 `expires_at`의 유효한 `TIMESTAMP`로
  저장하지 못해, 정리 메서드를 호출하기 전 테스트 데이터 준비 단계에서 예외가 발생한다.
- 기존 방어가 막지 못한 이유: H2 기반 테스트와 Docker가 없는 로컬 빌드에서는 MySQL 통합 테스트가 스킵돼
  MySQL 타입 범위 차이를 검출하지 못했다.
- 결론의 증거: Actions JUnit XML의 실제 SQL·입력·예외, V1의 `TIMESTAMP(6)` 스키마, 테스트 231행의
  `Instant.EPOCH`이 모두 같은 실패 경로를 가리킨다.

## 해결 또는 완화

- 선택한 방법: 운영 코드와 24시간 보관 계약은 유지하고, 테스트의 만료 입력만 고정 `NOW`의 1초 전으로 바꾼다.
- 변경 파일:
  - `src/test/java/io/regionevent/regioneventbackend/domain/idempotency/service/IdempotencyServiceMySqlTest.java`
  - `docs/troubleshooting/2026-07-31-pr-203-idempotency-mysql-timestamp-ci.md`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | GitHub Actions에서 `MysqlDataTruncation` | 로컬 Docker 부재로 스킵, Actions 재실행 전 | 대기 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 대상 MySQL 통합 테스트 | 스킵 | 로컬 Docker 데몬에 연결할 수 없음 |
| `IdempotencyRecordRepositoryTest` | 성공 | 관련 저장소 회귀 |
| `./gradlew build` | 성공 | Docker 의존 테스트 5개 스킵 |
| `git diff --check` | 성공 | 변경 정합성 |

## 재발 방지와 문서 반영

DB 시각 경계를 테스트 목적과 무관한 임의 최솟값으로 표현하지 않고, 테스트가 주입한 고정 `Clock`을 기준으로
상대적인 과거·미래 시각을 사용한다.

## 잔여 위험과 후속 작업

수정 후 GitHub Actions의 MySQL 8.0.42 실행으로 원래 실패가 사라지는지 확인해야 한다. 로컬 전체 빌드는
성공했지만 Docker 의존 테스트 5개는 스킵됐다.

## 관련 자료

- GitHub Actions run `30611504848`
- `docs/adr/0048-retain-terminal-idempotency-records-for-24-hours.md`
