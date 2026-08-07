## 8. 예약 취소

활성 회원이 본인 소유의 `CONFIRMED` 무료 예약 전체를 회차 시작 전 취소한다.
성공하면 예약을 `CANCELLED`로 전환하고, 홀드가 확보했던 인원만큼 회차 정원을 한 번 복구한다.

부분 취소와 인원 변경은 제공하지 않는다. 인원을 바꾸려면 예약 전체를 취소한 뒤 새 홀드와 예약을 생성해야 한다.

### Request

```http
POST /me/reservations/{reservationId}/cancel
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/me/reservations/{reservationId}/cancel
```

#### Request Example

```http
POST /api/v1/me/reservations/123/cancel HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | String | Y | 취소할 예약 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음. 사용자 취소 사유는 서버가 `USER_REQUEST`로 기록한다.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

이미 `CANCELLED`인 본인 예약의 취소 재요청은 최초 취소 결과를 반환한다. 기존 취소 사유와 정원 복구 시각은 변경하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "예약 취소에 성공했습니다.",
  "data": {
    "reservationId": "123",
    "sessionId": "456",
    "status": "CANCELLED",
    "cancellationReason": "USER_REQUEST",
    "cancelledAt": "2026-07-29T03:00:00Z",
    "capacityReleasedAt": "2026-07-29T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | String | 취소된 예약 식별자 |
| `data.sessionId` | String | 예약이 속한 회차 식별자 |
| `data.status` | String | 예약 상태. 항상 `CANCELLED` |
| `data.cancellationReason` | String | 최초 취소 사유. 이 API로 최초 취소한 경우 `USER_REQUEST` |
| `data.cancelledAt` | String | 최초 취소 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |
| `data.capacityReleasedAt` | String or null | 최초 정원 복구 시각. 회차 시작 이후의 회차 취소 등 정원을 복구하지 않은 취소는 `null` API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationId`가 양수가 아니다. 예약과 회차 정원은 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `reservationId`의 형식이 올바르지 않다. 예약과 회차 정원은 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 예약과 회차 정원은 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 예약의 소유자가 아니다. 예약과 회차 정원은 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약을 찾을 수 없다. 예약과 회차 정원은 변경하지 않으며 예약 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `RESERVATION_CANCEL_CONFLICT` | 예약이 `CONFIRMED` 또는 `CANCELLED`이 아니거나, `CONFIRMED` 예약에 대해 회차 시작 시각이 도래했거나, 체크인·노쇼가 먼저 성공했다. 이미 `CANCELLED`인 예약은 재시도해도 저장된 최초 취소 결과를 반환하며, 그 외 충돌은 동일 상태에서 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약 취소 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 예약과 회차 정원은 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "RESERVATION_CANCEL_CONFLICT",
  "message": "예약을 취소할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 예약의 `user_id`가 일치해야 한다.
3. 대상 예약이 이미 `CANCELLED`이면 저장된 `cancellation_reason`, `cancelled_at`, `capacity_released_at`을 포함한 최초 취소 결과를 반환하며 상태, 취소 사유와 정원을 다시 변경하지 않는다.
4. 최초 취소는 예약이 `CONFIRMED` 상태이고 MySQL 기준 현재 시각이 회차 시작 전일 때만 허용한다.
5. 최초 취소는 `reservation.status = CONFIRMED`와 `content_session.starts_at > MySQL 현재 시각`을 조건으로 `CONFIRMED → CANCELLED` 전이를 적용한다.
6. 예약 상태 전이와 같은 트랜잭션에서 회차의 `remaining_capacity`를 연결 홀드의 `quantity`만큼 한 번 복구한다.
7. 취소 성공 시 `cancelled_at`, `cancellation_reason = USER_REQUEST`, `capacity_released_at`을 같은 MySQL 기준 시각으로 기록한다.
8. 정원 복구는 최초 `CONFIRMED → CANCELLED` 전이에만 수행한다. 취소 재요청과 다른 종결 상태 전이는 정원을 다시 변경하지 않는다.
9. 회차 시작 이후 취소, 노쇼와 `CHECKED_IN` 전이는 정원을 복구하지 않는다. 이 API는 회차 시작 이후 취소 자체를 허용하지 않는다.
10. 체크인, 노쇼, 회차 취소, 회원 탈퇴와 취소가 경합하면 예약과 회차 상태의 조건부 전이에 먼저 성공한 처리만 반영한다.
11. `CHECKED_IN` 또는 `EXPIRED` 예약은 이 API로 취소할 수 없다.
12. 취소 성공 후 예약 QR 발급과 새 체크인을 차단한다. 기존 방문 기록과 후기는 변경하지 않는다.

### 감사 및 정합성

- 성공한 취소는 예약 상태 갱신, 회차 정원 복구와 성공 감사 이벤트를 하나의 MySQL 트랜잭션에서 함께 커밋한다.
- 감사 이벤트에는 처리자, 예약·홀드·회차·지역·콘텐츠 식별자, `CONFIRMED → CANCELLED` 전이, `USER_REQUEST` 사유와 처리 시각을 재현할 수 있도록 기록한다.
- 예약의 `capacity_released_at`은 회차 정원 복구가 성공한 최초 취소 시각과 같아야 한다.
- `capacity_hold.quantity`가 확보 인원의 단일 기준이며 예약에 인원을 중복 저장하지 않는다.
- `remaining_capacity`는 복구 뒤에도 `0 <= remaining_capacity <= capacity`를 유지해야 한다.
- 취소 실패와 경합 사유는 감사 기록과 구조화 로그로 추적한다.
