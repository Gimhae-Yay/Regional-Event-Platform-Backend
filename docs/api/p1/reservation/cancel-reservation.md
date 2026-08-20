# 유료 예약 취소 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-05`, `RSV-04` |
| 소유 도메인 | 예약 |
| 기준 문서 | [예약 API](reservation.md), [수동 환불 API](../refund/create-refund.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [P1 ERD](../../../p1-erd.md), [ADR-0001](../../../adr/0001-use-mysql-conditional-update-for-capacity-consistency.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자가 회차 시작 전 본인의 유료 `CONFIRMED` 예약을 취소한다. 예약 취소 자격과 전이는 P0 무료 예약
취소 정책(`RSV-04`)을 그대로 따른다. 최종 금액이 0원이어서 결제 행이 없는 예약은 취소와 함께 쿠폰을
복구하고, 연결된 결제가 `APPROVED` 또는 `DISCREPANT`면 같은 트랜잭션 흐름 안에서 전액 환불을 시작한다.
환불 생성·외부 PortOne 호출·확정 규칙은
[수동 환불 API](../refund/create-refund.md#3-수동-환불)의 처리 규칙을 그대로 참조해 재사용하며, 이 문서에서
다시 정의하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-05, RSV-04 | `POST /api/v1/me/reservations/{reservationId}/cancel` | `reservation`, `reservation_price_snapshot`, `payment`, `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history` |

## 2. 공통 계약 참조

인증·응답·오류 규칙은 [API 공통 계약](../../common/README.md)을 따른다. 환불 생성·전이 처리 규칙은
[수동 환불 API](../refund/create-refund.md#3-수동-환불) 5~9번을 참조로 재사용한다.

## 3. 유료 예약 취소

### Request

```http
POST /api/v1/me/reservations/{reservationId}/cancel
```

#### Request Example

```http
POST /api/v1/me/reservations/124/cancel HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 대상 예약을 소유한 활성 방문자여야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 확정 예약 식별자다. |

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
  "message": "예약 취소에 성공했습니다.",
  "data": {
    "reservationId": "124",
    "reservationStatus": "CANCELLED",
    "refund": {
      "refundId": "551",
      "paymentId": "902",
      "amount": 15000,
      "currency": "KRW",
      "status": "PROCESSING",
      "requestedAt": "2026-08-07T01:00:00Z"
    },
    "sessionId": "456",
    "status": "CANCELLED",
    "cancellationReason": "USER_REQUEST",
    "cancelledAt": "2026-08-07T01:00:00Z",
    "capacityReleasedAt": "2026-08-07T01:00:00Z"
  }
}
```

이미 `CANCELLED`인 예약을 다시 요청하면 새 정원 복구나 새 환불 효과를 만들지 않고, 같은 형식으로 현재
예약·환불 상태를 그대로 반환한다.

환불 응답이 없으면 `refund: null`을 반환한다. 최종 금액이 0원이어서 결제 행이 없는 경우와 연결 결제가
`APPROVED`·`DISCREPANT`가 아닌 경우에는 환불을 시작하지 않는다. 취소 재요청에서는 기존 환불을 찾지 못하면
`refund: null`을 반환한다.

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "예약 취소에 성공했습니다.",
  "data": {
    "reservationId": "123",
    "reservationStatus": "CANCELLED",
    "refund": null,
    "sessionId": "456",
    "status": "CANCELLED",
    "cancellationReason": "USER_REQUEST",
    "cancelledAt": "2026-08-07T01:00:00Z",
    "capacityReleasedAt": "2026-08-07T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.reservationId` | String | 취소한 예약 식별자다. |
| `data.reservationStatus` | String | 취소 처리 뒤 예약 상태다. 항상 `CANCELLED`이며 현재 `data.status`와 같은 값을 중복 표현한다. |
| `data.refund` | Object 또는 null | 환불 응답이 있으면 객체다. 결제 행이 없거나 연결 결제가 `APPROVED`·`DISCREPANT`가 아니거나, 취소 재요청에서 기존 환불을 찾지 못하면 `null`이다. |
| `data.refund.refundId` | String | `data.refund`가 객체일 때만 존재한다. 시작되었거나 기존에 있던 환불 식별자다. |
| `data.refund.paymentId` | String | `data.refund`가 객체일 때만 존재한다. 환불 대상 결제 식별자다. |
| `data.refund.amount` | Integer | `data.refund`가 객체일 때만 존재한다. 환불 금액이며 결제 최종 금액 전체다. |
| `data.refund.currency` | String | `data.refund`가 객체일 때만 존재한다. 통화 코드다. |
| `data.refund.status` | String | `data.refund`가 객체일 때만 존재한다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.refund.requestedAt` | String | `data.refund`가 객체일 때만 존재한다. 환불 요청 시각이며 UTC ISO 8601 형식이다. |
| `data.sessionId` | String | 예약이 속한 회차 식별자다. |
| `data.status` | String | 취소 처리 뒤 예약 상태다. 항상 `CANCELLED`이며 현재 `data.reservationStatus`와 같은 값을 중복 표현한다. |
| `data.cancellationReason` | String | 최초 취소 사유다. 이 API로 최초 취소한 경우 `USER_REQUEST`다. |
| `data.cancelledAt` | String | 최초 취소 시각이며 UTC ISO 8601 형식이다. |
| `data.capacityReleasedAt` | String or null | 최초 정원 복구 시각이며 UTC ISO 8601 형식이다. 정원을 복구하지 않은 취소는 `null`이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `reservationId`를 양의 정수 식별자로 처리할 수 없다. 예약·환불 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 예약·환불 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약이 없거나 인증 주체가 소유한 예약이 아니다. 다른 사용자의 예약 존재 여부를 노출하지 않으며 예약·환불 상태를 변경하지 않는다. |
| `409` | `RESERVATION_CANCEL_CONFLICT` | 대상 예약이 `CONFIRMED`가 아니거나(`CHECKED_IN`·`EXPIRED`는 취소 대상이 아니다), DB 시각이 이미 회차 시작 이후다. 예약·환불 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | PortOne 호출 실패를 포함해 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 예약·환불 상태를 변경하지 않으며 일시적 장애라면 동일한 요청으로 재시도할 수 있다. |

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

1. 인증 주체는 대상 예약을 소유한 활성 방문자여야 한다. 다른 사용자의 예약은 조회·취소할 수 없으며 존재 여부도 노출하지 않는다.
2. 대상 예약은 `CONFIRMED`여야 하며 DB 기준 시각이 회차 시작 전이어야 한다. 그 외 상태이거나 회차가 이미 시작했으면 `409 RESERVATION_CANCEL_CONFLICT`로 거부한다.
3. 이미 `CANCELLED`인 예약의 재요청은 새 정원 복구·환불·쿠폰 복구 효과를 만들지 않고 현재 예약과 기존 환불 또는 `refund: null` 결과를 그대로 반환한다.
4. 조건을 만족하면 같은 트랜잭션에서 예약을 `CONFIRMED → CANCELLED`로 전환하고, 성공한 최초 전이에서만 정원을 한 번 복구한다(`RSV-04`, [ADR-0001](../../../adr/0001-use-mysql-conditional-update-for-capacity-consistency.md)).
5. 취소 대상 결제가 `APPROVED` 또는 `DISCREPANT`면 같은 트랜잭션에서 [수동 환불 API](../refund/create-refund.md#3-수동-환불) 처리 규칙 5~7번과 동일하게 환불을 시작한다: `refund(REQUESTED)` 생성 뒤 즉시 `PROCESSING`으로 전이하고, 외부 호출 직전에 `refund_attempt(PENDING, attempt_no=1)`를 기록해 PortOne 취소를 호출한다(최대 응답 대기 30초). 응답 결과에 따라 `SUCCEEDED`·`FAILED`·`DISCREPANT`로 확정한다.
6. 5번의 환불이 어느 처리 경로에서든 최초 `SUCCEEDED`로 전이하면 [환불 공통 쿠폰 복구 계약](../refund/refund.md#쿠폰-복구-계약)을 적용한다. `FAILED` 또는 `DISCREPANT`에서는 쿠폰을 복구하지 않는다.
7. 가격 스냅샷의 `final_amount = 0`이고 결제 행이 없으면 환불을 만들지 않는다. 쿠폰이 적용됐다면 예약의 `hold_id`와 가격 스냅샷의 `hold_id`, 사용 이력의 `reservation_id`·`reservation_price_snapshot_id`·`coupon_id`가 각각 취소 대상 예약·스냅샷·적용 쿠폰과 일치하고, 사용 이력은 `CONFIRMED`, 쿠폰은 `USED`인지 검증한다. 최초 예약 취소 트랜잭션에서 검증된 `coupon_redemption`을 `REVERSED`로 전이하고 `refund_id = NULL`, `reversal_reason_code = RESERVATION_CANCELLED`, MySQL 현재 시각으로 고정한 `reversed_at`을 기록한다. 같은 시각을 기준으로 원래 만료 시각 전이면 쿠폰을 `AVAILABLE`, 지났으면 `EXPIRED`로 전이해 상태 이력을 기록한다. 적용 쿠폰이 없으면 쿠폰 처리 없이 `refund: null`로 취소한다.
8. 이 API는 결제를 승인 처리하거나 환불 실패를 수동으로 재처리하지 않는다. 환불이 `FAILED` 또는 `DISCREPANT`로 종결되면 이후 재시도·수동 조치는 전체관리자 절차([환불 재시도](../refund/retry-refund.md), [환불 실패 수동 조치](../refund/resolve-refund-failure.md))를 따른다.
9. 예약 취소와 서버가 부여한 `requestId`를 포함한 `RESERVATION` 감사 이력, 환불을 시작한 경우의 `REFUND` 감사 이력, 0원 예약의 쿠폰 복구가 있으면 `COUPON` 감사 이력을 같은 처리 결과에 연결한다.
10. 결제 비밀값, PortOne 원문과 전체 결제수단 정보는 저장하지 않는다.
