# 미션 자동 종료 Scheduler 명세서

## 1. 개요

미션 자동 종료 Scheduler는 `status = PUBLISHED AND ends_at <= 현재 시각`인 미션만 `ENDED`로 조건부 전이한다.
HTTP API가 아니므로 Endpoint를 정의하지 않는다.

### 실행 계약

| 항목 | 내용 |
| --- | --- |
| 실행 방식 | Scheduler |
| HTTP Endpoint | 없음 |
| 관련 요구사항 | `P1-FR-03`, `MSN-01`, `P1-AC-03` |
| 후보 조건 | `PUBLISHED mission AND ends_at <= 현재 시각` |
| 성공 전이 | `status = ENDED`, `ended_at = 실제 처리 시각` |
| 감사 주체 | `actor_kind = SYSTEM`, 사용자 처리자와 `audit_event_actor_link` 없음 |
| 실행 식별 | Scheduler 실행마다 고유한 `requestId` 생성 |
| 실행 주기 | 배포 설정으로 관리한다. 실행 간격과 무관하게 DB 현재 시각 기준 후보 조건을 매 실행마다 다시 평가한다. |
| 재실행 | 같은 후보를 다시 읽어도 조건부 전이로 한 번만 종료 효과를 만든다. |
| 잠금 순서 | 미션 행을 먼저 `PESSIMISTIC_WRITE`로 잠그고, 미완료 참여 행을 `mission_participation_id` 오름차순으로 잠근다. |

### 처리 규칙

1. 후보 식별자 조회 뒤 미션 한 건의 쓰기 트랜잭션을 시작하고 `mission` 행을
   `PESSIMISTIC_WRITE`(`SELECT ... FOR UPDATE`)로 먼저 잠근다.
2. 잠금 획득 뒤 `status = PUBLISHED AND ends_at <= DB 현재 시각`을 다시 확인한다. 이미 `ENDED`이거나
   조건을 만족하지 않는 미션은 변경하지 않는다.
3. 종료 대상이면 `IN_PROGRESS` 참여 행만 `mission_participation_id` 오름차순으로 잠근 뒤
   `ENDED_INCOMPLETE`로 전이한다. `COMPLETED`와 이미 `ENDED_INCOMPLETE`인 참여 상태는 변경하지 않지만,
   `COMPLETED`의 미수령 보상 권리는 `ends_at` 도달과 동시에 만료된다.
4. 참여 생성과 진행도 반영도 같은 미션 행을 먼저 잠근다. 필요한 모든 행의 잠금을 획득한 직후 DB 현재 시각을
   한 번만 읽어 `operationAt`으로 고정하고 `status = PUBLISHED AND ends_at > operationAt`을 재검증한다.
   참여 생성은 `joined_at = operationAt`으로 기록하고, 진행도 반영은 참여가 `IN_PROGRESS`일 때만
   `recorded_at = operationAt`으로 근거를 추가하며 완료 전이 시 `completed_at = operationAt`으로 기록한다.
5. 진행도 반영이 미션 잠금을 먼저 얻으면 해당 트랜잭션이 커밋된 뒤 종료가 완료 여부를 확인한다. 종료가 먼저
   잠금을 얻으면 이후 진행도 반영은 `ENDED` 상태를 확인하고 근거와 상태를 변경하지 않는다.
6. 종료 전에 생성된 기존 보상 수령 결과는 멱등 재조회할 수 있지만 새 수령과 수동 지급은 허용하지 않는다.
7. 성공한 자동 종료는 미션과 지역 식별자, `PUBLISHED → ENDED`, `reason_code = MISSION_END_TIME_REACHED`, 처리 시각과 `requestId`를 `audit_event`에 기록한다.
8. 미션 종료, 미완료 참여 전이와 성공 감사 이벤트는 미션 한 건의 같은 트랜잭션으로 커밋하거나 함께 롤백한다.
9. 후보 조회 뒤 다른 처리가 먼저 상태를 바꿔 조건을 만족하지 않는 경우는 정상적으로 건너뛰고 실패 감사 이벤트를 만들지 않는다.
10. 한 미션의 처리 실패는 해당 미션 트랜잭션만 롤백한다. 롤백 완료 뒤 독립 트랜잭션으로
   `target_type = MISSION`, 대상 미션과 지역, `previous_state = PUBLISHED`, `next_state = null`,
   `result = FAILURE`, `reason_code = MISSION_AUTO_END_FAILED`, 같은 `requestId`, `actor_kind = SYSTEM`인
   실패 감사 이벤트를 기록한다. 실패 감사 기록도 실패한 경우에만 구조화 로그로 관찰한다.
11. Scheduler 경계는 실패를 구조화 로그로 기록하고 나머지 후보 처리를 계속하며, 다음 실행에서 실패한 미션을
    다시 후보로 평가한다. 후보 조회 뒤 잠금 획득 시 종료 조건이 달라진 정상 건너뛰기는 실패 감사 대상이 아니다.
12. 실행 완료 로그에는 `requestId`, 조회 후보 수, 종료 성공 수, 조건 불충족 건너뛰기 수와 실패 수를 기록한다.
    후보가 없어도 정상 완료로 처리한다.

### 실행 결과

Scheduler는 HTTP 응답을 만들지 않는다. 실행 결과는 구조화 로그와 감사 이벤트로 확인하며, 미션별 성공 기준은
`mission`, 미완료 `mission_participation`, `audit_event`가 같은 트랜잭션으로 커밋된 경우다.
