# 결제 불일치 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-04` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 수동 조사가 필요한 결제 불일치 목록을 조회한다. 기본값은
아직 처리하지 않은(`OPEN`) 불일치이며, 조회는 불일치·결제 상태를 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/payment-discrepancies` | `payment_discrepancy`, `payment` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지네이션을 적용하지 않는다.

## 3. 결제 불일치 목록 조회

### Request

```http
GET /api/v1/platform-admin/payment-discrepancies
```

#### Request Example

```http
GET /api/v1/platform-admin/payment-discrepancies?status=OPEN HTTP/1.1
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

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | N | 조회할 불일치 상태다. `OPEN`, `RESOLVED_NO_ISSUE`, `REFUND_REQUESTED` 중 하나이며, 제공하지 않으면 `OPEN`으로 조회한다. |

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
  "message": "결제 불일치 목록 조회에 성공했습니다.",
  "data": {
    "discrepancies": [
      {
        "discrepancyId": "301",
        "paymentId": "902",
        "discrepancyType": "AMOUNT_MISMATCH",
        "status": "OPEN",
        "finalAmount": 15000,
        "currency": "KRW",
        "detectedAt": "2026-08-06T03:05:00Z"
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.discrepancies: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.discrepancies` | Array | 조회 조건을 만족하는 불일치 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.discrepancies[].discrepancyId` | String | 불일치 식별자다. |
| `data.discrepancies[].paymentId` | String | 조사 대상 결제 식별자다. |
| `data.discrepancies[].discrepancyType` | String | 불일치 유형이다. `LATE_APPROVAL`, `AMOUNT_MISMATCH`, `ORDER_MISMATCH`, `TARGET_MISMATCH` 중 하나다. |
| `data.discrepancies[].status` | String | 불일치 상태다. `OPEN`, `RESOLVED_NO_ISSUE`, `REFUND_REQUESTED` 중 하나다. |
| `data.discrepancies[].finalAmount` | Integer | 대상 결제의 최종 금액이다. |
| `data.discrepancies[].currency` | String | 통화 코드다. |
| `data.discrepancies[].detectedAt` | String | 불일치 감지 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 정의된 값 중 하나가 아니다. 조회 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 조회 결과를 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 불일치·결제 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다. 지역 관리자와 콘텐츠 운영자는 이 API를 호출할 수 없다.
2. `status`를 생략하면 `OPEN`으로 조회한다. `OPEN`, `RESOLVED_NO_ISSUE`, `REFUND_REQUESTED` 외의 값은 빈 목록으로 대체하지 않고 `400 INVALID_INPUT`으로 거부한다.
3. 목록은 `detectedAt` 오름차순, 같은 시각이면 `discrepancyId` 오름차순으로 정렬해 오래 대기한 건을 먼저 표시한다.
4. `finalAmount`, `currency`는 대상 결제에 연결된 `reservation_price_snapshot`에서 조회한다.
5. 이 API는 단순 목록이다. 페이지·커서와 사용자 지정 정렬을 제공하지 않는다.
6. 조회 시 불일치, 결제와 감사 이력을 생성·수정·삭제하지 않는다.
