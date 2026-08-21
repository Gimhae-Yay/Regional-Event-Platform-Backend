# 환불 실패 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-04` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 환불 한 건의 상세, 연결된 결제 정보와 지금까지의 외부 호출
시도 이력을 조회한다. 조회는 환불·결제 상태를 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/refund-failures/{refundId}` | `refund`, `refund_attempt`, `payment` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다.

## 3. 환불 실패 상세 조회

### Request

```http
GET /api/v1/platform-admin/refund-failures/{refundId}
```

#### Request Example

```http
GET /api/v1/platform-admin/refund-failures/553 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이어야 한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `refundId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 환불 식별자다. |

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
  "message": "환불 실패 상세 조회에 성공했습니다.",
  "data": {
    "refund": {
      "refundId": "553",
      "paymentId": "904",
      "reservationId": null,
      "amount": 12000,
      "currency": "KRW",
      "status": "DISCREPANT",
      "requestedAt": "2026-08-07T01:15:00Z",
      "completedAt": null
    },
    "payment": {
      "paymentId": "904",
      "orderId": "ORD-20260807-3K9P1M",
      "portonePaymentId": "portone-txn-def456",
      "finalAmount": 12000,
      "currency": "KRW"
    },
    "attempts": [
      {
        "refundAttemptId": "701",
        "attemptNo": 1,
        "initiatorKind": "SYSTEM",
        "portoneCancellationId": null,
        "outcomeKind": "NO_RESPONSE",
        "failureReasonCode": "TIMEOUT",
        "externalStatus": null,
        "attemptedAt": "2026-08-07T01:15:31Z"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.refund.refundId` | String | 환불 식별자다. |
| `data.refund.paymentId` | String | 환불 대상 결제 식별자다. |
| `data.refund.reservationId` | String 또는 null | 환불 대상 결제와 연결된 예약 식별자다. 확정 예약 없이 생성된 결제 불일치 환불이면 `null`이다. |
| `data.refund.amount` | Integer | 환불 금액이다. |
| `data.refund.currency` | String | 통화 코드다. |
| `data.refund.status` | String | 환불 상태다. 이 API에서는 보통 `FAILED` 또는 `DISCREPANT`다. |
| `data.refund.requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |
| `data.refund.completedAt` | String 또는 null | 환불 종결 시각이다. 종결 전에는 `null`이다. |
| `data.payment.paymentId` | String | 환불 대상 결제 식별자다. |
| `data.payment.orderId` | String | 서버가 발급한 내부 주문 식별자다. |
| `data.payment.portonePaymentId` | String 또는 null | PortOne V2 거래 식별자다. |
| `data.payment.finalAmount` | Integer | 결제 최종 금액이다. |
| `data.payment.currency` | String | 통화 코드다. |
| `data.attempts` | Array | 이 환불에 대한 외부 호출 시도 이력이다. 오래된 순으로 정렬한다. |
| `data.attempts[].refundAttemptId` | String | 시도 식별자다. |
| `data.attempts[].attemptNo` | Integer | 시도 순번이다. 1~3 범위다. |
| `data.attempts[].initiatorKind` | String | 시작 주체다. `SYSTEM`, `SUPER_ADMIN`, `PLATFORM_ADMIN` 중 하나다. |
| `data.attempts[].portoneCancellationId` | String 또는 null | PortOne 취소 ID다. 외부 호출 전이거나 응답을 받지 못했으면 `null`이다. |
| `data.attempts[].outcomeKind` | String | 호출 결과다. `PENDING`, `RESPONDED`, `NO_RESPONSE` 중 하나다. |
| `data.attempts[].failureReasonCode` | String 또는 null | 응답 미수신 사유다. `TIMEOUT`, `CONNECTION`, `NETWORK`, `PROCESS_INTERRUPTED`, `UNKNOWN` 중 하나이며, `NO_RESPONSE`일 때만 값이 있다. |
| `data.attempts[].externalStatus` | String 또는 null | PortOne이 보고한 외부 환불 상태다. `RESPONDED`일 때만 값이 있다. |
| `data.attempts[].attemptedAt` | String | 외부 환불 시도 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `refundId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 조회 결과를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 환불이 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 환불·결제·시도 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다.
2. `attempts`는 대상 환불에 연결된 `refund_attempt` 전체를 `attempt_no` 오름차순으로 반환한다.
3. 확정 예약 없이 생성된 결제 불일치 환불은 `reservationId: null`로 반환한다. 예약이 연결된 환불은 예약 식별자를 문자열로 반환하며, 예약 연결이 없다는 이유만으로 정합성 오류로 처리하지 않는다.
4. PortOne 원문, 응답 원문 해시와 비밀값은 응답에 포함하지 않는다.
5. 조회 시 환불, 결제와 시도 이력을 생성·수정·삭제하지 않는다.
