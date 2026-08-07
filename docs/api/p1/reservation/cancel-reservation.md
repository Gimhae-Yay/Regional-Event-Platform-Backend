# 유료 예약 취소 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-05`, `RSV-04` |
| 소유 도메인 | 예약 |
| 기준 문서 | [예약 API](reservation.md), [수동 환불 API](../refund/create-refund.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [P1 ERD](../../../p1-erd.md), [ADR-0001](../../../adr/0001-use-mysql-conditional-update-for-capacity-consistency.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자가 회차 시작 전 본인의 유료 `CONFIRMED` 예약을 취소한다. 예약 취소 자격과 전이는 P0 무료 예약
취소 정책(`RSV-04`)을 그대로 따르며, 연결된 결제가 `APPROVED` 또는 `DISCREPANT`면 같은 트랜잭션 흐름
안에서 전액 환불을 시작한다. 환불 생성·외부 PortOne 호출·확정 규칙은
[수동 환불 API](../refund/create-refund.md#3-수동-환불)의 처리 규칙을 그대로 참조해 재사용하며, 이 문서에서
다시 정의하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-05, RSV-04 | `POST /api/v1/me/reservations/{reservationId}/cancel` | `reservation`, `payment`, `refund`, `refund_attempt`, `coupon_redemption` |

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
    }
  }
}
```

이미 `CANCELLED`인 예약을 다시 요청하면 새 정원 복구나 새 환불 효과를 만들지 않고, 같은 형식으로 현재
예약·환불 상태를 그대로 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.reservationId` | String | 취소한 예약 식별자다. |
| `data.reservationStatus` | String | 취소 처리 뒤 예약 상태다. 이 API에서는 항상 `CANCELLED`다. |
| `data.refund.refundId` | String | 시작되었거나 기존에 있던 환불 식별자다. |
| `data.refund.paymentId` | String | 환불 대상 결제 식별자다. |
| `data.refund.amount` | Integer | 환불 금액이다. 결제 최종 금액 전체다. |
| `data.refund.currency` | String | 통화 코드다. |
| `data.refund.status` | String | 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.refund.requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |

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
3. 이미 `CANCELLED`인 예약의 재요청은 새 정원 복구나 새 환불 효과를 만들지 않고 현재 예약·환불 상태를 그대로 반환해, 같은 요청의 재시도가 중복 효과를 만들지 않게 한다(`refund.payment_id` 자연 멱등 재사용).
4. 조건을 만족하면 같은 트랜잭션에서 예약을 `CONFIRMED → CANCELLED`로 전환하고, 성공한 최초 전이에서만 정원을 한 번 복구한다(`RSV-04`, [ADR-0001](../../../adr/0001-use-mysql-conditional-update-for-capacity-consistency.md)).
5. 취소 대상 결제가 `APPROVED` 또는 `DISCREPANT`면 같은 트랜잭션에서 [수동 환불 API](../refund/create-refund.md#3-수동-환불) 처리 규칙 5~7번과 동일하게 환불을 시작한다: `refund(REQUESTED)` 생성 뒤 즉시 `PROCESSING`으로 전이하고, 외부 호출 직전에 `refund_attempt(PENDING, attempt_no=1)`를 기록해 PortOne 취소를 호출한다(최대 응답 대기 30초). 응답 결과에 따라 `SUCCEEDED`·`FAILED`·`DISCREPANT`로 확정한다.
6. 대상 결제의 가격 스냅샷에 적용 쿠폰이 있으면 같은 트랜잭션에서 `coupon_redemption`을 복구 가능한 상태로 되돌린다([수동 환불 API](../refund/create-refund.md#3-수동-환불) 8번과 동일).
7. 이 API는 결제를 승인 처리하거나 환불 실패를 수동으로 재처리하지 않는다. 환불이 `FAILED` 또는 `DISCREPANT`로 종결되면 이후 재시도·수동 조치는 전체관리자 절차([환불 재시도](../refund/retry-refund.md), [환불 실패 수동 조치](../refund/resolve-refund-failure.md))를 따른다.
8. 예약 취소와 서버가 부여한 `requestId`를 포함한 `RESERVATION` 감사 이력, 환불을 시작한 경우의 `REFUND` 감사 이력은 하나의 트랜잭션으로 처리한다.
9. 결제 비밀값, PortOne 원문과 전체 결제수단 정보는 저장하지 않는다.
