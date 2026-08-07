# 내 환불 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-06` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자가 본인 환불 한 건의 상세 상태를 조회한다. 조회는 환불·결제 상태를 변경하지 않으며, 외부 호출
시도 횟수·실패 사유 같은 내부 이력은 노출하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-06 | `GET /api/v1/me/refunds/{refundId}` | `refund` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다.

## 3. 내 환불 상세 조회

### Request

```http
GET /api/v1/me/refunds/{refundId}
```

#### Request Example

```http
GET /api/v1/me/refunds/551 HTTP/1.1
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
  "message": "내 환불 상세 조회에 성공했습니다.",
  "data": {
    "refundId": "551",
    "paymentId": "902",
    "reservationId": "123",
    "amount": 17000,
    "currency": "KRW",
    "status": "PROCESSING",
    "requestedAt": "2026-08-07T01:00:00Z",
    "completedAt": null
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.refundId` | String | 환불 식별자다. |
| `data.paymentId` | String | 환불 대상 결제 식별자다. |
| `data.reservationId` | String | 환불 대상 결제와 연결된 예약 식별자다. |
| `data.amount` | Integer | 환불 금액이다. 결제 최종 금액과 같다. |
| `data.currency` | String | 통화 코드다. |
| `data.status` | String | 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |
| `data.completedAt` | String 또는 null | 환불 종결 시각이다. 종결 전에는 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `refundId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 대상 환불이 연결된 결제의 소유자가 아니다. 조회 결과를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 환불이 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 환불·결제 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 서버는 인증 주체가 대상 환불이 연결된 결제가 속한 홀드의 `user_id`와 일치하는지 검증한다. 다른 회원의 환불은 존재 여부를 포함해 노출하지 않는다.
2. 내부 시도 이력(`refund_attempt`)과 실패 사유는 노출하지 않는다. 방문자에게는 `refund` 상태와 금액·시각만 제공한다.
3. 조회 시 환불, 결제와 감사 이력을 생성·수정·삭제하지 않는다.
