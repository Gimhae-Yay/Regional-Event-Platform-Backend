## 3. 예약 대기 및 정원 홀드 생성

활성 회원이 공개 콘텐츠의 회차와 인원을 선택해 예약 확정 전 정원을 임시 확보한다.
성공하면 회차의 잔여 정원을 조건부로 차감하고 `ACTIVE` 상태의 정원 홀드를 생성한다.

이 API는 예약을 확정하지 않는다. 예약 확정은 생성된 `holdId`를 사용해 별도 API에서 처리한다.

### Request

```http
POST /reservations
```

실제 요청 경로는 다음과 같다.

```http
POST /api/v1/reservations
```

#### Request Example

```http
POST /api/v1/reservations HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "sessionId": 456,
  "quantity": 2
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "sessionId": 456,
  "quantity": 2
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | Long | Y | 정원을 홀드할 회차 식별자. 양수여야 한다. |
| `quantity` | Integer | Y | 홀드할 인원 수. 1 이상이어야 하며 회차의 남은 정원을 초과할 수 없다. |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "예약 대기 및 정원 홀드 생성에 성공했습니다.",
  "data": {
    "holdId": 789,
    "sessionId": 456,
    "quantity": 2,
    "status": "ACTIVE",
    "expiresAt": "2026-07-29T12:10:00+09:00",
    "createdAt": "2026-07-29T12:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.holdId` | Long | 생성된 정원 홀드 식별자 |
| `data.sessionId` | Long | 홀드 대상 회차 식별자 |
| `data.quantity` | Integer | 홀드한 인원 수 |
| `data.status` | String | 홀드 상태. 항상 `ACTIVE` |
| `data.expiresAt` | String | 홀드 만료 시각. `createdAt + 10분`과 회차 시작 시각 중 더 이른 시각 |
| `data.createdAt` | String | 홀드 생성 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `sessionId` 또는 `quantity`가 없거나 형식·범위가 올바르지 않다. 홀드 생성과 정원 차감은 발생하지 않으며 재시도 전 요청 값을 수정해야 한다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 홀드 생성과 정원 차감은 발생하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 홀드 생성과 정원 차감은 발생하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 예약 생성 권한이 없다. 홀드 생성과 정원 차감은 발생하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 회차를 찾을 수 없거나 공개 조회가 허용되지 않는 회차다. 홀드 생성과 정원 차감은 발생하지 않으며 회차 식별자와 공개 상태를 확인한 뒤 재시도할 수 있다. |
| `409` | `RESERVATION_HOLD_CONFLICT` | 콘텐츠가 `PUBLISHED`가 아니거나, 회차가 `SCHEDULED`가 아니거나, 회차 시작 이후이거나, 잔여 정원이 부족하거나, 다른 상태 전이가 먼저 성공했다. 홀드 생성과 정원 차감은 발생하지 않으며 동일 상태에서 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 홀드 생성 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 홀드 생성과 정원 차감은 발생하지 않으며 서버가 롤백된 것을 확인한 경우에만 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "RESERVATION_HOLD_CONFLICT",
  "message": "예약 대기를 생성할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 회원이어야 한다.
2. 서버는 `sessionId`와 `quantity` 필수 여부, 타입과 범위를 검증한다.
3. 대상 회차가 존재해야 한다.
4. 대상 회차의 콘텐츠가 `PUBLISHED` 상태여야 한다.
5. 대상 회차가 `SCHEDULED` 상태여야 한다.
6. MySQL 기준 현재 시각이 회차 시작 전이어야 한다.
7. `quantity`는 1 이상이어야 하며 대상 회차의 잔여 정원을 초과할 수 없다.
8. 홀드 생성은 `content_session.remaining_capacity >= quantity` 조건부 갱신으로 잔여 정원을 먼저 차감한다.
9. 잔여 정원 차감과 `ACTIVE` 홀드 생성은 하나의 트랜잭션에서 함께 커밋한다.
10. 홀드 만료 시각은 `createdAt + 10분`과 회차 시작 시각 중 더 이른 시각으로 설정한다.
11. 동일 사용자가 같은 회차에 대해 이 API를 반복 호출하면, 잔여 정원이 허용하는 한 별도 홀드를 생성할 수 있다.
12. 이 API는 멱등 키를 받지 않는다. 예약 확정의 멱등성은 별도 예약 확정 API에서 보장한다.
13. 생성된 홀드는 연장하거나 재사용하지 않는다.
14. 오류가 발생하면 홀드 생성과 정원 차감은 모두 반영하지 않는다.

### 감사 및 정합성

- `capacity_hold.status`는 `ACTIVE`로 기록한다.
- `capacity_hold.user_id`는 인증 회원 식별자로 기록한다.
- `capacity_hold.quantity`가 확보 인원의 단일 기준이며, 예약 테이블에 인원을 중복 저장하지 않는다.
- `capacity_hold.expires_at`은 응답의 `expiresAt`과 동일한 시각으로 기록한다.
- 홀드 생성 시 차감된 정원은 `ACTIVE → EXPIRED` 또는 `ACTIVE → INVALIDATED` 최초 전이에서만 한 번 복구한다.
- 홀드 생성 성공은 콘텐츠 상태 로그를 생성하지 않는다.
- 정원 부족, 회차 취소, 콘텐츠 비예약 가능 상태 전이와 경합하면 먼저 성공한 조건부 갱신만 반영한다.
