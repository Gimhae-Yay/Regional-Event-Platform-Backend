# 유료 예약 결제 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-01` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [ADR-0069](../../../adr/0069-use-p0-capacity-hold-and-reservation-price-snapshot-for-paid-checkout.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 회원이 본인이 생성한 활성 `capacity_hold`에 대해 유료 결제를 생성한다. 최초 요청은 홀드에 연결된
불변 `reservation_price_snapshot`을 만들고, 최종 금액이 양수이면 내부 결제 시도(`payment`, `PENDING`)를
생성한다. 최종 금액이 0원이면 PortOne을 호출하지 않고 P0 무료 예약 확정 흐름으로 즉시 예약을 확정한다
(`ADR-0069`). 이 API는 예약을 확정하지 않는다. 양수 결제의 예약 확정은
[PortOne 결제 웹훅 수신](receive-portone-webhook.md)에서만 처리한다.

이 API는 영속 멱등 처리 대상이다. 동일한 `Idempotency-Key`와 동일한 요청 의미의 재시도는 최초 처리 결과를
반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-01 | `POST /api/v1/me/reservation-holds/{holdId}/payments` | `capacity_hold`, `reservation_price_snapshot`, `payment`, `payment_idempotency`, `coupon` |

## 2. 공통 계약 참조

생성·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다.

## 3. 유료 예약 결제 생성

### Request

```http
POST /api/v1/me/reservation-holds/{holdId}/payments
```

#### Request Example

```http
POST /api/v1/me/reservation-holds/789/payments HTTP/1.1
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "couponId": "401"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 활성 회원이어야 한다. |
| `Idempotency-Key` | Y | 클라이언트가 생성한 비어 있지 않은 멱등 키다. 같은 결제 생성 시도의 재시도에는 반드시 같은 값을, 새 결제 생성 요청에는 새 값을 사용한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `holdId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 정원 홀드 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "couponId": "401"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `couponId` | String | N | 이 결제에 적용할 쿠폰 식별자다. 제공하지 않으면 쿠폰을 적용하지 않는다. 제공하면 양의 10진 문자열·signed 64비트 `Long` 범위를 만족해야 한다. |

### Response

두 가지 결과가 있다. 최종 금액이 양수이면 결제 시도가 생성되고, 0원이면 결제 없이 예약이 즉시 확정된다.

#### Status

```http
201 Created
```

#### Response Body

최종 금액이 양수이면 다음과 같이 응답한다.

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "유료 예약 결제 생성에 성공했습니다.",
  "data": {
    "requiresPayment": true,
    "payment": {
      "paymentId": "901",
      "holdId": "789",
      "orderId": "ORD-20260806-9F3K7Q",
      "status": "PENDING",
      "amount": {
        "baseAmount": 20000,
        "discountAmount": 3000,
        "finalAmount": 17000,
        "currency": "KRW"
      },
      "createdAt": "2026-08-06T02:30:00Z"
    },
    "reservation": null
  }
}
```

최종 금액이 0원이면 결제 없이 예약이 즉시 확정되며 다음과 같이 응답한다.

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "유료 예약 결제 생성에 성공했습니다.",
  "data": {
    "requiresPayment": false,
    "payment": null,
    "reservation": {
      "reservationId": "123",
      "reservationNo": "R20260806A7K3M9Q2W5XZ",
      "holdId": "789",
      "status": "CONFIRMED",
      "confirmedAt": "2026-08-06T02:30:00Z"
    }
  }
}
```

동일한 `Idempotency-Key`와 `holdId`로 완료된 요청을 재시도한 경우에도 최초 성공과 동일한 결과를 `201 Created`로 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.requiresPayment` | Boolean | `true`이면 클라이언트가 `data.payment`의 정보로 PortOne 결제 절차를 계속 진행해야 한다. `false`이면 결제 없이 예약이 이미 확정됐다. |
| `data.payment` | Object 또는 null | `requiresPayment = true`일 때만 값이 있다. |
| `data.payment.paymentId` | String | 생성된 내부 결제 식별자다. |
| `data.payment.holdId` | String | 결제 대상 정원 홀드 식별자다. |
| `data.payment.orderId` | String | 서버가 발급한 내부 주문 식별자다. 클라이언트는 이 값으로 PortOne 결제 절차를 시작한다. |
| `data.payment.status` | String | 항상 `PENDING`이다. |
| `data.payment.amount.baseAmount` | Integer | 쿠폰 적용 전 기본 금액이다. |
| `data.payment.amount.discountAmount` | Integer | 쿠폰 할인 금액이다. 쿠폰을 적용하지 않으면 `0`이다. |
| `data.payment.amount.finalAmount` | Integer | 결제할 최종 금액이다. `baseAmount - discountAmount`이며 항상 1 이상이다. |
| `data.payment.amount.currency` | String | 통화 코드다. 현재는 항상 `KRW`다. |
| `data.payment.createdAt` | String | 결제 시도 생성 시각이다. UTC ISO 8601 형식이다. |
| `data.reservation` | Object 또는 null | `requiresPayment = false`일 때만 값이 있다. |
| `data.reservation.reservationId` | String | 확정된 예약 식별자다. |
| `data.reservation.reservationNo` | String | 시스템 전체에서 유일한 예약 번호다. |
| `data.reservation.holdId` | String | 소비된 정원 홀드 식별자다. |
| `data.reservation.status` | String | 항상 `CONFIRMED`다. |
| `data.reservation.confirmedAt` | String | 예약 확정 시각이다. UTC ISO 8601 형식이다. |

예약 QR은 이 API에서 발급하지 않는다. `CONFIRMED` 예약은 기존 P0 [내 예약 QR 조회](../../p0/check-in/get-my-reservation-qr.md)로 발급받는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `holdId` 또는 `couponId`를 양의 정수 식별자로 처리할 수 없다. 결제·스냅샷·멱등 기록을 생성하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `holdId`·`couponId`가 범위를 벗어나거나 `Idempotency-Key`가 없거나 비어 있다. 결제·스냅샷·멱등 기록을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 결제·스냅샷·멱등 기록을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 결제·스냅샷·멱등 기록을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 홀드의 소유자가 아니다. 결제·스냅샷·멱등 기록을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 대상 홀드 또는 요청한 쿠폰이 없다. 결제·스냅샷·멱등 기록을 생성하지 않는다. |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 같은 회원의 `PAYMENT_CREATE` 명령에서 이미 다른 `holdId`에 사용한 `Idempotency-Key`다. 새 결제를 만들지 않으며 새 요청에는 새 키를 사용해야 한다. |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 회원·키·홀드의 최초 요청이 아직 처리 중이다. 새 결제를 만들지 않으며 동일 키로 재시도할 수 있다. |
| `409` | `PAYMENT_HOLD_CONFLICT` | 홀드가 유효한 `ACTIVE`가 아니거나(만료·소비·무효화), 홀드에 다른 `Idempotency-Key`의 진행 중 `PENDING` 결제가 이미 있거나, 요청한 쿠폰이 사용 가능한 상태가 아니다. 결제·스냅샷을 생성하지 않으며 동일 상태에서 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 결제·스냅샷·멱등 기록을 생성하지 않으며 일시적 장애라면 동일한 `Idempotency-Key`로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "PAYMENT_HOLD_CONFLICT",
  "message": "결제를 생성할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이며 대상 홀드의 `user_id`와 일치해야 한다.
2. `holdId`, `Idempotency-Key`의 필수 여부와 형식을 먼저 검증한다.
3. 멱등 키의 논리 유일 범위는 `(actor_user_id, operation = PAYMENT_CREATE, idempotency_key_hash)`다. 같은 키·같은 `request_hash`면 `payment_idempotency`에 연결된 기존 결제 또는 0원 확정 결과를 재구성해 반환하고 새 스냅샷·결제·멱등 작업을 실행하지 않는다. 같은 키·다른 요청 해시는 `409 IDEMPOTENCY_KEY_CONFLICT`로 거부한다.
4. 대상 홀드는 유효한 `ACTIVE` 상태여야 하고 `expires_at`이 MySQL 기준 현재 시각보다 미래여야 한다.
5. 홀드에 이미 다른 `Idempotency-Key`로 생성된 진행 중 `PENDING` 결제가 있으면 새 결제를 만들지 않고 `409 PAYMENT_HOLD_CONFLICT`로 거부한다. 홀드당 진행 중 결제는 최대 하나다.
6. 홀드에 아직 `reservation_price_snapshot`이 없으면 이 요청에서 한 번만 생성한다(`UNIQUE (hold_id)`). 같은 홀드의 재시도는 항상 같은 스냅샷을 사용한다.
7. `couponId`를 제공하면 쿠폰이 인증 회원 소유이고 `AVAILABLE` 상태이며 스냅샷 대상 콘텐츠의 지역과 일치하는지 검증한 뒤 `RESERVED`로 전이하고 스냅샷에 연결한다. 한 스냅샷에는 쿠폰을 최대 하나만 연결한다.
8. `final_amount = base_amount - discount_amount`를 계산한다.
9. `final_amount > 0`이면 `order_id`를 발급하고 `payment(PENDING)`을 생성해 스냅샷·홀드에 연결한다. PortOne은 호출하지 않는다. 클라이언트는 응답의 `orderId`로 PortOne 결제 절차를 계속한다.
10. `final_amount = 0`이면 `payment` 행을 만들지 않고 P0 무료 예약 확정 흐름을 그대로 사용해 홀드를 `CONSUMED`로 전환하고 `CONFIRMED` 예약을 생성한다. 쿠폰을 적용했으면 같은 트랜잭션에서 `coupon_redemption(CONFIRMED)`도 생성한다.
11. 스냅샷 생성(또는 재사용), 쿠폰 상태 전이, 결제 생성 또는 0원 확정과 멱등 기록 점유는 하나의 MySQL 트랜잭션에서 커밋한다.
12. 오류가 발생하면 스냅샷·결제·쿠폰 전이·예약을 반영하지 않는다.
13. `payment.hold_id`, `payment.reservation_price_snapshot_id`는 `NOT NULL`이며 홀드당 진행 중 `PENDING` 결제는 유일하다. `payment_idempotency`의 `(actor_user_id, operation, idempotency_key_hash)`는 유일하며 `actor_user_id`는 `app_user`를 참조하지 않는 비-FK 식별값이다.
14. 0원 확정 성공은 P0와 동일하게 `CAPACITY_HOLD`, `RESERVATION` 감사 이벤트 두 건을 같은 `requestId`로 기록한다. 양수 결제 생성 성공은 결제 시도만 기록하며 예약 관련 감사 이벤트는 [PortOne 결제 웹훅 수신](receive-portone-webhook.md)에서 기록한다.
15. 결제 종결 시 `payment_idempotency.expires_at = payment.finalized_at + 24시간`으로 정하며, 만료한 종결 기록만 정리한다.
