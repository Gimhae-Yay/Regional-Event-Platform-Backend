# 담당 예약 결제 상태 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-06` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 본인 담당 콘텐츠의 유료 예약 결제 현황을 조회해 고객 문의 대응에 사용한다. 이
API는 결제·불일치 상태를 변경하지 않으며, 무료 예약처럼 결제가 없는 예약은 결과에 포함하지 않는다.
환불 상태는 이 API의 범위가 아니며 환불 도메인의 담당 예약 환불 상태 조회 API를 사용한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-06 | `GET /api/v1/operator/payment-reconciliations` | `payment`, `reservation`, `content_session`, `payment_discrepancy` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지네이션을 적용하지 않는다.

## 3. 담당 예약 결제 상태 조회

### Request

```http
GET /api/v1/operator/payment-reconciliations
```

#### Request Example

```http
GET /api/v1/operator/payment-reconciliations?contentId=201 HTTP/1.1
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

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | N | 특정 담당 콘텐츠로 결과를 좁힌다. 제공하지 않으면 담당 콘텐츠 전체를 대상으로 한다. 제공하면 양의 10진 문자열·signed 64비트 `Long` 범위를 만족해야 한다. |

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
  "message": "담당 예약 결제 상태 조회에 성공했습니다.",
  "data": {
    "reconciliations": [
      {
        "reservationId": "123",
        "reservationNo": "R20260806A7K3M9Q2W5XZ",
        "contentId": "201",
        "sessionId": "456",
        "paymentId": "901",
        "paymentStatus": "APPROVED",
        "finalAmount": 17000,
        "currency": "KRW",
        "discrepancy": null,
        "updatedAt": "2026-08-06T02:31:10Z"
      },
      {
        "reservationId": null,
        "reservationNo": null,
        "contentId": "201",
        "sessionId": "457",
        "paymentId": "902",
        "paymentStatus": "DISCREPANT",
        "finalAmount": 15000,
        "currency": "KRW",
        "discrepancy": {
          "discrepancyId": "301",
          "status": "OPEN"
        },
        "updatedAt": "2026-08-06T03:05:00Z"
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.reconciliations: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.reconciliations` | Array | 담당 콘텐츠에 속한 결제 시도 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.reconciliations[].reservationId` | String 또는 null | 결제가 예약 확정까지 이어졌으면 예약 식별자이고, `DISCREPANT`처럼 예약이 생성되지 않았으면 `null`이다. |
| `data.reconciliations[].reservationNo` | String 또는 null | `reservationId`가 있을 때만 값이 있다. |
| `data.reconciliations[].contentId` | String | 담당 콘텐츠 식별자다. |
| `data.reconciliations[].sessionId` | String | 결제 대상 회차 식별자다. |
| `data.reconciliations[].paymentId` | String | 결제 식별자다. |
| `data.reconciliations[].paymentStatus` | String | 결제 상태다. `PENDING`, `APPROVED`, `DECLINED`, `CANCELLED`, `EXPIRED`, `DISCREPANT` 중 하나다. |
| `data.reconciliations[].finalAmount` | Integer | 결제 최종 금액이다. |
| `data.reconciliations[].currency` | String | 통화 코드다. |
| `data.reconciliations[].discrepancy` | Object 또는 null | 이 결제에 연결된 불일치가 있으면 값이 있고, 없으면 `null`이다. |
| `data.reconciliations[].discrepancy.discrepancyId` | String | 불일치 식별자다. |
| `data.reconciliations[].discrepancy.status` | String | 불일치 상태다. `OPEN`, `RESOLVED_NO_ISSUE`, `REFUND_REQUESTED` 중 하나다. |
| `data.reconciliations[].updatedAt` | String | 결제 또는 연결된 불일치의 가장 최근 상태 변경 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `contentId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `contentId`가 범위를 벗어난다. 조회 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 요청한 `contentId`의 담당 운영자가 아니다. 조회 결과를 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 결제·예약·불일치 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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
2. `contentId`를 제공하면 해당 콘텐츠의 소유 운영자인지 검증하고, 소유가 아니면 `403 FORBIDDEN`으로 거부한다.
3. `contentId`를 제공하지 않으면 인증 주체가 소유한 모든 콘텐츠의 회차를 대상으로 한다. 다른 운영자의 콘텐츠는 조회 대상에서 완전히 제외한다.
4. 결제가 없는 무료 예약은 결과에 포함하지 않는다.
5. `updatedAt` 내림차순, 같은 시각이면 `paymentId` 내림차순으로 정렬해 최근 변경 건을 먼저 표시한다.
6. 이 API는 단순 목록이다. 페이지·커서와 사용자 지정 정렬을 제공하지 않는다.
7. 조회 시 결제, 예약, 불일치와 감사 이력을 생성·수정·삭제하지 않는다.
