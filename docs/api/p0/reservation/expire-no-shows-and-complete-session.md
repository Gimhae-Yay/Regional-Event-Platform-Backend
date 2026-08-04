## 7. 노쇼 전환과 회차 완료 처리

이 기능은 외부 클라이언트가 호출하는 HTTP API가 아닌 내부 스케줄러 작업이다.
실행 경로 식별자는 `scheduler`이며, 외부 URL·인증 헤더·JSON 요청과 응답은 제공하지 않는다.

스케줄러는 회차가 종료되고 체크인 창이 닫힌 뒤에도 남아 있는 `CONFIRMED` 예약을 노쇼 `EXPIRED`로 전환한다.
해당 회차의 노쇼 처리가 끝나면 회차를 `COMPLETED`로 전환한다.
모든 회차가 종결된 뒤 콘텐츠를 `ENDED`로 바꾸는 것은
[모든 회차 종결 콘텐츠 자동 종료](../content-catalog/end-completed-contents.md)의 별도 조정 스케줄러 책임이다.

### 실행 계약

| 항목 | 계약 |
| --- | --- |
| 실행 경로 | `scheduler` |
| 실행 주체 | 애플리케이션 내부 스케줄러 |
| 외부 HTTP 경로 | 없음 |
| 인증·인가 | 없음. 외부 요청을 받지 않는다. |
| 요청 본문·응답 본문 | 없음 |
| 시간 기준 | 애플리케이션 서버 시계가 아닌 MySQL 현재 시각 |
| 실행 주기 | 운영 설정으로 관리한다. 스케줄러 지연은 노쇼 전환을 늦출 수 있지만 체크인 창 종료 전 노쇼 전환을 허용하지 않는다. |

### 처리 대상과 상태 전이

| 구분 | 대상 조건 | 상태 전이 | 정원 처리 |
| --- | --- | --- |
| 노쇼 전환 | 회차가 `SCHEDULED`, `ends_at <= MySQL 현재 시각`, `checkin_close_at <= MySQL 현재 시각`이고 예약이 `CONFIRMED` | `CONFIRMED → EXPIRED` | 정원을 복구하지 않는다. `capacity_released_at`은 `null`로 유지한다. |
| 회차 완료 | 노쇼 처리 후 회차가 `SCHEDULED`, `ends_at <= MySQL 현재 시각`, `checkin_close_at <= MySQL 현재 시각`이고 남은 `CONFIRMED` 예약이 없음 | `SCHEDULED → COMPLETED` | 정원을 변경하지 않는다. |

### 처리 규칙

1. 노쇼 판정은 `ends_at <= MySQL 현재 시각`과 `checkin_close_at <= MySQL 현재 시각`을 모두 만족할 때만 수행한다.
2. `checkin_close_at`은 `ends_at`보다 이르지 않으므로, 두 조건을 모두 명시적으로 검사해 회차 종료 전 또는 체크인 창 종료 전 노쇼 전환을 막는다.
3. 대상 예약은 `status = CONFIRMED`를 조건으로 `CONFIRMED → EXPIRED`로 전이한다. 이미 `CHECKED_IN`, `CANCELLED`, `EXPIRED`인 예약은 변경하지 않는다.
4. 노쇼 전환에 성공하면 `reservation.expired_at`을 MySQL 기준 처리 시각으로 기록한다. `capacity_released_at`은 기록하지 않으며 회차의 `remaining_capacity`도 변경하지 않는다.
5. 회차의 모든 노쇼 대상 처리가 끝난 뒤, `status = SCHEDULED`, `ends_at <= MySQL 현재 시각`, `checkin_close_at <= MySQL 현재 시각`이고 남아 있는 `CONFIRMED` 예약이 없는 경우에만 `SCHEDULED → COMPLETED`를 조건부로 전이하고 `content_session.completed_at`을 기록한다. 예약이 없는 회차도 두 시각 조건을 만족하기 전에는 `SCHEDULED`로 유지한다.
6. 회차 완료는 `CHECKED_IN`, `CANCELLED`, `EXPIRED` 예약과 방문 기록을 변경하지 않는다.
7. 체크인과 노쇼 처리는 MySQL 현재 시각과 예약 상태를 조건으로 수행한다. 체크인 창 종료 경계에서 하나만 먼저 성공하며, 노쇼가 먼저 성공한 예약은 새 체크인을 허용하지 않는다.
8. 회차 취소와 노쇼·완료 처리가 경합하면 `content_session.status = SCHEDULED`와 예약 상태의 조건부 전이에 먼저 성공한 처리만 반영한다. 회차 취소가 먼저 성공하면 스케줄러는 해당 회차를 처리하지 않는다.
9. 노쇼 또는 회차 완료가 먼저 성공하면 회차 취소는 이미 종결된 상태를 다시 전이하지 않는다.
10. 회차 단위로 예약 노쇼 전환과 `COMPLETED` 전이가 중간에 분리되어 커밋되지 않도록 처리한다. 오류가 발생하면 해당 회차의 변경을 롤백하고 다음 스케줄러 실행에서 재시도할 수 있다.
11. 스케줄러가 중복 실행되거나 다중 인스턴스에서 동시에 실행돼도 예약의 `CONFIRMED` 조건과 회차의 `SCHEDULED`·종료 시각·체크인 마감 시각 조건부 전이로 각 예약의 노쇼 처리와 회차 완료는 최대 한 번만 발생한다.
12. 스케줄러는 외부 API 응답을 만들지 않는다. 노쇼·완료 처리 건수와 실패 사유는 구조화 로그와 감사 기록으로 관찰한다.

### 감사 및 정합성

- 성공한 노쇼 전환은 예약·회차·지역·콘텐츠 식별자, 이전·이후 상태, 처리 시각과 노쇼 사유를 재현할 수 있도록 감사 기록에 남긴다.
- 성공한 회차 완료는 회차 식별자, `SCHEDULED → COMPLETED` 전이와 처리 시각을 감사 기록에 남긴다.
- 시스템이 수행한 노쇼·회차 완료 처리의 감사 이벤트에는 사용자 처리자를 연결하지 않는다.
- 노쇼 처리로 `reservation.status = EXPIRED`가 되면 `expired_at`은 존재하고 `capacity_released_at`은 `null`이어야 한다.
- 회차 완료 시 `completed_at`은 존재해야 하며, `ends_at` 또는 `checkin_close_at`이 MySQL 현재 시각보다 미래이거나 같은 회차에 `CONFIRMED` 예약이 남아 있으면 `COMPLETED` 전이를 허용하지 않는다.
