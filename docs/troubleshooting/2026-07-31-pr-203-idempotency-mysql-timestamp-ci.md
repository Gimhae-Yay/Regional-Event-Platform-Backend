# PR #203 멱등 기록 정리 MySQL 테스트 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #203의 GitHub Actions `빌드 및 테스트`가 실패해 병합할 수 없다. |
| 최초 확인 시각·시간대 | 2026-07-31 16:31 KST |
| 관련 요구사항·이슈 | ADR-0048, #182, PR #203 |
| revision·브랜치 | `04b1bcd1793f54fda7b6c039bea7cdf334d76558`, `feature/182-region-admin-authorization` |
| 환경·프로필 | GitHub Actions Amazon Corretto 21.0.12, MySQL 8.0.42 Testcontainers, 기본 프로필, 수정 전 `Clock.systemUTC()` |

## 기대 결과와 실제 결과

### 기대 결과

`IdempotencyServiceMySqlTest`는 만료된 종결 기록만 삭제하고 `PROCESSING` 기록은 보존하는 ADR-0048의 정리
정책을 MySQL에서 검증하며, PR #203의 전체 빌드는 통과해야 한다.

### 실제 결과

최초 실행에서는 `만료된_종결_기록만_정리하고_처리_중_기록은_보존한다()`가 `expires_at`을 갱신하는 준비
단계에서 `MysqlDataTruncation: Incorrect datetime value: '1970-01-01 00:00:00'`로 실패했다. 유효한
`NOW.minusSeconds(1)`로 교체한 재실행에서는 데이터 저장은 성공했지만 삭제 건수가 0이었다. 테스트의 `NOW`는
2026-08-02인 반면 주입된 `Clock.systemUTC()`는 실행 시각인 2026-07-31을 반환해, 새 값도 삭제 기준에는
미래였기 때문이다. PR #203의 지역 관리자 인가 변경 코드는 실행되기 전에 무관한 멱등성 통합 테스트가 중단된다.

## 재현 절차

### 선행 조건

- Docker를 사용할 수 있어야 한다.
- MySQL 8.0.42 Testcontainers를 실행한다.
- 테스트의 고정 시각은 `2026-08-02T00:00:00Z`이다.

### 명령·요청·입력

1. `./gradlew test --tests 'io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyServiceMySqlTest.만료된_종결_기록만_정리하고_처리_중_기록은_보존한다'`
2. `expires_at` 준비 값으로 `Timestamp.from(Instant.EPOCH)`을 사용한다.

### 재현 결과

- 실행 횟수: GitHub Actions 2회, 로컬 1회
- 성공 횟수: 로컬 1회 스킵
- 실패 횟수: GitHub Actions 2회
- 종료 코드·HTTP 상태: GitHub Actions `./gradlew --no-daemon clean build` 종료 코드 1

## 수집한 증거

- GitHub Actions run `30611504848`, job `91095092119`에서 190개 테스트 중 1개만 실패했다.
- 업로드된 JUnit XML은 실패 SQL을
  `UPDATE idempotency_record SET expires_at = ? ...`로 기록했다.
- 동일 XML은 바인딩된 값이 `1970-01-01 00:00:00`이며 MySQL이 잘못된 `TIMESTAMP` 값으로 거부했다고 기록했다.
- 재실행 run `30613494008`에서는 `MysqlDataTruncation`이 사라지고 삭제 건수 기대값 1, 실제값 0으로
  실패 지점이 이동했다.
- 실패 위치는 운영 코드가 아니라
  `IdempotencyServiceMySqlTest.java:231`의 `Timestamp.from(Instant.EPOCH)` 테스트 준비 구문이다.
- `idempotency_record.expires_at`의 실제 스키마 타입은 `TIMESTAMP(6) NOT NULL`이다.
- 테스트 데이터는 `NOW = 2026-08-02T00:00:00Z`를 사용하지만 테스트용 Bean은 고정 시계가 아니라
  `Clock.systemUTC()`를 반환한다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-07-31 16:31 KST | 관찰 | PR #203 Actions 로그와 테스트 아티팩트 확인 | 실패 테스트·SQL·DB 예외를 특정한다. | `Instant.EPOCH`을 `TIMESTAMP(6)`에 기록하는 준비 단계에서 실패했다. | 채택 |
| 2026-07-31 16:35 KST | 가설 | 만료를 표현하는 테스트 값이 MySQL `TIMESTAMP` 범위를 벗어난다. | 고정 `NOW` 이전의 유효한 값은 준비 구문을 통과하고 정리 정책 검증까지 진행된다. | 원격 예외, 스키마와 테스트 입력이 예측에 일치한다. | 채택 |
| 2026-07-31 16:36 KST | 검증 | 로컬 대상 테스트와 Docker 상태 확인 | Docker가 있으면 MySQL에서 재현되고, 없으면 스킵 여부를 구분한다. | 로컬 Docker 데몬에 연결할 수 없어 대상 테스트 1개가 스킵됐다. | 채택 |
| 2026-07-31 16:37 KST | 변경 | 만료 입력을 `NOW.minusSeconds(1)`로 교체 | 테스트 시계도 `NOW`로 고정돼 있다면 정책상 만료됐고 MySQL이 수용 가능한 시각이 된다. | 관련 저장소 테스트와 로컬 전체 빌드는 통과했지만 Docker 테스트는 스킵됐다. | 부분 채택 |
| 2026-07-31 16:39 KST | 검증 | 수정 후 Actions run `30613494008` 확인 | 데이터 절단이 사라지고 삭제 정책까지 통과한다. | 데이터 절단은 사라졌으나 삭제 건수는 0이었다. | 부분 채택 |
| 2026-07-31 16:41 KST | 가설 | 테스트 시계와 `NOW` 기반 데이터가 서로 다른 시간축을 사용한다. | `Clock.systemUTC()`의 현재는 `NOW.minusSeconds(1)`보다 이르므로 삭제되지 않는다. | Actions 실행일은 2026-07-31이고 `NOW`는 2026-08-02라 예측과 일치한다. | 채택 |
| 2026-07-31 16:44 KST | 검증 | 시계 고정 후 Actions run `30613755161` 확인 | MySQL 통합 테스트와 전체 빌드가 통과한다. | `./gradlew --no-daemon clean build`가 1분 9초에 성공했다. | 채택 |

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

### 가설 2: 테스트 데이터와 서비스가 서로 다른 시간축을 사용한다

- 근거: 테스트 픽스처는 `NOW`를 사용하지만 `ClockConfiguration`은 `Clock.systemUTC()`를 반환한다.
- 참일 때의 예측: 2026-07-31 실행에서 `NOW.minusSeconds(1)`은 서비스의 삭제 기준보다 미래여서
  `deleteExpiredTerminalRecords()`가 0을 반환한다.
- 반증 조건: 서비스의 주입 시계가 `NOW`로 고정돼 있거나 저장된 `expires_at`이 실제 실행 시각보다 과거다.
- 검증 방법: 테스트 설정과 Actions 실행 시각을 대조하고, 테스트 시계를 `NOW`로 고정해 동일 Actions를 재실행한다.
- 결과: 설정과 실행 시각이 예측과 일치했고, 시계를 고정한 Actions run `30613755161`이 통과했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: MySQL 8.0.42에서 실행일과 다른 `NOW` 상수를 사용하는 만료 종결 기록 테스트를 실행한다.
- 결함이 있는 코드·설정·데이터·계약: 테스트는 `TIMESTAMP(6)` 컬럼에
  `Timestamp.from(Instant.EPOCH)`을 입력했고, 픽스처에는 고정 `NOW`를 쓰면서 서비스에는
  `Clock.systemUTC()`를 주입해 두 시간축을 혼용했다.
- 증상으로 이어진 메커니즘: 최초 값은 MySQL 유효 범위를 벗어나 데이터 준비 단계에서 예외가 발생했다.
  유효 범위 값으로만 바꾸면 `NOW.minusSeconds(1)`이 실제 시스템 시각보다 미래여서 종결 기록이 삭제 조건에
  포함되지 않는다.
- 기존 방어가 막지 못한 이유: H2 기반 테스트와 Docker가 없는 로컬 빌드에서는 MySQL 통합 테스트가 스킵돼
  MySQL 타입 범위 차이를 검출하지 못했다.
- 결론의 증거: 첫 Actions JUnit XML의 SQL·입력·예외, 두 번째 Actions JUnit XML의 삭제 건수 0,
  V1의 `TIMESTAMP(6)` 스키마와 `Clock.systemUTC()` 설정이 두 단계 실패를 모두 설명한다.

## 해결 또는 완화

- 선택한 방법: 운영 코드와 24시간 보관 계약은 유지한다. 테스트 만료 입력을 고정 `NOW`의 1초 전으로 바꾸고,
  서비스에도 `Clock.fixed(NOW, ZoneOffset.UTC)`를 주입해 하나의 결정적 시간축을 사용한다.
- 변경 파일:
  - `src/test/java/io/regionevent/regioneventbackend/domain/idempotency/service/IdempotencyServiceMySqlTest.java`
  - `docs/troubleshooting/2026-07-31-pr-203-idempotency-mysql-timestamp-ci.md`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | 첫 Actions에서 `MysqlDataTruncation`, 두 번째 Actions에서 삭제 건수 0 | 세 번째 Actions에서 전체 빌드 성공 | 통과 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| 대상 MySQL 통합 테스트 | 스킵 | 로컬 Docker 데몬에 연결할 수 없음 |
| `IdempotencyRecordRepositoryTest` | 성공 | 관련 저장소 회귀 |
| `./gradlew build` | 성공 | Docker 의존 테스트 5개 스킵 |
| `git diff --check` | 성공 | 변경 정합성 |
| Actions run `30613755161`의 `./gradlew --no-daemon clean build` | 성공 | MySQL 8.0.42 Testcontainers 포함 |

## 재발 방지와 문서 반영

DB 시각 경계를 테스트 목적과 무관한 임의 최솟값으로 표현하지 않고, 테스트가 주입한 고정 `Clock`을 기준으로
상대적인 과거·미래 시각을 사용한다.

## 잔여 위험과 후속 작업

로컬에서는 Docker 의존 테스트 5개가 스킵됐지만 GitHub Actions의 MySQL 8.0.42 전체 빌드로 보완했다.
현재 확인된 잔여 기능 위험은 없다.

## 관련 자료

- GitHub Actions run `30611504848`
- GitHub Actions run `30613494008`
- GitHub Actions run `30613755161`
- `docs/adr/0048-retain-terminal-idempotency-records-for-24-hours.md`
