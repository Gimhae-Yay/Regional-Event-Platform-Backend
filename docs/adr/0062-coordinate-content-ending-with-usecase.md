# ADR-0062: 자동·수동 콘텐츠 종료를 같은 UseCase에서 조정

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-04
- 결정일: 2026-08-04
- 관련 요구사항: [FR-04 승인·자동 공개·종료](../p0/content-catalog.md#fr-04-승인자동-공개종료), [UseCase 책임](../ARCHITECTURE.md#33-usecase), [모든 회차 종결 콘텐츠 자동 종료](../api/p0/content-catalog/end-completed-contents.md), [예약·노출 종료](../api/p0/reservation/end-content-reservations.md)
- 관련 단계: 단계 0. 정책·설계 확정, 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: [ADR-0059](0059-automatically-end-content-after-all-sessions-terminate.md#결정)의 "공통 종료 서비스", "공통 홀드 무효화 서비스"와 종료 트랜잭션 책임 범위. 자동 종료 조건과 조정 스케줄러 선택은 유지한다.

## 맥락

ADR-0059는 자동 종료 스케줄러와 수동 종료 API가 같은 결과를 만들도록 "공통 종료 서비스"를 호출한다고
정했다. 하지만 콘텐츠 종료는 콘텐츠 상태, 회차 상태 확인, 콘텐츠 로그, 감사 기록, 활성 홀드와 정원을 함께
처리한다. 이를 하나의 Service가 직접 맡거나 Service가 다른 Service를 호출하게 만들면 여러 작업의 순서와
트랜잭션을 누가 책임지는지 불분명해진다.

프로젝트 아키텍처는 여러 Service를 묶어 하나의 작업으로 실행하는 책임을 UseCase에 둔다. 현재 수동 종료도
`EndContentReservationsUseCase`가 Controller의 요청을 받아 여러 처리 단계를 조정한다. 자동 종료를 추가할 때
같은 UseCase를 사용하면 수동·자동 종료가 같은 잠금, 상태 확인과 변경 순서를 사용할 수 있다.

## 결정 동인과 불변 조건

- 수동 종료와 자동 종료는 같은 상태 확인, 잠금과 변경 순서를 사용해야 한다.
- 여러 Service를 조정하고 쓰기 트랜잭션을 소유하는 계층은 UseCase여야 한다.
- Scheduler는 실행 시점과 후보 반복만 담당하고 개별 Service를 직접 조정하지 않아야 한다.
- Service는 맡은 도메인 작업과 저장소만 다루며 다른 Service를 직접 호출하지 않아야 한다.
- 후보 전체를 하나의 긴 트랜잭션으로 묶지 않고 콘텐츠 한 건의 종료만 한 트랜잭션으로 처리해야 한다.
- 수동 요청의 권한·오류 응답과 자동 실행의 시스템 처리·건너뛰기 차이는 유지해야 한다.
- ADR-0060의 공통 `content` 행 잠금과 ADR-0061의 회차 종결 상태 기준은 바꾸지 않아야 한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 별도 Scheduler가 기존 `EndContentReservationsUseCase`를 호출 | 수동·자동 종료가 같은 작업 순서와 트랜잭션을 사용하고, Scheduler와 Service의 책임이 분명하다. | UseCase에 수동·자동 진입 메서드를 나눠야 한다. | 낮음. 기존 수동 UseCase를 유지한 채 자동 진입점만 추가할 수 있다. | 사용자가 선택했으며 현재 아키텍처와 기존 코드에 가장 잘 맞는다. |
| 2 | Service 클래스 안에 `@Scheduled`를 둠 | 클래스 수가 적고 바로 실행할 수 있다. | 실행 시점, 여러 도메인 조정과 트랜잭션 책임이 Service에 섞인다. 다른 Service를 직접 호출할 가능성도 커진다. | 중간. 나중에 Scheduler와 UseCase로 책임을 다시 나눠야 한다. | 기술적으로 가능하지만 프로젝트 계층 규칙과 맞지 않는다. |
| 3 | 수동·자동 종료 UseCase를 각각 만듦 | 각 입력 경로의 차이를 독립적으로 표현할 수 있다. | 잠금, 상태 확인, 로그·감사·홀드 처리가 복제되어 두 흐름이 달라질 수 있다. | 중간. 중복 코드를 합치고 테스트를 다시 정리해야 한다. | 같은 종료 결과를 요구하는 현재 범위에는 중복이 크다. |

## 결정

`EndCompletedContentsScheduler`를 별도 `@Component`로 둔다. 이 컴포넌트가 `@Scheduled` 실행 시점과 후보 목록
반복만 담당한다. 개별 콘텐츠를 끝내기 위해 `ContentService`, `ContentSessionService`, `ContentLogService` 또는
`CapacityHoldService`를 직접 호출하지 않고 `EndContentReservationsUseCase`만 호출한다.

`EndContentReservationsUseCase`는 다음 진입점을 제공한다.

- `findAutoEndCandidateIds()`는 자동 종료 후보 식별자를 조회한다. 이 조회는 쓰기 트랜잭션을 시작하거나 행 잠금을
  오래 유지하지 않는다.
- `endByRegionAdmin(userId, contentId, requestId)`는 담당 지역 관리자 권한을 확인하고 수동 종료를 처리한다.
- `endBySystem(contentId, requestId)`는 시스템 처리자로 자동 종료를 시도한다. 후보 조회 뒤 조건이 달라졌으면
  오류로 만들지 않고 건너뛴다.

두 종료 메서드는 각각 공개된 UseCase 메서드의 쓰기 트랜잭션 안에서 콘텐츠 한 건만 처리한다. Scheduler의 후보
조회와 전체 반복을 하나의 트랜잭션으로 묶지 않는다. 각 종료 메서드는 같은 내부 처리 순서를 사용해 대상
`content` 행을 먼저 잠그고, 콘텐츠와 전체 회차 상태를 다시 확인한 뒤 콘텐츠 상태, 콘텐츠 로그, 성공 감사 기록,
활성 홀드 무효화와 정원 복구를 함께 커밋하거나 롤백한다.

UseCase는 `ContentService`, `ContentSessionService`, `ContentLogService`, `CapacityHoldService`와 감사 기록 담당
객체를 조정한다. 각 Service는 자신이 맡은 작업만 수행하며 Service끼리 직접 호출하지 않는다.

수동 종료는 지역 관리자 권한을 확인하고 사용자 처리자를 기록하며, 종료할 수 없는 상태는 API의 `409`로
응답한다. 자동 종료는 별도 사용자 권한을 확인하지 않고 시스템 처리자를 기록하며, 이미 종료됐거나 종결되지 않은
회차가 생긴 후보는 정상적으로 건너뛴다. 이 입력 경로의 차이를 제외한 잠금과 상태 변경 결과는 같다.

## 결과와 트레이드오프

### 기대 효과

- Scheduler와 Controller가 달라도 콘텐츠 종료의 실제 작업 순서가 한 UseCase에 모인다.
- 종료 트랜잭션의 시작과 끝이 콘텐츠 한 건 단위로 분명해진다.
- Service가 다른 Service를 호출하지 않아 각 계층의 책임을 지킬 수 있다.
- 자동 종료 후보 한 건이 실패해도 다른 후보 처리를 계속할 수 있다.

### 수용한 단점과 위험

- 하나의 UseCase에 수동·자동 진입 메서드가 함께 있어 입력 경로별 차이를 명확히 구분해야 한다.
- 후보 조회와 실제 종료 사이에 상태가 바뀔 수 있으므로 종료 트랜잭션에서 조건을 반드시 다시 확인해야 한다.
- UseCase의 공통 내부 처리 순서를 우회하는 새 종료 경로가 생기면 수동·자동 결과가 다시 달라질 수 있다.

## 전환과 롤백

DB 스키마나 기존 데이터 이관은 필요 없다. 먼저 자동·수동 종료 명세에서 공통 Service 표현을 제거하고 Scheduler,
UseCase, Service의 책임과 콘텐츠 한 건 단위 트랜잭션을 명확히 한다. 수동 종료의 기존
`EndContentReservationsUseCase`는 유지하고 자동 종료 Scheduler와 시스템 진입점을 연결한다.

자동 실행에 문제가 생기면 Scheduler만 중지하고 수동 종료 API는 같은 UseCase로 계속 사용할 수 있다. 책임을
다른 계층으로 옮기거나 수동·자동 UseCase를 분리하려면 새 ADR로 이 결정을 대체한다.

## 검증 방법

- 수동 Controller와 자동 Scheduler가 모두 `EndContentReservationsUseCase`만 호출하는지 의존성 테스트로 검증한다.
- `EndCompletedContentsScheduler`가 개별 Service나 Repository에 직접 의존하지 않는지 검증한다.
- 후보 전체가 아니라 후보 한 건마다 별도 쓰기 트랜잭션이 시작되고 끝나는지 MySQL 통합 테스트로 검증한다.
- 콘텐츠 상태, 로그, 성공 감사, 홀드와 정원 중 하나가 실패하면 해당 후보의 변경이 모두 롤백되는지 검증한다.
- 수동 종료는 사용자 처리자와 API 오류를, 자동 종료는 시스템 처리자와 정상 건너뛰기를 각각 기록하는지 검증한다.
- 자동·수동 종료와 추가 회차 생성의 두 실행 순서에서 ADR-0060의 공통 잠금 규칙이 유지되는지 검증한다.
- `PENDING` 또는 `SCHEDULED` 회차가 있으면 종료되지 않고, 나머지가 모두 종결 상태면 한 번만 종료되는지 검증한다.

## 대체 조건

- 후보 수가 늘어 Scheduler의 동기 반복 대신 메시지 큐나 분산 작업 처리가 필요해진다.
- 콘텐츠 종료가 MySQL 밖의 여러 시스템까지 원자적으로 바꿔야 해 현재 트랜잭션으로 처리할 수 없게 된다.
- 수동·자동 종료의 상태 변경 결과가 서로 다른 별도 업무로 확정된다.
