# 미션 동시 반려 실패 감사 CI 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | PR #831의 Testcontainers shard 1 실패로 JaCoCo 통합 커버리지 검증이 실행되지 않음 |
| 최초 확인 시각·시간대 | 2026-08-15 10:03 KST (CI 실패 시각 2026-08-15 01:03 UTC) |
| 관련 요구사항·이슈 | #787 미션 생성·제출·반려의 대상 식별 후 실패 감사 기록 |
| revision·브랜치 | `f95081097b042c0528561d1f12265b289b58e5aa` · `fix/787-mission-failure-audit` |
| 환경·프로필 | GitHub Actions Corretto 21.0.12, Testcontainers MySQL · 로컬 Java 21.0.6, 기본 테스트 프로필 |

## 기대 결과와 실제 결과

### 기대 결과

동시 반려 요청 중 한 요청은 상태 변경과 `SUCCESS` 감사를 기록하고, 잠금 획득 후 상태 충돌로 실패한 요청은
이슈 #787 계약에 따라 같은 대상의 `FAILURE` 감사를 독립 트랜잭션으로 기록한다. 따라서 감사 이벤트는 총 2개다.

### 실제 결과

애플리케이션 동작은 감사 이벤트 2개를 기록했지만 기존 테스트는 1개를 기대해 실패했다.

## 재현 절차

### 선행 조건

- Docker 및 Testcontainers MySQL 실행 가능
- PR #831 head `f9508109`

### 명령·요청·입력

1. CI 원본 명령: `./gradlew --no-daemon clean containerTestShard1`
2. 로컬 최소 재현: `./gradlew.bat test --tests "io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionConcurrencyMySqlTest.concurrentRejections_returnMissionToDraftExactlyOnce"`

### 재현 결과

- 실행 횟수: CI 1회, 로컬 1회
- 성공 횟수: 0회
- 실패 횟수: 2회
- 종료 코드·HTTP 상태: Gradle 종료 코드 1

## 수집한 증거

- 실패 테스트: `ApproveRegionAdminMissionConcurrencyMySqlTest.concurrentRejections_returnMissionToDraftExactlyOnce`
- 실패 위치: `ApproveRegionAdminMissionConcurrencyMySqlTest.java:174`
- CI XML assertion: `expected: 1L`, `but was: 2L`
- 같은 테스트에서 상태 전이는 `DRAFT`로 검증됐고, 한 요청 성공 및 다른 요청의
  `MISSION_STATE_CONFLICT` 실패도 검증을 통과했다.
- PR #831의 `RejectRegionAdminMissionUseCase`는 대상 식별 후 `BusinessException` 발생 시
  `RecordFailedAuditEventUseCase`로 실패 감사를 기록한다.
- 종료 단계의 Netty `RejectedExecutionException`은 assertion 실패 이후 컨텍스트 종료 중 발생했다.

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-15 10:03 KST | 관찰 | shard 1의 단일 테스트가 감사 개수 assertion에서 실패 | CI XML에 expected/actual이 존재 | `1L` 기대, `2L` 실제 | 채택 |
| 2026-08-15 10:10 KST | 가설 | #787 실패 감사 추가를 기존 동시성 테스트가 반영하지 않음 | 성공 1개와 실패 1개가 기록되면 총 2개 | 구현 diff와 이슈 AC가 예측과 일치 | 채택 |
| 2026-08-15 10:12 KST | 가설 | Netty 종료 오류가 테스트 실패 원인 | assertion보다 먼저 연결 오류가 발생해야 함 | assertion 실패 후 종료 과정에서 발생 | 기각 |
| 2026-08-15 10:17 KST | 검증 | 단일 MySQL 테스트로 수정 전 재현 | 같은 174행 assertion에서 실패해야 함 | 로컬에서도 1개 테스트 실패 | 채택 |
| 2026-08-15 10:18 KST | 변경 | 성공·실패 감사 이벤트를 각각 검증 | 총 2개와 상태·오류 코드가 계약과 일치해야 함 | 테스트 assertion 보강 | 채택 |
| 2026-08-15 10:26 KST | 검증 | 단일·클래스 전체·`fastTest` 회귀 검증 | 모두 성공해야 함 | 모두 성공 | 채택 |

## 가설과 검증

### 가설 1: 기존 동시성 테스트의 감사 기대값이 #787 계약과 불일치한다

- 근거: 실패한 요청도 대상 식별 후 상태 충돌로 거절되므로 #787의 실패 감사 대상이다.
- 참일 때의 예측: 성공 감사 1개와 실패 감사 1개가 남고 실제 개수는 2개다.
- 반증 조건: 실패 요청이 대상 식별 전에 종료되거나 실제 감사 이벤트가 1개다.
- 검증 방법: CI XML, 테스트의 동시 실행 결과 검증, 반려 유스케이스 diff와 #787 AC를 대조한다.
- 결과: CI XML의 실제 개수는 2개이며 성공/실패 요청 결과도 각각 기대대로다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 같은 `PENDING_REVIEW` 미션에 반려 요청 2개가 동시에 실행됨.
- 결함이 있는 코드·설정·데이터·계약: 기존 테스트가 성공 감사만 존재한다는 과거 계약의 개수 `1`을 유지함.
- 증상으로 이어진 메커니즘: 첫 요청은 반려 성공 감사를 기록하고, 두 번째 요청은 잠금 후 `DRAFT` 상태를 확인해
  `MISSION_STATE_CONFLICT` 실패 감사를 기록하므로 실제 개수가 2가 됨.
- 기존 방어가 막지 못한 이유: PR 변경 목록에 이 기존 동시성 테스트가 포함되지 않았고 관련 테스트 선별 실행에도 포함되지 않음.
- 결론의 증거: CI XML의 `expected: 1L`, `but was: 2L`, #787 Acceptance Criteria, 반려 유스케이스 diff.

## 해결 또는 완화

- 선택한 방법: 기존 MySQL 동시성 테스트가 성공·실패 감사를 각각 검증하도록 최소 수정한다.
- 변경 파일: `ApproveRegionAdminMissionConcurrencyMySqlTest.java`
- 정책·계약 변경 여부: 없음. 확정된 #787 계약을 테스트에 반영한다.

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | CI와 로컬에서 감사 개수 `expected: 1L`, `but was: 2L` | 단일 MySQL 테스트 성공 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `./gradlew.bat --no-daemon test --tests "io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionConcurrencyMySqlTest.concurrentRejections_returnMissionToDraftExactlyOnce"` | 성공 | 원래 실패 테스트 |
| `./gradlew.bat --no-daemon test --tests "io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionConcurrencyMySqlTest"` | 성공 | 관련 MySQL 동시성 클래스 전체 |
| `./gradlew.bat fastTest` | 성공 | 저장소 필수 로컬 검증, 5분 48초 |

## 재발 방지와 문서 반영

동시성 테스트에서 총 개수만 검증하지 않고 성공·실패 감사 결과를 각각 검증한다.

## 잔여 위험과 후속 작업

로컬 회귀 검증은 완료했다. 변경 사항이 아직 커밋·푸시되지 않아 PR CI 재실행 결과는 확인하지 않았다.

## 관련 자료

- https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/pull/831
- https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/787
- https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/actions/runs/31855104260/job/94938273614
