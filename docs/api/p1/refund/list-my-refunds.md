# 내 환불 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-06` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자가 본인 결제에 연결된 환불 목록을 조회한다. 조회는 환불·결제 상태를 변경하지 않는다. 환불은
결제당 최대 한 건이며, 확정 예약의 취소·환불과 예약 없이 생성된 결제 불일치 환불을 함께 포함할 수 있다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-06 | `GET /api/v1/me/refunds` | `refund` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지네이션을 적용하지 않는다.

## 3. 내 환불 목록 조회

### Request

```http
GET /api/v1/me/refunds
```

#### Request Example

```http
GET /api/v1/me/refunds HTTP/1.1
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

없음.

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
  "message": "내 환불 목록 조회에 성공했습니다.",
  "data": {
    "refunds": [
      {
        "refundId": "553",
        "paymentId": "904",
        "reservationId": null,
        "amount": 12000,
        "currency": "KRW",
        "status": "DISCREPANT",
        "requestedAt": "2026-08-07T01:15:00Z",
        "completedAt": null
      },
      {
        "refundId": "551",
        "paymentId": "902",
        "reservationId": "123",
        "amount": 17000,
        "currency": "KRW",
        "status": "SUCCEEDED",
        "requestedAt": "2026-08-07T01:00:00Z",
        "completedAt": "2026-08-07T01:00:12Z"
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.refunds: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.refunds` | Array | 인증 주체 소유 환불 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.refunds[].refundId` | String | 환불 식별자다. |
| `data.refunds[].paymentId` | String | 환불 대상 결제 식별자다. |
| `data.refunds[].reservationId` | String 또는 null | 환불 대상 결제와 연결된 예약 식별자다. 확정 예약 없이 생성된 결제 불일치 환불이면 `null`이다. |
| `data.refunds[].amount` | Integer | 환불 금액이다. 결제 최종 금액과 같다. |
| `data.refunds[].currency` | String | 통화 코드다. |
| `data.refunds[].status` | String | 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.refunds[].requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |
| `data.refunds[].completedAt` | String 또는 null | 환불 종결 시각이다. 종결 전에는 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 환불·결제 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증이 필요합니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 회원이어야 한다.
2. 목록은 인증 주체가 소유한 결제에 연결된 `refund`만 포함한다. 다른 회원의 환불은 노출하지 않는다.
3. 본인 소유 결제의 확정 예약 유무는 조회 포함 조건이 아니다. 확정 예약 없이 생성된 결제 불일치 환불은 `reservationId: null`로, 예약이 연결된 환불은 예약 식별자를 문자열로 반환한다.
4. `requestedAt` 내림차순, 같은 시각이면 `refundId` 내림차순으로 정렬해 최근 요청을 먼저 표시한다.
5. 내부 시도 이력(`refund_attempt`)은 노출하지 않는다.
6. 이 API는 단순 목록이다. 페이지·커서와 사용자 지정 정렬을 제공하지 않는다.
7. 조회 시 환불, 결제와 감사 이력을 생성·수정·삭제하지 않는다.
