# PortOne 결제 웹훅 수신 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-04` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

PortOne이 결제 이벤트를 서버 간 호출로 통지하면 서명을 검증한 뒤 [서버 결제 승인 확인](confirm-payment.md)과
같은 검증·상태 전이 로직으로 처리한다. 이 API는 방문자를 인증하지 않으며 PortOne의 웹훅 서명으로만
요청을 신뢰한다.

> **구현 전 잔여 확정 항목:** PortOne V2 웹훅의 정확한 서명 헤더 이름, 페이로드 스키마와 재전송 정책은
> [유료 결제·환불](../../../p1/payment-refund.md#4-구현-전-잔여-확정-항목)의 PortOne 세부 연동 결정에 따라
> 확정한다. 아래 `{중괄호}` 항목은 공급자 계약이 확정되면 실제 값으로 교체한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-04 | `POST /api/v1/webhooks/portone` | `payment_webhook`, `payment`, `payment_verification` |

## 2. 공통 계약 참조

응답·오류 구조는 [결제 API](payment.md#2-공통-계약-참조)를 따르되, 인증은 `Authorization` Bearer 대신
공급자 서명 검증을 사용한다.

## 3. PortOne 결제 웹훅 수신

### Request

```http
POST /api/v1/webhooks/portone
```

#### Request Example

```http
POST /api/v1/webhooks/portone HTTP/1.1
{PortOne 웹훅 서명 헤더}: {서명 값}
Content-Type: application/json; charset=UTF-8

{
  "eventId": "{PortOne 이벤트 식별자}",
  "type": "{Transaction.Paid 등 이벤트 유형}",
  "paymentId": "ORD-20260806-9F3K7Q",
  "timestamp": "2026-08-06T02:31:05Z"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `{PortOne 웹훅 서명 헤더}` | Y | PortOne이 서명한 값이다. 정확한 헤더 이름과 서명 알고리즘은 공급자 연동 확정 시 채운다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "eventId": "{PortOne 이벤트 식별자}",
  "type": "{이벤트 유형}",
  "paymentId": "ORD-20260806-9F3K7Q",
  "timestamp": "2026-08-06T02:31:05Z"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `eventId` | String | Y | PortOne이 부여한 이벤트 식별자다. `payment_webhook.provider_event_id`의 유일성 기준이다. |
| `type` | String | Y | PortOne 이벤트 유형이다. 정확한 값 목록은 공급자 연동 확정 시 채운다. |
| `paymentId` | String | Y | 결제 생성 시 서버가 발급한 `order_id`와 같은 값이다. 서버는 이 값으로 대상 결제를 조회한다. |
| `timestamp` | String | Y | PortOne이 이벤트를 발생시킨 시각이다. |

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
  "message": "웹훅 수신에 성공했습니다.",
  "data": null
}
```

서명이 유효하면 결제 매칭 여부나 내부 처리 결과와 무관하게 `200 OK`를 반환해 PortOne의 불필요한 재전송을
막는다. 매칭되는 결제를 찾지 못해도 수신·인증 결과는 `payment_webhook`에 남긴다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data` | null | 항상 `null`이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 필수 필드가 없다. `payment_webhook`을 생성하지 않는다. |
| `401` | `WEBHOOK_SIGNATURE_INVALID` | 서명 헤더가 없거나 검증에 실패했다. `payment_webhook`을 생성하지 않고 어떤 도메인 상태도 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 `payment_webhook`과 결제 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "WEBHOOK_SIGNATURE_INVALID",
  "message": "웹훅 서명 검증에 실패했습니다.",
  "data": null
}
```

### 처리 규칙

1. 서명 검증에 실패하면 `payment_webhook`을 생성하지 않고 결제·예약 상태를 변경하지 않는다.
2. `eventId`(`provider_event_id`)가 이미 처리된 값이면 새로운 도메인 처리를 실행하지 않고 `200 OK`를 반환한다(`UNIQUE (provider_event_id)`).
3. `paymentId`(`order_id`)로 대상 결제를 찾지 못하면 `payment_webhook.payment_id = null`로 수신·인증 결과를 기록하고 `200 OK`를 반환한다.
4. 대상 결제를 찾으면 [서버 결제 승인 확인](confirm-payment.md#3-서버-결제-승인-확인)의 처리 규칙과 같은 검증·상태 전이 로직을 재사용한다. 이 API 호출과 클라이언트의 확인 호출이 동시에 도착해도 결제 행 잠금 또는 조건부 전이로 하나의 처리만 상태를 변경한다.
5. 웹훅 원문, 서명 원문과 비밀값은 저장하지 않는다. `payment_webhook`에는 정규화한 필드, 인증 결과, 처리 결과와 원문 해시만 남긴다.
6. 결제 종결과 무관하게 서명이 유효한 요청은 항상 `200 OK`로 응답한다. 검증 로직 내부에서 발생한 불일치는 `DISCREPANT` 전이와 `payment_discrepancy` 생성으로 처리하며 이 API의 HTTP 응답 자체를 오류로 바꾸지 않는다.
7. 웹훅 수신과 처리 결과는 `payment_webhook`에 남기며, 이어서 발생하는 결제·홀드·예약·불일치 상태 전이의 감사 이력은 [서버 결제 승인 확인](confirm-payment.md#3-서버-결제-승인-확인)의 처리 규칙과 동일한 규칙을 따른다.
8. 서명 검증 실패는 감사 대상 상태 변경이 없으므로 `audit_event`를 생성하지 않고 구조화 로그로만 관찰한다.
