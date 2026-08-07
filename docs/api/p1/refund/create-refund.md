# 수동 환불 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-05`, `ADM-04` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [결제 API](../payment/payment.md), [유료 결제·환불](../../../p1/payment-refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 승인된 결제(`APPROVED` 또는 `DISCREPANT`)에 대해 전액 환불을
시작한다. 방문자의 취소·환불 요청(예약 도메인)이나 결제 불일치 조사 결과 전액환불이 필요하다고 판단된
경우 모두 이 API로 수렴한다. 부분 환불은 지원하지 않으며, 결제당 환불은 최대 한 건이다.

이 API는 영속 자원 유일 제약(`refund.payment_id` 유일)으로 자연 멱등하다. 대상 결제에 이미 환불이 있으면
새로 만들지 않고 기존 환불 상태를 그대로 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, P1-FR-10, PAY-05, ADM-04 | `POST /api/v1/platform-admin/payments/{paymentId}/refund` | `payment`, `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history`, `payment_discrepancy`, `payment_discrepancy_action` |

## 2. 공통 계약 참조

생성·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다.

## 3. 수동 환불

### Request

```http
POST /api/v1/platform-admin/payments/{paymentId}/refund
```

#### Request Example

```http
POST /api/v1/platform-admin/payments/902/refund HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "evidenceReference": "방문자 고객센터 환불 요청 #7781",
  "reason": "회차 시작 전 방문자 취소 요청에 따른 전액 환불"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이어야 한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `paymentId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 환불 대상 결제 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "evidenceReference": "방문자 고객센터 환불 요청 #7781",
  "reason": "회차 시작 전 방문자 취소 요청에 따른 전액 환불"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `evidenceReference` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 처리 근거 증빙 참조다. 빈 문자열 또는 공백만으로 된 값은 허용하지 않는다. |
| `reason` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 처리 사유다. 빈 문자열 또는 공백만으로 된 값은 허용하지 않으며 성공 감사 이력에 기록한다. |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "수동 환불에 성공했습니다.",
  "data": {
    "refundId": "551",
    "paymentId": "902",
    "amount": 15000,
    "currency": "KRW",
    "status": "PROCESSING",
    "requestedAt": "2026-08-07T01:00:00Z"
  }
}
```

대상 결제에 이미 환불이 있으면 새로 만들지 않고 기존 환불의 현재 상태를 같은 형식으로 `201 Created`에
담아 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.refundId` | String | 생성되었거나 기존에 있던 환불 식별자다. |
| `data.paymentId` | String | 환불 대상 결제 식별자다. |
| `data.amount` | Integer | 환불 금액이다. 결제 최종 금액 전체다. |
| `data.currency` | String | 통화 코드다. |
| `data.status` | String | 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `paymentId`를 양의 정수 식별자로 처리할 수 없다. 환불·시도·감사 이력을 생성하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `evidenceReference` 또는 `reason`이 누락·공백·500자 초과다. 환불·시도·감사 이력을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 환불·시도·감사 이력을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 환불·시도·감사 이력을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 환불·시도·감사 이력을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 대상 결제가 없다. 환불·시도·감사 이력을 생성하지 않는다. |
| `409` | `REFUND_PAYMENT_CONFLICT` | 대상 결제가 `APPROVED` 또는 `DISCREPANT`가 아니다(`PENDING`·`DECLINED`·`CANCELLED`·`EXPIRED`는 환불 대상이 아니다). 환불을 생성하지 않으며 동일 상태에서 재시도해도 성공하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | PortOne 호출 실패를 포함해 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 환불·시도·감사 이력을 생성하지 않으며 일시적 장애라면 동일한 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REFUND_PAYMENT_CONFLICT",
  "message": "환불을 생성할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다.
2. 대상 결제는 `APPROVED` 또는 `DISCREPANT`여야 한다. 그 외 상태면 `409 REFUND_PAYMENT_CONFLICT`로 거부한다.
3. 대상 결제에 이미 `refund`가 있으면(`UNIQUE (payment_id)`) 새로 만들지 않고 기존 환불 상태를 그대로 반환해, 같은 요청의 재시도가 새 환불 효과를 만들지 않게 한다.
4. 환불 금액은 대상 결제의 최종 금액 전체다. 부분 환불은 지원하지 않는다.
5. 최초 요청이면 `refund(REQUESTED)`를 생성한 뒤 즉시 `PROCESSING`으로 전이하고, 외부 호출 직전에 `refund_attempt(PENDING, attempt_no=1)`를 기록해 시도 번호를 점유한다. PortOne 환불 호출의 최대 응답 대기 시간은 30초다.
6. 응답을 받으면 외부 상태와 응답 원문 해시를 저장해 `RESPONDED`로 확정한다. 성공이면 `refund`를 `SUCCEEDED`로 전이하고 `completed_at`을 기록하며, 명시적 실패면 `FAILED`로 전이한다.
7. 타임아웃·연결 단절·네트워크 실패처럼 응답을 받지 못하면 응답 값은 저장하지 않고 비밀값 없는 실패 사유와 함께 `NO_RESPONSE`로 확정하며, `refund`를 `DISCREPANT`로 전이한다. 이 시도도 총 3회에 포함된다.
8. 6번에서 `refund`가 최초 `SUCCEEDED`로 전이하면 [환불 공통 쿠폰 복구 계약](refund.md#쿠폰-복구-계약)을 같은 상태 반영 트랜잭션에 적용한다. `FAILED` 또는 `DISCREPANT`로 전이하면 쿠폰을 복구하지 않는다.
9. 대상 결제에 연결된 `OPEN` 상태 `payment_discrepancy`가 있으면, 같은 트랜잭션에서 `payment_discrepancy.status`를 `REFUND_REQUESTED`로 전이하고 `action = FULL_REFUND_REQUEST`, `evidenceReference`, `reason`을 포함한 `payment_discrepancy_action`을 생성한다.
10. 이 API는 결제를 승인 처리하거나 취소된 예약을 강제로 확정하지 않으며, 예약 상태 자체는 예약 도메인의 취소 API가 별도로 관리한다.
11. 이중 승인은 적용하지 않는다. 처리자 1명의 요청만으로 환불 시작이 확정된다.
12. `evidenceReference`, `reason`은 앞뒤 공백 제거 뒤 1~500자여야 하며 빈 문자열 또는 공백만으로 된 값은 허용하지 않는다.
13. 환불 생성과 서버가 부여한 `requestId`를 포함한 `REFUND` 감사 이력을 처리하고, 쿠폰을 복구한 경우 같은 `requestId`의 `COUPON` 감사 이력을 함께 기록한다.
14. 결제 비밀값, PortOne 원문과 전체 결제수단 정보는 `evidenceReference`나 감사 이력에 기록하지 않는다.
