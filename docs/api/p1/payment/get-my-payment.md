# 내 결제 상태 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-06` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자가 본인이 생성한 결제 시도의 현재 상태를 조회한다. 조회는 결제, 홀드, 예약과 감사 이력을 변경하지
않는다. 환불 상태는 이 API의 범위가 아니며 환불 도메인의 내 환불 상태 조회 API를 사용한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-06 | `GET /api/v1/me/payments/{paymentId}` | `payment`, `reservation_price_snapshot` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다.

## 3. 내 결제 상태 조회

### Request

```http
GET /api/v1/me/payments/{paymentId}
```

#### Request Example

```http
GET /api/v1/me/payments/901 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
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

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "내 결제 상태 조회에 성공했습니다.",
  "data": {
    "paymentId": "901",
    "holdId": "789",
    "orderId": "ORD-20260806-9F3K7Q",
    "status": "APPROVED",
    "amount": {
      "baseAmount": 20000,
      "discountAmount": 3000,
      "finalAmount": 17000,
      "currency": "KRW"
    },
    "reservationId": "123",
    "createdAt": "2026-08-06T02:30:00Z",
    "finalizedAt": "2026-08-06T02:31:10Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.paymentId` | String | 결제 식별자다. |
| `data.holdId` | String | 결제 대상 정원 홀드 식별자다. |
| `data.orderId` | String | 서버가 발급한 내부 주문 식별자다. |
| `data.status` | String | 결제 상태다. `PENDING`, `APPROVED`, `DECLINED`, `CANCELLED`, `EXPIRED`, `DISCREPANT` 중 하나다. |
| `data.amount.baseAmount` | Integer | 쿠폰 적용 전 기본 금액이다. |
| `data.amount.discountAmount` | Integer | 쿠폰 할인 금액이다. |
| `data.amount.finalAmount` | Integer | 결제 최종 금액이다. |
| `data.amount.currency` | String | 통화 코드다. 현재는 항상 `KRW`다. |
| `data.reservationId` | String 또는 null | `status = APPROVED`이면 확정된 예약 식별자이고, 그 외에는 `null`이다. |
| `data.createdAt` | String | 결제 시도 생성 시각이다. UTC ISO 8601 형식이다. |
| `data.finalizedAt` | String 또는 null | 결제가 종결 상태이면 종결 시각이고, `PENDING`이면 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `paymentId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 결제 상태를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 결제가 연결된 홀드의 소유자가 아니다. 결제 상태를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 결제가 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 결제·홀드·스냅샷 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증 주체가 대상 결제가 연결된 홀드의 `user_id`와 일치하는지 검증한다. 다른 회원의 결제는 존재 여부를 포함해 노출하지 않는다.
2. `amount`는 결제에 연결된 `reservation_price_snapshot`에서 조회한다.
3. `finalizedAt`은 `payment.finalized_at`을 그대로 사용하며 종결 전에는 `null`이다.
4. 조회 시 결제, 홀드, 예약, 쿠폰과 감사 이력을 생성·수정·삭제하지 않는다.
