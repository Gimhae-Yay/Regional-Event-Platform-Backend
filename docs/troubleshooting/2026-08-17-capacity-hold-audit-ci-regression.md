# 홀드 종결 감사 변경 후 Testcontainers shard 1 회귀

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #882의 Testcontainers shard 1 실패로 CI 미통과 |
| 최초 확인 시각·시간대 | 2026-08-17 00:44 KST |
| 관련 요구사항·이슈 | Issue #874, 활성 홀드 종결과 감사 단일 기록 |
| revision·브랜치 | `47c7e6f6`, `feature/874-approve-content-withdrawal` |
| 환경·프로필 | GitHub Actions Ubuntu, Java 21, Gradle `containerTestShard1` |

## 기대 결과와 실제 결과

### 기대 결과

`./gradlew --no-daemon clean containerTestShard1`의 기존 콘텐츠 중단·종료 및 동시성 회귀 테스트가 모두 통과해야 한다.

### 실제 결과

기존 콘텐츠 중단·종료 경로의 감사 이벤트 개수 검증을 포함한 테스트 8건이 실패했다.

## 재현 절차

### 선행 조건

- PR #882의 head `47c7e6f6`
- Docker 및 저장소의 MySQL Testcontainers 실행 환경

### 명령·요청·입력

1. `./gradlew --no-daemon clean containerTestShard1`

### 재현 결과

- 로컬 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle 종료 코드 1, 대상 24건 중 8건 실패

## 수집한 증거

- 실패 workflow run: `31956201654`
- 실패 job: `Testcontainers 테스트 (shard 1)`
- 실패 범위: `SuspendContentControllerMySqlIntegrationTest`, `EndContentReservationsUseCaseMySqlTest`, `SuspendContentConcurrencyMySqlTest`
- CI artifact의 7개 실패는 콘텐츠 감사 1건을 기대했지만 콘텐츠 감사와 홀드 감사가 함께 조회돼 실제 2건이었다.
- 동시 중단 실패는 콘텐츠 성공 감사 actor link 1건을 기대했지만 두 홀드 감사 actor link까지 포함해 실제 3건이었다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·반증 조건 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-17 00:44 KST | 관찰 | CI 실패 체크와 영향 테스트를 확인한다 | shard 1에만 실패가 집중된다 | shard 1 실패, 빠른 테스트와 shard 2 성공 | 채택 |
| 2026-08-17 00:48 KST | 검증 | CI 테스트 artifact의 assertion 실제값을 확인한다 | 감사 이벤트 또는 actor link가 계약만큼 추가됐다면 기대값보다 홀드 수만큼 많다 | 감사 이벤트 `1 → 2`, actor link `1 → 3` 확인 | 채택 |
| 2026-08-17 00:52 KST | 재현 | 실패한 세 테스트 클래스만 실행한다 | CI와 같은 8건이 실패한다 | 24건 중 같은 8건 실패 | 채택 |
| 2026-08-17 00:56 KST | 변경·검증 | 콘텐츠 감사 검증에 대상 유형을 추가하고 actor link 범위를 콘텐츠 감사로 제한한다 | 필수 홀드 감사를 유지한 채 24건이 통과한다 | 24건 전부 성공 | 채택 |
| 2026-08-17 01:19 KST | 회귀 검증 | CI와 같은 clean shard 1 전체를 실행한다 | 213건이 모두 통과한다 | 213건 성공, 실패·오류·건너뜀 0건 | 채택 |

## 가설과 검증

### 가설 1: 홀드 감사 이벤트가 중복 기록된다

- 근거: 기존 테스트가 기대한 감사 이벤트 수보다 실제 수가 한 건 많았다.
- 참일 때의 예측: 추가 이벤트도 `CAPACITY_HOLD` 대상 유형과 같은 홀드 식별자를 가진다.
- 반증 조건: 추가 이벤트가 콘텐츠 감사와 동일한 대상 유형·상태 전이를 가진다.
- 검증 방법: 감사 계약, 변경 코드와 CI artifact의 실패 조건을 함께 확인한다.
- 결과: 추가 이벤트는 계약상 필요한 `CAPACITY_HOLD` 감사였고 콘텐츠 감사와 대상 유형이 달랐다.
- 판정: 기각

### 가설 2: 기존 테스트가 감사 대상을 숫자 식별자만으로 구분한다

- 근거: 실패 fixture에서 콘텐츠 ID와 홀드 ID가 각각의 테이블에서 같은 숫자였고, 7개 assertion은 `targetId`만 필터링했다. 동시성 assertion은 모든 대상 유형의 actor link를 전역 집계했다.
- 참일 때의 예측: `targetType = CONTENT`를 함께 적용하면 콘텐츠 감사 단일 실행 검증은 유지되고 필수 홀드 감사와 충돌하지 않는다.
- 반증 조건: 대상 유형을 추가해도 기대 개수와 실제 개수가 다르다.
- 검증 방법: 세 테스트의 감사 조회 범위만 좁힌 뒤 동일 24건과 shard 1 전체를 재실행한다.
- 결과: 대상 24건과 shard 1 전체 213건이 모두 통과했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 결제가 없는 종결 홀드에도 계약된 `CAPACITY_HOLD` 감사를 기록하도록 공통 종료 유스케이스를 보완했다.
- 결함이 있는 코드·설정·데이터·계약: 기존 콘텐츠 중단·종료 테스트가 서로 다른 테이블의 숫자 ID가 겹칠 수 있는데도 감사 대상을 `targetId`만으로 구분했고, 동시성 테스트는 모든 대상 유형의 actor link를 전역 집계했다.
- 증상으로 이어진 메커니즘: 콘텐츠 ID와 홀드 ID가 같은 fixture에서 콘텐츠 감사와 홀드 감사가 같은 조회 결과에 포함되고, 처리자가 있는 중단 경로에서는 두 홀드 감사 actor link도 전역 개수에 포함됐다.
- 기존 방어가 막지 못한 이유: 이전 구현은 연결 결제가 없는 홀드의 감사를 누락해 테스트의 불완전한 대상 식별이 드러나지 않았다.
- 결론의 증거: CI artifact의 `1 → 2`, `1 → 3` 결과와 대상 유형을 한정한 뒤 동일 테스트 및 전체 shard가 통과한 결과가 일치한다.

## 해결 또는 완화

- 생산 코드와 감사 계약은 변경하지 않았다.
- 콘텐츠 감사 검증은 `AuditEventTargetType.CONTENT`와 콘텐츠 ID를 함께 사용하도록 수정했다.
- 동시성 테스트의 actor link 검증은 콘텐츠 성공·실패 감사 이벤트에 연결된 actor만 계산하도록 수정했다.
- 변경 파일:
  - `SuspendContentControllerMySqlIntegrationTest.java`
  - `EndContentReservationsUseCaseMySqlTest.java`
  - `SuspendContentConcurrencyMySqlTest.java`

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 실패한 세 클래스 | 24건 중 8건 실패 | 24건 전부 성공 | 해결 |
| CI 동일 shard 1 전체 | CI 213건 중 8건 실패 | 로컬 213건 전부 성공 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon containerTestShard1 --tests "io.regionevent.regioneventbackend.domain.content.controller.SuspendContentControllerMySqlIntegrationTest" --tests "io.regionevent.regioneventbackend.domain.content.service.EndContentReservationsUseCaseMySqlTest" --tests "io.regionevent.regioneventbackend.domain.content.service.SuspendContentConcurrencyMySqlTest"` | 수정 전 실패, 수정 후 성공 | 24건, Before 8건 실패, After 실패 0건 |
| `.\gradlew.bat --no-daemon clean containerTestShard1` | 성공 | 213건, 실패·오류·건너뜀 0건 |

## 재발 방지와 문서 반영

감사 이벤트를 조회할 때는 숫자 식별자만 사용하지 않고 `targetType`과 `targetId`를 함께 사용한다. 대상 유형별 actor link 정합성 검증도 전역 개수 대신 해당 감사 이벤트와의 연결을 기준으로 한다.

## 잔여 위험과 후속 작업

- 로컬에서 CI 동일 shard는 통과했지만 수정 커밋을 푸시한 뒤 GitHub Actions 재실행 결과는 아직 확인하지 않았다.
- 생산 코드와 다른 shard는 변경하지 않았다.

## 관련 자료

- Issue #874
- PR #882
