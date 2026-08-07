# PortOne 결제 웹훅 수신 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-02`, `PAY-03`, `PAY-04` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [P1 ERD](../../../p1-erd.md), [ADR-0069](../../../adr/0069-use-p0-capacity-hold-and-reservation-price-snapshot-for-paid-checkout.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

PortOne이 결제 이벤트를 서버 간 호출로 통지하면 서명을 검증한 뒤 서버가 PortOne V2에서 해당 거래를
재조회해 외부 거래 식별자·금액·통화·주문 식별자가 대상 홀드·가격 스냅샷과 일치하는지 검증한다. 검증에
성공하면 같은 트랜잭션에서 홀드 소비, 예약 확정과 결제 승인을 함께 처리한다.

이 API는 결제 승인·확정의 유일한 진입점이다. 클라이언트는 결제 승인을 직접 트리거하지 않으며, [내 결제
상태 조회](get-my-payment.md)로 처리 결과를 확인한다. 이 API는 별도 `Idempotency-Key`를 받지 않는다.
`payment.status`와 `payment_webhook.provider_event_id` 유일성으로 재전송에 자연 멱등하다.

> **구현 전 잔여 확정 항목:** PortOne V2 웹훅의 정확한 서명 헤더 이름, 페이로드 스키마와 재전송 정책은
> [유료 결제·환불](../../../p1/payment-refund.md#4-구현-전-잔여-확정-항목)의 PortOne 세부 연동 결정에 따라
> 확정한다. 아래 `{중괄호}` 항목은 공급자 계약이 확정되면 실제 값으로 교체한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-02, PAY-03, PAY-04 | `POST /api/v1/webhooks/portone` | `payment_webhook`, `payment`, `payment_verification`, `capacity_hold`, `reservation`, `coupon`, `coupon_status_history`, `coupon_redemption`, `payment_discrepancy` |

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

서명이 유효하고 PortOne 조회가 필요 없거나 조회에 성공적으로 응답을 받았으면, 결제 매칭 여부·검증 결과나
내부 처리 결과와 무관하게 `200 OK`를 반환해 PortOne의 불필요한 재전송을 막는다. 매칭되는 결제를 찾지
못해도 수신·인증 결과는 `payment_webhook`에 남긴다. 단, `PENDING` 결제의 PortOne 조회 자체가
타임아웃·연결 실패·5xx로 끝나면 `500 INTERNAL_SERVER_ERROR`로 응답해 PortOne의 재전송을 유도한다
(처리 규칙 5, 13번 참고).

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
| `500` | `INTERNAL_SERVER_ERROR` | `PENDING` 또는 홀드 종결로 `EXPIRED`가 된 결제의 PortOne 조회 자체가 타임아웃·연결 실패·5xx로 끝났거나 예상하지 못한 서버 오류가 발생했다. `payment_webhook`·`payment_verification`을 남기지 않고 결제·예약 상태를 변경하지 않으며, PortOne이 이 응답을 받으면 웹훅을 재전송하므로 이후 조회를 다시 시도할 수 있다. |

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
4. 대상 결제를 찾았고 이미 종결 상태가 `APPROVED`, `DECLINED`, `CANCELLED`, `DISCREPANT`이면 PortOne을 다시 조회하지 않고 저장된 상태를 유지한다. 홀드·예약·쿠폰을 다시 변경하지 않는다. 홀드 종결 작업이 만든 `EXPIRED`는 늦은 외부 성공 확인 대상이므로 이 조기 반환에 포함하지 않는다.
5. 대상 결제가 `PENDING` 또는 홀드 종결로 `EXPIRED`가 된 상태면 데이터베이스 행 잠금을 획득하기 전에 `payment.order_id`로 PortOne V2 거래를 조회한다. 조회 자체가 타임아웃·연결 실패·5xx로 끝나면 `payment_webhook`·`payment_verification`을 남기지 않고 `500 INTERNAL_SERVER_ERROR`로 응답해 PortOne의 재전송을 유도한다.
6. 조회에 성공하면 외부 거래 식별자, 금액, 통화, 주문 식별자와 홀드·가격 스냅샷 대상의 일치 여부를 검증해 외부 응답 요약과 판정 결과를 준비한다. 준비한 결과는 14번의 상태 반영 트랜잭션에서 `payment_verification`에 기록하며, 하나라도 다르면 성공 상태로 전이하지 않는다.
7. 외부 조회 뒤 상태 반영 트랜잭션의 도메인 행 잠금과 조건부 전이는 `content → content_session → capacity_hold → reservation_price_snapshot → payment → coupon` 순서를 따른다. 잠금 획득 뒤 콘텐츠·회차·홀드·스냅샷 연결과 결제·쿠폰 상태를 다시 검증하며, 쿠폰을 적용하지 않은 경우 마지막 단계를 건너뛴다.
8. 결제 행을 잠근 시점에 `APPROVED`, `DECLINED`, `CANCELLED`, `DISCREPANT`이면 새 도메인 처리를 실행하지 않고 저장된 결과를 따른다. `PENDING` 또는 홀드 종결로 `EXPIRED`가 된 상태면 조회 결과에 따른 전이를 계속한다.
9. PortOne이 아직 완료되지 않은 거래를 보고하면 기존 결제가 `PENDING`일 때만 `PENDING`과 쿠폰 선점을 유지한다. 기존 결제가 `EXPIRED`이면 결제 종결과 이미 수행한 쿠폰 선점 해제를 유지한다.
10. PortOne이 명시적 거절을 보고하면 기존 결제가 `PENDING`일 때 예약과 홀드를 변경하지 않고 `payment.status`를 `DECLINED`로 전이하며, 스냅샷에 `RESERVED` 쿠폰이 있으면 원래 만료 시각 전에는 `AVAILABLE`, 지났으면 `EXPIRED`로 전이하고 상태 이력을 기록한다. 기존 결제가 `EXPIRED`이면 결제와 쿠폰의 종결 결과를 유지한다.
11. 검증에 성공하면 같은 트랜잭션에서 홀드를 `ACTIVE → CONSUMED`로 전이하고 `CONFIRMED` 예약을 생성하며 `payment.status`를 `APPROVED`로 전이한다. 스냅샷에 적용 쿠폰이 있으면 같은 스냅샷에 선점된 `RESERVED`인지 확인한 뒤 `RESERVED → USED`, 상태 이력과 `coupon_redemption(CONFIRMED)`을 함께 기록한다.
12. 늦은 승인(홀드가 이미 만료·소비·무효화됐거나 결제가 홀드 종결로 `EXPIRED`가 된 뒤의 외부 성공)이거나 금액·주문 식별자·대상이 일치하지 않으면 예약을 되살리거나 강제로 확정하지 않고 `payment.status`를 `DISCREPANT`로 전이한 뒤 `discrepancy_type`(`LATE_APPROVAL`, `AMOUNT_MISMATCH`, `ORDER_MISMATCH`, `TARGET_MISMATCH` 중 해당하는 값)과 함께 `payment_discrepancy`를 생성한다. 확정 예약이 없고 스냅샷의 쿠폰이 아직 `RESERVED`이면 만료 여부에 따라 `AVAILABLE` 또는 `EXPIRED`로 선점을 해제한다. 홀드 종결에서 이미 선점을 해제했다면 새 쿠폰 전이를 만들지 않는다.
13. 같은 결제에 대한 웹훅 이벤트가 재전송되거나 순서가 뒤바뀌어 동시에 도착해도 잠금과 조건부 전이로 하나의 처리만 결제·홀드·예약·쿠폰 상태를 변경하고 나머지는 그 결과를 그대로 따른다.
14. 결제 상태 전이, 홀드 소비, 예약 생성, 쿠폰 상태와 사용 이력 또는 선점 해제, 웹훅·검증·불일치 기록은 해당 결과별 하나의 MySQL 트랜잭션에서 커밋한다.
15. 외부 서비스 지연·실패가 발생해도 PortOne 응답을 기다리는 동안 데이터베이스 잠금을 유지하지 않는다.
16. 서명이 유효하고 PortOne 조회가 필요 없거나 조회에 성공했다면 결제 종결·검증 결과와 무관하게 항상 `200 OK`로 응답한다. 검증 로직 내부에서 발생한 불일치는 `DISCREPANT` 전이와 `payment_discrepancy` 생성으로 처리하며 이 API의 HTTP 응답 자체를 오류로 바꾸지 않는다. 5번의 PortOne 조회 자체 실패만 예외로 `500`을 반환한다.
17. 검증 성공에 따른 홀드 소비·예약 생성은 P0와 동일하게 `CAPACITY_HOLD`, `RESERVATION` 감사 이벤트 두 건을 같은 `requestId`로 기록하고, 결제 승인과 쿠폰 상태 전이는 각각 `PAYMENT`, `COUPON` 감사 이벤트로 함께 기록한다.
18. `DECLINED`·`DISCREPANT` 전이와 쿠폰 선점 해제도 같은 `requestId`로 감사한다. `DISCREPANT`는 원본 외부 응답 해시와 내부 판정 결과를 `payment_verification`에 남기고 `payment_discrepancy`를 생성하며 예약·홀드 상태를 임의로 확정하지 않는다.
19. 웹훅 원문, 서명 원문, PortOne 원문과 비밀값과 전체 결제수단 정보는 저장하지 않는다. `payment_webhook`에는 정규화한 필드, 인증 결과, 처리 결과와 원문 해시만 남긴다.
20. 서명 검증 실패는 감사 대상 상태 변경이 없으므로 `audit_event`를 생성하지 않고 구조화 로그로만 관찰한다.
