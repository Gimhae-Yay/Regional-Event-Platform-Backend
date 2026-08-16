# 전체 콘텐츠 철회 승인 보강 테스트 실패

## 요약

| 항목 | 내용 |
| --- | --- |
| 상태 | 해결 |
| 영향 | Issue #874 필수 시나리오 보강 테스트 2건 실패 |
| 최초 확인 시각·시간대 | 2026-08-16 23:24 KST |
| 관련 요구사항·이슈 | #874 정상·오류·자연 멱등·부수 효과 단일 실행 테스트 |
| revision·브랜치 | `f53f5059`, `feature/874-approve-content-withdrawal` |
| 환경·프로필 | Windows, Gradle `fastTest`, H2 MySQL mode |

## 기대 결과와 실제 결과

### 기대 결과

보강한 유스케이스 단위 테스트와 JPA 통합 테스트가 통과한다.

### 실제 결과

Mockito matcher 사용 오류와 H2 SQL 문법 오류로 각 1건씩 실패했다.

## 재현 절차

### 선행 조건

Issue #874 구현과 직전 정합성 검토의 필수 테스트 보강 변경이 작업 트리에 존재한다.

### 명령·요청·입력

1. `ApproveContentWithdrawalUseCaseTest`와 `ApproveContentWithdrawalControllerIntegrationTest`를 필터링한다.
2. `fastTest`를 실행한다.

### 재현 결과

- 실행 횟수: 1
- 성공 횟수: 0
- 실패 횟수: 1
- 종료 코드·HTTP 상태: Gradle 종료 코드 1, 8건 중 2건 실패

## 수집한 증거

- `ApproveContentWithdrawalUseCaseTest`: `InvalidUseOfMatchersException`
- `ApproveContentWithdrawalControllerIntegrationTest`: H2 `JdbcSQLSyntaxErrorException`

## 조사 타임라인

| 시각 | 구분 | 가설 또는 작업 | 예측·검증 | 결과 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 2026-08-16 23:24 KST | 관찰 | 보강 테스트 2건 실패 | 테스트 결과 XML과 SQL 예외를 확인한다 | 두 원인의 구체적 메시지 확인 | 채택 |
| 2026-08-16 23:26 KST | 검증 | H2 SQL 오류 원인 확인 | 실패 SQL에 MySQL 다중 테이블 UPDATE가 나타난다 | H2가 `UPDATE ... JOIN`을 거부 | 채택 |
| 2026-08-16 23:30 KST | 변경·검증 | matcher 통일 및 MySQL 통합 테스트 전환 | 같은 범위 테스트가 통과한다 | 단위 7건, MySQL 통합 1건 성공 | 채택 |

## 가설과 검증

### 가설 1: Mockito matcher 혼용

- 근거: `InvalidUseOfMatchersException`이 `verify(expirePaymentUseCase).expire(...)`에서 발생했다.
- 참일 때의 예측: 모든 인수를 matcher 또는 실제 값으로 통일하면 단위 테스트가 통과한다.
- 반증 조건: matcher를 통일해도 같은 예외가 발생한다.
- 검증 방법: 실패 줄과 Mockito 메시지를 확인하고 최소 수정 후 같은 테스트를 실행한다.
- 결과: `eq()` matcher로 실제 인수 두 개를 통일한 뒤 7건이 모두 통과했다.
- 판정: 채택

### 가설 2: 홀드·정원 원자 갱신 SQL과 H2 방언 차이

- 근거: 실제 홀드 종결 서비스를 연결한 JPA 통합 테스트에서 SQL 문법 오류가 발생했다.
- 참일 때의 예측: 실패 SQL을 확인하면 H2에서 지원하지 않는 MySQL 전용 갱신 구문이 나타난다.
- 반증 조건: 실패가 fixture 제약이나 다른 SQL에서 발생한다.
- 검증 방법: 테스트 XML의 최하위 SQL 예외를 확인한다.
- 결과: H2가 `CapacityHoldRepository.invalidateAndReleaseCapacityIfActive()`의 `UPDATE ... JOIN`을 거부했다. 같은 테스트를 저장소의 공유 MySQL 8.0.42 컨테이너로 전환하자 실제 쿼리와 연계 상태 검증이 통과했다.
- 판정: 채택

## 근본 원인

- 촉발 조건: 비어 있지 않은 종결 홀드를 단위 테스트에서 검증하고, H2 통합 테스트에 실제 홀드 종결 서비스를 연결했다.
- 결함이 있는 코드·설정·데이터·계약: 단위 테스트는 raw 인수와 matcher를 혼용했고, 통합 테스트 환경은 MySQL 전용 다중 테이블 UPDATE를 실행할 수 없는 H2였다.
- 증상으로 이어진 메커니즘: Mockito가 혼용된 인수 검증을 거부했고 H2 파서가 `UPDATE ... JOIN`을 거부했다.
- 기존 방어가 막지 못한 이유: 두 경로 모두 이번 필수 테스트 보강에서 처음 실행됐다.
- 결론의 증거: matcher 통일 후 단위 테스트 성공, 공유 MySQL 컨테이너 전환 후 실제 연계 통합 테스트 성공.

## 해결 또는 완화

- 선택한 방법: Mockito 인수를 모두 matcher로 통일하고, 홀드·정원 원자 갱신을 검증하는 통합 테스트를 저장소의 기존 MySQL Testcontainers 방식으로 실행한다.
- 변경 파일: `ApproveContentWithdrawalUseCaseTest.java`, `ApproveContentWithdrawalControllerIntegrationTest.java`
- 정책·계약 변경 여부: 없음

## Before/After 검증

| 검증 항목 | Before | After | 판정 |
| --- | --- | --- | --- |
| 원래 재현 절차 | 8건 중 2건 실패 | 단위 7건과 MySQL 통합 1건 성공 | 해결 |

## 회귀 테스트

| 테스트·명령 | 결과 | 비고 |
| --- | --- | --- |
| `fastTest --tests "*ApproveContentWithdrawalUseCaseTest"` | 성공 | 7건 통과 |
| `containerTestShard1 --tests "*ApproveContentWithdrawalControllerIntegrationTest"` | 성공 | MySQL 통합 1건 통과 |

## 재발 방지와 문서 반영

MySQL 전용 다중 테이블 갱신을 포함하는 통합 테스트는 H2가 아닌 기존 공유 MySQL 컨테이너에서 실행한다.

## 잔여 위험과 후속 작업

Docker를 사용할 수 없는 환경에서는 컨테이너 테스트가 비활성화된다.

## 관련 자료

- Issue #874 구현 인계 요약
