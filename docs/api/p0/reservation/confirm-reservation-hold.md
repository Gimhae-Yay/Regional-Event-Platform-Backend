## 4. 활성 홀드의 무료 예약 확정

활성 회원이 자신이 생성한 유효한 `ACTIVE` 홀드를 무료 예약으로 한 번만 확정한다.
성공하면 정원을 추가로 차감하지 않고 홀드를 `CONSUMED`로 전환하며 `CONFIRMED` 예약을 생성한다.

예약 확정은 영속 멱등 처리 대상이다. 동일한 `Idempotency-Key`와 `holdId`를 재전송하면 최초 성공 결과를 반환한다.

### Request

```http
POST /reservation-holds/{holdId}/confirm
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/reservation-holds/{holdId}/confirm
```

#### Request Example

```http
POST /api/v1/reservation-holds/789/confirm HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Idempotency-Key` | Y | 클라이언트가 생성한 비어 있지 않은 멱등 키. 같은 예약 확정의 재시도에는 반드시 같은 값을 사용하고, 새 확정 요청에는 새 값을 사용한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `holdId` | Long | Y | 확정할 정원 홀드 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
201 Created
```

동일한 `Idempotency-Key`와 `holdId`로 완료된 요청을 재시도한 경우에도 최초 성공과 동일하게 `201 Created`와 저장된 결과를 반환한다.

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "무료 예약 확정에 성공했습니다.",
  "data": {
    "reservationId": 123,
    "reservationNo": "R202607290001",
    "holdId": 789,
    "sessionId": 456,
    "status": "CONFIRMED",
    "confirmedAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | Long | 생성된 예약 식별자 |
| `data.reservationNo` | String | 시스템 전체에서 유일한 예약 번호. 형식은 서버가 생성한다. |
| `data.holdId` | Long | 소비된 정원 홀드 식별자 |
| `data.sessionId` | Long | 예약한 회차 식별자 |
| `data.status` | String | 예약 상태. 항상 `CONFIRMED` |
| `data.confirmedAt` | String | 예약 확정 시각 |

예약 QR은 이 API에서 발급하지 않는다. `CONFIRMED` 예약은 체크인 창에서 별도 QR 발급 API를 사용할 수 있다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `holdId`가 양수가 아니거나 `Idempotency-Key`가 없거나 비어 있다. 멱등 기록, 홀드와 예약은 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `holdId`의 형식이 올바르지 않다. 멱등 기록, 홀드와 예약은 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 멱등 기록, 홀드와 예약은 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 홀드의 소유자가 아니다. 멱등 기록, 홀드와 예약은 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 홀드를 찾을 수 없다. 멱등 기록, 홀드와 예약은 변경하지 않으며 홀드 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 회원의 `RESERVATION_CONFIRM` 명령에서 이미 다른 `holdId`에 사용한 `Idempotency-Key`다. 새 예약을 만들지 않으며 같은 키로 재시도할 수 없고 새 요청에는 새 키를 사용해야 한다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 회원·키·홀드의 최초 요청이 아직 처리 중이다. 새 예약을 만들지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `RESERVATION_CONFIRM_CONFLICT` | 홀드가 유효한 `ACTIVE`가 아니거나, 회원·콘텐츠·회차가 예약 가능하지 않거나, 회차 시작 시각이 도래했거나, 다른 확정·만료·무효화 전이가 먼저 성공했다. 예약은 생성하지 않으며 동일 상태에서 재시도해도 성공하지 않으므로 상태를 확인한 뒤 새 홀드 생성 여부를 판단해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약 확정 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 멱등 성공 기록, 홀드와 예약은 변경하지 않으며 일시적 장애라면 동일한 `Idempotency-Key`로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "RESERVATION_CONFIRM_CONFLICT",
  "message": "예약을 확정할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 홀드의 `user_id`가 일치해야 한다.
3. `holdId`와 `Idempotency-Key`의 필수 여부와 형식을 먼저 검증한다.
4. 멱등 키의 논리 유일 범위는 `(actor_user_id, operation = RESERVATION_CONFIRM, idempotency_key_hash)`다.
5. 같은 멱등 키와 같은 `holdId`의 `SUCCEEDED` 기록이 있으면 해당 `result_reservation_id`로 최초 성공 응답을 재구성해 반환한다. 홀드 소비와 예약 생성을 다시 실행하지 않는다.
6. 같은 멱등 키에 다른 `holdId`를 요청하면 `409 IDEMPOTENCY_KEY_CONFLICT`로 거부한다.
7. 같은 멱등 키와 같은 `holdId`의 최초 요청이 `PROCESSING`이면 `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`로 응답한다. 대기 제한을 넘긴 뒤에도 새 도메인 작업을 실행하지 않는다.
8. 대상 홀드는 유효한 `ACTIVE` 상태여야 하고 `expires_at`이 MySQL 기준 현재 시각보다 미래여야 한다.
9. 대상 콘텐츠는 `PUBLISHED`, 회차는 `SCHEDULED` 상태여야 하며 MySQL 기준 현재 시각이 회차 시작 전이어야 한다.
10. 유효한 홀드는 `ACTIVE → CONSUMED`으로 조건부 전이하고, 같은 트랜잭션에서 `CONFIRMED` 예약을 한 건 생성한다.
11. 예약은 홀드의 `region_id`, `session_id`, `user_id`와 일치시킨다. `reservation.hold_id`의 유일 제약으로 한 홀드당 예약을 최대 한 건만 허용한다.
12. 예약 확정은 홀드 생성 시 이미 확보한 정원을 소비하므로 `content_session.remaining_capacity`를 추가로 차감하거나 복구하지 않는다.
13. `ACTIVE`가 아닌 홀드, 만료·무효화된 홀드, 회차 시작 이후의 홀드는 연장·재사용·재확정할 수 없다.
14. 종결·상태 충돌처럼 재시도해도 결과가 바뀌지 않는 확정 실패는 `idempotency_record.status = FAILED`와 `result_code`로 기록하고, 같은 키·같은 홀드의 재요청에는 저장된 실패 결과를 반환한다.
15. 검증·인증·인가·대상 부재 오류와 트랜잭션 롤백이 필요한 일시적 서버 오류는 성공 멱등 결과로 기록하지 않는다.
16. 서로 다른 멱등 키로 같은 홀드를 동시에 확정하면 `ACTIVE → CONSUMED` 조건부 전이와 `reservation.hold_id` 유일 제약으로 한 요청만 성공한다. 나머지 요청은 `409 RESERVATION_CONFIRM_CONFLICT`로 응답한다.

### 감사 및 정합성

- 멱등 키 점유, `capacity_hold`의 `ACTIVE → CONSUMED` 전이, `reservation` 생성, 성공 `idempotency_record` 기록과 성공 감사 이벤트는 하나의 MySQL 트랜잭션에서 커밋한다.
- 성공 멱등 기록의 `operation`은 `RESERVATION_CONFIRM`, `status`는 `SUCCEEDED`, `result_reservation_id`는 생성한 예약 식별자로 기록한다. `result_visit_id`는 `null`이다.
- `request_hash`는 `holdId`를 포함한 정규화된 명령 의미로 계산하며 개인정보 원문을 포함하지 않는다.
- `capacity_hold.terminal_at`과 예약의 `confirmed_at`은 확정 처리 시각으로 기록한다.
- `reservation.qr_reference`는 서버가 생성한 불투명 참조이며, 이름·연락처·`user_id`를 포함하지 않는다.
- 성공 감사 이벤트는 처리자, 홀드·예약 식별자, `ACTIVE → CONSUMED` 및 `CONFIRMED` 결과와 처리 시각을 재현할 수 있어야 한다.
- 확정 실패와 멱등 충돌의 사유는 감사 기록과 구조화 로그로 추적한다.
