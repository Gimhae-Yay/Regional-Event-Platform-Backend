## 5. 내 예약 단기 QR 조회·발급

활성 방문자가 내 예약 화면에서 체크인용 단기 QR을 조회하고 발급받는다. 저장된 정적 QR을 반환하지 않으며,
조회 시점에 발급 조건을 다시 검증해 짧은 TTL의 HMAC 토큰을 생성한다. 이 경로가 QR 발급·갱신의 유일한 HTTP 계약이다.

### Request

```http
GET /me/reservations/{reservationId}/qr
```

실제 요청 경로는 다음과 같다.

```http
GET /api/v1/me/reservations/{reservationId}/qr
```

#### Request Example

```http
GET /api/v1/me/reservations/123/qr HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 예약에 연결된 활성 방문자여야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | Long | Y | QR을 조회해 발급받을 내 예약 식별자. 양수여야 한다. |

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
  "message": "예약 QR 조회에 성공했습니다.",
  "data": {
    "reservationId": 123,
    "sessionId": 456,
    "qrToken": "v1.k1.eyJ...signature",
    "issuedAt": "2026-08-01T10:05:00+09:00",
    "expiresAt": "2026-08-01T10:10:00+09:00",
    "checkinClosesAt": "2026-08-01T10:30:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.reservationId` | Long | QR 발급 자격을 검증한 내 예약 식별자 |
| `data.sessionId` | Long | 예약 회차 식별자 |
| `data.qrToken` | String | 조회 시점에 HMAC-SHA256으로 서명한 단기 QR 토큰 |
| `data.issuedAt` | String | MySQL 기준 토큰 발급 시각 |
| `data.expiresAt` | String | `issuedAt + 운영 설정 TTL`과 `checkinClosesAt` 중 이른 시각 |
| `data.checkinClosesAt` | String | 회차의 체크인 종료 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `reservationId`가 양수가 아니다. QR을 발급하지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `reservationId`의 형식이 올바르지 않다. QR을 발급하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. QR을 발급하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 방문자가 아니거나 대상 예약의 소유자가 아니다. QR을 발급하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약을 찾을 수 없다. QR을 발급하지 않으며 식별자를 확인한 뒤 재시도할 수 있다. |
| `409` | `QR_ISSUE_CONFLICT` | 예약이 `CONFIRMED`가 아니거나(체크인 완료 후 `CHECKED_IN` 포함) 활성 회원 연결이 없거나, 회차가 `SCHEDULED`가 아니거나, 체크인 창 밖이거나, 다른 상태 전이가 먼저 성공했다. QR을 발급하지 않으며 현재 예약·회차 상태를 다시 조회해야 한다. |
| `500` | `INTERNAL_SERVER_ERROR` | QR 서명 키 설정 또는 예상하지 못한 서버 오류가 발생했다. QR을 발급하지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "QR_ISSUE_CONFLICT",
  "message": "QR을 발급할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태이며 대상 `reservation.user_id`와 일치해야 한다.
2. 예약은 활성 회원 연결이 있는 `CONFIRMED`, 회차는 `SCHEDULED`여야 한다.
3. MySQL 기준 현재 시각이 `checkin_open_at <= now < checkin_close_at`인 경우에만 조회·발급 응답을 생성한다.
4. 성공할 때마다 현재 시각을 기준으로 새 단기 토큰을 생성할 수 있다. HTTP `GET` 응답의 QR 원문과 발급 이력은 서버 상태로 저장하지 않는다.
5. 토큰은 HMAC-SHA256으로 서명하고 토큰 버전, 키 식별자, `qr_reference`, `session_id`, 만료 시각만 포함한다. 이름, 연락처와 `user_id`는 포함하지 않는다.
6. `expiresAt`은 `issuedAt + 운영 설정의 짧은 TTL`과 `checkin_close_at` 중 이른 시각이다. 기존 토큰은 자신의 만료 시각과 체크인 창 종료 시각 중 이른 시각까지만 유효하다.
7. 콘텐츠가 `SUSPENDED`, `WITHDRAWN`, `ENDED`여도 회차가 명시적으로 취소되지 않았고 기존 예약·회차 조건을 만족하면 발급 자격을 유지한다. 키 회전은 토큰 버전과 키 식별자로 검증한다.
8. 브라우저, CDN과 중간 캐시가 QR 응답을 저장하지 않도록 `Cache-Control: no-store`를 적용한다.
9. 이 API에는 체크인 명령용 `Idempotency-Key`를 적용하지 않는다.

### 보안 및 정합성

- QR 토큰 원문, HMAC 키, 개인정보를 데이터베이스, 감사 이벤트, 구조화 로그와 캐시에 저장하지 않는다.
- 응답에는 다른 예약 정보, 이름·연락처, 사용자 식별자를 포함하지 않는다.
- 이 조회·발급은 예약·회차·정원·방문 상태를 변경하지 않는다.
