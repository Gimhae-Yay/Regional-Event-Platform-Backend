## 9. 예약 상세 조회

활성 회원이 본인 소유 예약의 예약 상태, 회차 일정·운영 상태와 체크인 완료 상태를 조회한다.
이 API는 조회 전용이며 예약, 방문, 회차, 홀드와 정원 상태를 변경하지 않는다.

### Request

```http
GET /me/reservations/{reservationId}
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/me/reservations/{reservationId}
```

#### Request Example

```http
GET /api/v1/me/reservations/123 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | Long | Y | 조회할 예약 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

### Response

#### Status

```http
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "예약 상세 조회에 성공했습니다.",
  "data": {
    "reservation": {
      "reservationId": 123,
      "reservationNo": "R202607290001",
      "status": "CONFIRMED",
      "confirmedAt": "2026-07-29T12:00:00+09:00",
      "cancelledAt": null,
      "cancellationReason": null,
      "expiredAt": null
    },
    "session": {
      "sessionId": 456,
      "contentId": 789,
      "status": "SCHEDULED",
      "startsAt": "2026-08-03T14:00:00+09:00",
      "endsAt": "2026-08-03T16:00:00+09:00",
      "checkinOpenAt": "2026-08-03T13:30:00+09:00",
      "checkinCloseAt": "2026-08-03T16:00:00+09:00"
    },
    "checkIn": {
      "checkedIn": false,
      "checkedAt": null
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservation.reservationId` | Long | 예약 식별자 |
| `data.reservation.reservationNo` | String | 시스템 전체에서 유일한 예약 번호. 형식은 서버가 생성한다. |
| `data.reservation.status` | String | 예약 상태. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 중 하나 |
| `data.reservation.confirmedAt` | String | 예약 확정 시각 |
| `data.reservation.cancelledAt` | String or null | `CANCELLED` 상태의 취소 시각. 그 외 상태에서는 `null` |
| `data.reservation.cancellationReason` | String or null | `CANCELLED` 상태의 취소 사유. 그 외 상태에서는 `null` |
| `data.reservation.expiredAt` | String or null | `EXPIRED` 상태의 노쇼 처리 시각. 그 외 상태에서는 `null` |
| `data.session.sessionId` | Long | 예약이 속한 회차 식별자 |
| `data.session.contentId` | Long | 회차가 속한 콘텐츠 식별자 |
| `data.session.status` | String | 회차 상태. `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `data.session.startsAt` | String | 회차 시작 시각 |
| `data.session.endsAt` | String | 회차 종료 시각 |
| `data.session.checkinOpenAt` | String | 체크인 창 시작 시각 |
| `data.session.checkinCloseAt` | String | 체크인 창 종료 시각 |
| `data.checkIn.checkedIn` | Boolean | 예약 상태가 `CHECKED_IN`이면 `true`, 그 외에는 `false` |
| `data.checkIn.checkedAt` | String or null | `checkedIn = true`인 경우 방문 기록의 체크인 시각. 그 외에는 `null` |

예약 QR 토큰, `qr_reference`, 사용자 식별자와 다른 예약자의 정보는 응답에 포함하지 않는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationId`가 양수가 아니다. 조회 대상과 상태를 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `reservationId`의 형식이 올바르지 않다. 조회 대상과 상태를 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 조회 대상과 상태를 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 예약의 소유자가 아니다. 조회 대상과 상태를 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약을 찾을 수 없다. 조회 대상과 상태를 변경하지 않으며 예약 식별자를 확인한 뒤 재시도할 수 있다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예약 상세 조회 중 예상하지 못한 서버 오류 또는 예약·방문 연결 정합성 오류가 발생했다. 조회 대상과 상태를 변경하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있지만 정합성 오류는 해결 전까지 재시도해도 성공하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 예약의 `user_id`가 일치해야 한다.
3. `reservationId`의 필수 여부, 타입과 양수 범위를 검증한다.
4. 예약은 어느 상태에서나 조회할 수 있다. `CONFIRMED`, `CHECKED_IN`, `CANCELLED`, `EXPIRED` 상태에 따라 nullable 시각과 사유 필드를 반환한다.
5. 회차는 예약의 `session_id`와 일치하는 회차만 반환하며, 예약·회차·콘텐츠·지역의 연결 일치 제약을 유지한다.
6. 예약 상태가 `CHECKED_IN`이면 같은 예약과 회차에 연결된 방문 기록이 정확히 한 건 존재해야 하며, `checkedIn = true`와 그 방문의 `checked_at`을 반환한다.
7. 예약 상태가 `CHECKED_IN`이 아니면 `checkedIn = false`, `checkedAt = null`을 반환한다.
8. `CHECKED_IN` 예약에 방문 기록이 없거나 방문의 예약·회차·콘텐츠·지역 연결이 일치하지 않으면 정상 응답을 만들지 않고 정합성 오류로 관찰한다.
9. 조회 시 예약, 방문, 회차, 홀드, 정원, QR과 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 상태 전이나 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 예약·회차·방문 식별자와 결과 코드만 구조화 로그로 남기며, 예약 번호·QR 참조·사용자 식별자와 개인정보 원문을 로그에 남기지 않는다.
- `CHECKED_IN` 상태와 방문 기록 연결의 불일치는 데이터 정합성 오류로 관찰하고, 다른 예약의 정보로 대체해 응답하지 않는다.
