# 미션 진행도 반영 내부 실행 명세서

## 1. 개요

영속화된 유효 방문을 입력으로 받아 참여 중인 지역 미션의 진행도를 반영한다. HTTP API가 아니므로 Endpoint를
정의하지 않는다. 이 계약은 진행도 처리의 입력·트랜잭션·멱등성·실패 동작을 정의하며, 별도 Outbox, 메시지 브로커
또는 Scheduler 도입을 전제하지 않는다.

### 실행 계약

| 항목 | 내용 |
| --- | --- |
| 실행 방식 | 방문 완료 뒤 내부 실행 어댑터가 호출 |
| HTTP Endpoint | 없음 |
| 관련 요구사항 | `P1-FR-04`, `MSN-03`, `P1-AC-04` |
| 입력 | 영속 `visitId` |
| 처리 대상 | 방문 사용자에게 속한 `IN_PROGRESS` 참여 중 방문 지역과 같은 지역의 미션 |
| 처리 단위 | 대상 미션 참여 한 건의 쓰기 트랜잭션 |
| 멱등 기준 | `UNIQUE (mission_participation_id, visit_id)`와 `CONTENT_SET`의 참여·콘텐츠별 조건부 쓰기 |
| 잠금 순서 | 미션 행을 먼저 잠근 뒤 참여 행을 잠금 |

### 처리 규칙

1. 입력 `visitId`로 영속 방문을 조회한다. 방문을 찾을 수 없거나 유효 방문이 아니면 진행 근거를 만들지 않는다.
2. 방문 사용자와 지역을 기준으로 현재 `IN_PROGRESS`인 미션 참여 후보를 식별한다. 후보 조회 결과만으로 진행도를
   변경하지 않고 각 대상 트랜잭션에서 조건을 다시 검증한다.
3. 대상별 트랜잭션에서 `mission` 행을 `PESSIMISTIC_WRITE`로 먼저 잠그고 해당 `mission_participation` 행을 잠근다.
4. 모든 잠금을 획득한 직후 DB 현재 시각을 한 번만 읽어 `operationAt`으로 고정한다. 잠금 뒤 다음 조건을 모두
   만족하는 경우에만 진행도를 반영한다.
   - 미션이 `PUBLISHED`이고 `ends_at > operationAt`이다.
   - 참여가 `IN_PROGRESS`다.
   - `visit.user_id = mission_participation.user_id`다.
   - `visit.region_id = mission.region_id`다.
   - `visit.checked_at >= mission_participation.joined_at`이다.
   - `visit.content_id = mission_progress.content_id`로 기록할 수 있다.
5. `VISIT_COUNT`는 같은 참여에 같은 `visitId` 근거가 없을 때 진행 근거를 한 건 추가한다. 서로 다른 `visitId`면
   같은 콘텐츠 재방문도 별도 진행도로 인정한다.
6. `CONTENT_SET`은 방문 콘텐츠가 `mission_target_content`에 포함되고 같은 참여·콘텐츠의 기존 진행 근거가 없을 때만
   한 건 추가한다. 이미 반영한 콘텐츠의 재방문은 정상 무변경으로 처리한다.
7. 진행 근거의 `recorded_at`은 `operationAt`으로 기록한다. 반영 뒤 완료 조건을 만족하면 같은 트랜잭션에서 참여를
   `COMPLETED`로 전이하고 `completed_at = operationAt`으로 기록한다.
8. 동일 `visitId`가 반복 전달되거나 동시 처리되면 유일 제약 충돌 뒤 현재 상태를 다시 확인하고, 이미 반영된 결과로
   수렴한다. 새 진행 근거나 완료 전이를 만들지 않는다.
9. 한 대상 미션의 처리 실패는 해당 대상 트랜잭션을 모두 롤백한다. 방문 원본과 이미 커밋된 다른 미션의 진행도는
   변경하지 않으며, 동일 `visitId`로 해당 처리를 다시 호출할 수 있다.
10. 실패 로그에는 `requestId`, `visitId`, `missionId`, `missionParticipationId`와 비개인 오류 코드만 기록한다.
    토큰, 사용자 개인정보와 방문 상세를 기록하지 않는다.

### 실행 결과

내부 실행은 HTTP 응답을 만들지 않는다. 대상별 성공 기준은 `mission_progress` 삽입과 필요한
`mission_participation` 완료 전이가 같은 트랜잭션으로 커밋된 경우다. 조건 불충족과 이미 반영된 방문은 정상
무변경 결과다.
