# 서버 결제 승인 확인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-02`, `PAY-03` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [ADR-0069](../../../adr/0069-use-p0-capacity-hold-and-reservation-price-snapshot-for-paid-checkout.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자가 PortOne 결제 절차를 마친 뒤 클라이언트가 호출해 서버 검증을 트리거한다. 서버는 클라이언트가 보낸
성공 표시를 그대로 믿지 않고 PortOne V2에서 거래를 재조회해 외부 거래 식별자·금액·통화·주문 식별자가
대상 홀드·가격 스냅샷과 일치하는지 검증한다. 검증에 성공하면 같은 트랜잭션에서 홀드 소비, 예약 확정과
결제 승인을 함께 처리한다.

이 API와 [PortOne 결제 웹훅 수신](receive-portone-webhook.md)은 같은 결제를 확정시키는 두 개의 진입점이다
(`PAY-04`). 순서가 뒤바뀌거나 동시에 도착해도 하나의 내부 처리 결과로 수렴하며, 어느 한쪽이 다른 쪽을
대체하지 않는다. 이 API는 별도 `Idempotency-Key`를 받지 않는다. 결제가 이미 종결 상태이면 외부 재조회
없이 저장된 결과를 그대로 반환해 자연스럽게 멱등하다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-02, PAY-03 | `POST /api/v1/me/payments/{paymentId}/confirm` | `payment`, `payment_verification`, `capacity_hold`, `reservation`, `coupon_redemption`, `payment_discrepancy` |

## 2. 공통 계약 참조

확인·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다.

## 3. 서버 결제 승인 확인

### Request

```http
POST /api/v1/me/payments/{paymentId}/confirm
```

#### Request Example

```http
POST /api/v1/me/payments/901/confirm HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `paymentId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 결제 식별자다. |

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

검증 성공(`status = APPROVED`)이면 확정된 예약 정보를 함께 반환한다.

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "서버 결제 승인 확인에 성공했습니다.",
  "data": {
    "paymentId": "901",
    "status": "APPROVED",
    "verifiedAt": "2026-08-06T02:31:10Z",
    "reservation": {
      "reservationId": "123",
      "reservationNo": "R20260806A7K3M9Q2W5XZ",
      "holdId": "789",
      "status": "CONFIRMED",
      "confirmedAt": "2026-08-06T02:31:10Z"
    }
  }
}
```

외부 결제가 아직 진행 중이라 `status = PENDING`을 유지하는 경우, 그리고 `status`가 `DECLINED`,
`CANCELLED`, `EXPIRED`, `DISCREPANT`인 경우 모두 예약이 없으므로 같은 형식으로 `reservation: null`을
반환한다.

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "서버 결제 승인 확인에 성공했습니다.",
  "data": {
    "paymentId": "901",
    "status": "PENDING",
    "verifiedAt": "2026-08-06T02:31:10Z",
    "reservation": null
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.paymentId` | String | 확인한 결제 식별자다. |
| `data.status` | String | 확인 시점의 결제 상태다. `PENDING`, `APPROVED`, `DECLINED`, `CANCELLED`, `EXPIRED`, `DISCREPANT` 중 하나다. |
| `data.verifiedAt` | String | 이번 확인 처리 시각이다. UTC ISO 8601 형식이다. |
| `data.reservation` | Object 또는 null | `status = APPROVED`일 때만 값이 있다. |
| `data.reservation.reservationId` | String | 확정된 예약 식별자다. |
| `data.reservation.reservationNo` | String | 시스템 전체에서 유일한 예약 번호다. |
| `data.reservation.holdId` | String | 소비된 정원 홀드 식별자다. |
| `data.reservation.status` | String | 항상 `CONFIRMED`다. |
| `data.reservation.confirmedAt` | String | 예약 확정 시각이다. UTC ISO 8601 형식이다. |

예약 QR은 이 API에서 발급하지 않는다. `CONFIRMED` 예약은 기존 P0 [내 예약 QR 조회](../../p0/check-in/get-my-reservation-qr.md)로 발급받는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `paymentId`를 양의 정수 식별자로 처리할 수 없다. 결제·예약 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 결제·예약 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 결제가 연결된 홀드의 소유자가 아니다. 결제·예약 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 결제가 없다. 결제·예약 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | PortOne 조회 실패를 포함해 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 결제·예약 상태를 변경하지 않으며 일시적 장애라면 동일한 요청으로 재시도할 수 있다. |

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

1. 인증 주체는 `ACTIVE` 상태의 회원이며 대상 결제가 연결된 홀드의 `user_id`와 일치해야 한다.
2. 대상 결제가 이미 종결 상태(`APPROVED`, `DECLINED`, `CANCELLED`, `EXPIRED`, `DISCREPANT`)이면 PortOne을 다시 조회하지 않고 저장된 상태와 결과를 그대로 반환한다. 홀드·예약·쿠폰을 다시 변경하지 않는다.
3. `PENDING`이면 서버가 `payment.order_id`로 PortOne V2 거래를 조회한다. 조회·검증에 사용한 외부 응답 요약과 판정 결과는 `payment_verification`에 기록한다.
4. 검증은 외부 거래 식별자, 금액, 통화, 주문 식별자와 홀드·가격 스냅샷 대상의 일치 여부를 확인한다. 하나라도 다르면 성공 상태로 전이하지 않는다.
5. PortOne이 아직 완료되지 않은 거래를 보고하면 `payment.status`는 `PENDING`을 유지하고 `data.status = PENDING`을 반환한다.
6. PortOne이 명시적 거절을 보고하면 `payment.status`를 `DECLINED`로 전이한다. 예약은 생성하지 않으며 홀드는 소비하지 않는다.
7. 검증에 성공하면 같은 트랜잭션에서 홀드를 `ACTIVE → CONSUMED`로 전이하고 `CONFIRMED` 예약을 생성하며, 스냅샷에 적용 쿠폰이 있으면 `coupon_redemption(CONFIRMED)`을 생성하고, `payment.status`를 `APPROVED`로 전이한다.
8. 늦은 승인(홀드가 이미 만료·소비·무효화된 뒤의 외부 성공)이거나 금액·주문 식별자·대상이 일치하지 않으면 예약을 되살리거나 강제로 확정하지 않고 `payment.status`를 `DISCREPANT`로 전이한 뒤 `discrepancy_type`(`LATE_APPROVAL`, `AMOUNT_MISMATCH`, `ORDER_MISMATCH`, `TARGET_MISMATCH` 중 해당하는 값)과 함께 `payment_discrepancy`를 생성한다.
9. 이 API 호출과 웹훅 수신이 같은 결제에 동시에 도착하면 결제 행 잠금 또는 조건부 전이로 하나의 처리만 상태를 변경하고 나머지는 그 결과를 그대로 반환한다.
10. 외부 서비스 지연·실패가 발생해도 PortOne 응답을 기다리는 동안 다른 요청을 막는 장시간 데이터베이스 잠금을 유지하지 않는다.
11. 검증 성공에 따른 홀드 소비·예약 생성은 P0와 동일하게 `CAPACITY_HOLD`, `RESERVATION` 감사 이벤트 두 건을 같은 `requestId`로 기록하고, 결제 승인은 `PAYMENT` 감사 이벤트로 함께 기록한다.
12. `DISCREPANT` 전이는 원본 외부 응답 해시와 내부 판정 결과를 `payment_verification`에 남기고 `payment_discrepancy`를 생성하는 것으로 대체하며, 예약·홀드 상태를 임의로 확정하지 않는다.
13. PortOne 원문, 웹훅 원문, 비밀값과 전체 결제수단 정보는 저장하지 않는다.
