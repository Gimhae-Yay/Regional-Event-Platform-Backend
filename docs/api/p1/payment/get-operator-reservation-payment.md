# 담당 예약 결제·환불 상태 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-06` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 본인 담당 콘텐츠에 속한 특정 예약의 결제·환불 현재 상태를 조회해 고객 문의
대응에 사용한다. 이 API는 결제·환불·불일치 상태를 변경하지 않는다.

환불 처리 자체는 이 문서의 범위가 아니다. 취소·환불 요청, 방문자의 내 환불 상태 조회와 환불 재시도는
별도 환불 도메인 문서가 소유한다. 이 API는 운영자 문의 대응을 위해 환불 상태를 읽기 전용으로만 함께
보여준다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, P1-FR-08, PAY-06 | `GET /api/v1/operator/reservations/{reservationId}/payment` | `payment`, `refund`, `reservation`, `payment_discrepancy` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다.

## 3. 담당 예약 결제·환불 상태 조회

### Request

```http
GET /api/v1/operator/reservations/{reservationId}/payment
```

#### Request Example

```http
GET /api/v1/operator/reservations/123/payment HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 승인된 `OPERATOR`여야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reservationId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 예약 식별자다. |

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

결제가 있고 아직 환불이 요청되지 않은 예약은 다음과 같이 응답한다.

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "담당 예약 결제·환불 상태 조회에 성공했습니다.",
  "data": {
    "reservationId": "123",
    "reservationNo": "R20260806A7K3M9Q2W5XZ",
    "contentId": "201",
    "sessionId": "456",
    "payment": {
      "paymentId": "901",
      "status": "APPROVED",
      "finalAmount": 17000,
      "currency": "KRW",
      "discrepancy": null
    },
    "refund": null,
    "updatedAt": "2026-08-06T02:31:10Z"
  }
}
```

환불이 요청된 예약은 `data.refund`에 값이 있다.

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "담당 예약 결제·환불 상태 조회에 성공했습니다.",
  "data": {
    "reservationId": "123",
    "reservationNo": "R20260806A7K3M9Q2W5XZ",
    "contentId": "201",
    "sessionId": "456",
    "payment": {
      "paymentId": "901",
      "status": "APPROVED",
      "finalAmount": 17000,
      "currency": "KRW",
      "discrepancy": null
    },
    "refund": {
      "refundId": "551",
      "status": "PROCESSING",
      "amount": 17000,
      "requestedAt": "2026-08-07T01:00:00Z",
      "completedAt": null
    },
    "updatedAt": "2026-08-07T01:00:00Z"
  }
}
```

결제 없이 확정된 무료 예약은 같은 형식으로 `data.payment`와 `data.refund`를 모두 `null`로 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.reservationId` | String | 조회 대상 예약 식별자다. |
| `data.reservationNo` | String | 시스템 전체에서 유일한 예약 번호다. |
| `data.contentId` | String | 담당 콘텐츠 식별자다. |
| `data.sessionId` | String | 결제 대상 회차 식별자다. |
| `data.payment` | Object 또는 null | 예약에 연결된 결제가 있으면 값이 있고, 결제 없이 확정된 무료 예약이면 `null`이다. |
| `data.payment.paymentId` | String | 결제 식별자다. |
| `data.payment.status` | String | 결제 상태다. `PENDING`, `APPROVED`, `DECLINED`, `CANCELLED`, `EXPIRED`, `DISCREPANT` 중 하나다. |
| `data.payment.finalAmount` | Integer | 결제 최종 금액이다. |
| `data.payment.currency` | String | 통화 코드다. |
| `data.payment.discrepancy` | Object 또는 null | 이 결제에 연결된 불일치가 있으면 값이 있고, 없으면 `null`이다. |
| `data.payment.discrepancy.discrepancyId` | String | 불일치 식별자다. |
| `data.payment.discrepancy.status` | String | 불일치 상태다. `OPEN`, `RESOLVED_NO_ISSUE`, `REFUND_REQUESTED` 중 하나다. |
| `data.refund` | Object 또는 null | 이 결제에 대해 환불이 요청된 적 있으면 값이 있고, 없으면 `null`이다. |
| `data.refund.refundId` | String | 환불 식별자다. |
| `data.refund.status` | String | 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.refund.amount` | Integer | 환불 금액이다. 결제 최종 금액과 같다. |
| `data.refund.requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |
| `data.refund.completedAt` | String 또는 null | 환불 종결 시각이다. 종결 전에는 `null`이다. UTC ISO 8601 형식이다. |
| `data.updatedAt` | String | 결제·환불·불일치 중 가장 최근 상태 변경 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `reservationId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 대상 예약이 속한 콘텐츠의 담당 운영자가 아니다. 조회 결과를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 예약이 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 예약·결제·환불·불일치 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 인증 주체는 활성·승인된 `OPERATOR`여야 한다.
2. 대상 예약이 속한 콘텐츠의 담당 운영자인지 검증하고, 아니면 `403 FORBIDDEN`으로 거부한다. 다른 운영자가 담당하는 콘텐츠의 예약은 존재 여부를 포함해 노출하지 않는다.
3. 예약에 연결된 결제가 없으면(무료 예약) `data.payment`를 `null`로 반환한다.
4. 결제당 환불은 최대 한 건이다(`refund.payment_id` 유일). 환불이 요청된 적 없으면 `data.refund`를 `null`로 반환한다.
5. `data.payment.discrepancy`는 대상 결제에 연결된 `payment_discrepancy` 중 가장 최근 건을 반환한다.
6. `updatedAt`은 결제, 환불, 불일치 중 가장 최근 상태 변경 시각을 사용한다.
7. 조회 시 예약, 결제, 환불과 불일치, 감사 이력을 생성·수정·삭제하지 않는다.
