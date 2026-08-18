# 결제 불일치 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-04` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 결제 불일치 한 건의 상세, 연결된 결제 정보, 서버 검증 이력과
지금까지의 수동 조치 이력을 조회한다. 조회는 불일치·결제 상태를 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/payment-discrepancies/{discrepancyId}` | `payment_discrepancy`, `payment`, `payment_verification`, `payment_discrepancy_action` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다.

## 3. 결제 불일치 상세 조회

### Request

```http
GET /api/v1/platform-admin/payment-discrepancies/{discrepancyId}
```

#### Request Example

```http
GET /api/v1/platform-admin/payment-discrepancies/301 HTTP/1.1
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
| `discrepancyId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 결제 불일치 식별자다. |

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
  "message": "결제 불일치 상세 조회에 성공했습니다.",
  "data": {
    "discrepancy": {
      "discrepancyId": "301",
      "discrepancyType": "AMOUNT_MISMATCH",
      "status": "OPEN",
      "detectedAt": "2026-08-06T03:05:00Z"
    },
    "payment": {
      "paymentId": "902",
      "holdId": "790",
      "orderId": "ORD-20260806-7H2P4X",
      "portonePaymentId": "portone-txn-abc123",
      "status": "DISCREPANT",
      "finalAmount": 15000,
      "currency": "KRW"
    },
    "verifications": [
      {
        "paymentVerificationId": "551",
        "reason": "CONFIRM_REQUEST",
        "externalStatus": "PAID",
        "observedAmount": 14000,
        "matched": false,
        "verifiedAt": "2026-08-06T03:05:00Z"
      }
    ],
    "actions": []
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.discrepancy.discrepancyId` | String | 불일치 식별자다. |
| `data.discrepancy.discrepancyType` | String | 불일치 유형이다. `LATE_APPROVAL`, `AMOUNT_MISMATCH`, `ORDER_MISMATCH`, `TARGET_MISMATCH` 중 하나다. |
| `data.discrepancy.status` | String | 불일치 상태다. `OPEN`, `RESOLVED_NO_ISSUE`, `REFUND_REQUESTED` 중 하나다. |
| `data.discrepancy.detectedAt` | String | 불일치 감지 시각이다. UTC ISO 8601 형식이다. |
| `data.payment.paymentId` | String | 조사 대상 결제 식별자다. |
| `data.payment.holdId` | String | 결제 대상 정원 홀드 식별자다. |
| `data.payment.orderId` | String | 서버가 발급한 내부 주문 식별자다. |
| `data.payment.portonePaymentId` | String 또는 null | PortOne V2 거래 식별자다. 결제 시작 전이면 `null`이다. |
| `data.payment.status` | String | 결제 상태다. 이 API에서는 항상 `DISCREPANT`다. |
| `data.payment.finalAmount` | Integer | 결제 최종 금액이다. |
| `data.payment.currency` | String | 통화 코드다. |
| `data.verifications` | Array | 이 결제에 대한 서버 검증 이력이다. 오래된 순으로 정렬한다. |
| `data.verifications[].paymentVerificationId` | String | 검증 이력 식별자다. |
| `data.verifications[].reason` | String | 검증을 트리거한 원인이다. `CONFIRM_REQUEST`, `WEBHOOK` 중 하나다. |
| `data.verifications[].externalStatus` | String | PortOne이 보고한 외부 결제 상태다. |
| `data.verifications[].observedAmount` | Integer | PortOne이 보고한 관측 금액이다. |
| `data.verifications[].matched` | Boolean | 내부 판정 결과다. 일치하면 `true`, 불일치가 감지됐으면 `false`다. |
| `data.verifications[].verifiedAt` | String | 검증 처리 시각이다. UTC ISO 8601 형식이다. |
| `data.actions` | Array | 이 불일치에 대해 지금까지 수행한 수동 조치 이력이다. 아직 처리하지 않았으면 빈 배열 `[]`이다. |
| `data.actions[].actionId` | String | 조치 식별자다. |
| `data.actions[].action` | String | 수행한 조치다. `NO_ISSUE_CLOSE`(이 도메인) 또는 `FULL_REFUND_REQUEST`(환불 도메인에서 기록)다. |
| `data.actions[].evidenceReference` | String | 처리 근거 증빙 참조다. |
| `data.actions[].reason` | String | 처리 사유다. |
| `data.actions[].actedAt` | String | 조치 처리 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `discrepancyId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 조회 결과를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 불일치가 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 불일치·결제·검증·조치 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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
2. `verifications`는 대상 결제에 연결된 `payment_verification` 전체를 `verified_at` 오름차순으로 반환한다.
3. `actions`는 대상 불일치에 연결된 `payment_discrepancy_action` 전체를 `acted_at` 오름차순으로 반환하며, 이 도메인에서 기록한 조치와 환불 도메인에서 기록한 전액환불 조치를 모두 포함한다.
4. 결제 원문, 웹훅 원문과 비밀값은 응답에 포함하지 않는다.
5. 조회 시 불일치, 결제, 검증 이력과 조치 이력을 생성·수정·삭제하지 않는다.
