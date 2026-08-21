# 환불 실패 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-04` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 수동 조사·처리가 필요한 환불 목록을 조회한다. 기본값은
자동 처리로 종결되지 않은 `FAILED`·`DISCREPANT` 환불이며, 조회는 환불·결제 상태를 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/refund-failures` | `refund`, `payment` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지네이션을 적용하지 않는다.

## 3. 환불 실패 목록 조회

### Request

```http
GET /api/v1/platform-admin/refund-failures
```

#### Request Example

```http
GET /api/v1/platform-admin/refund-failures?status=DISCREPANT HTTP/1.1
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
| `status` | String | N | 조회할 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나이며, 제공하지 않으면 `FAILED`와 `DISCREPANT`를 함께 조회한다. |

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
  "message": "환불 실패 목록 조회에 성공했습니다.",
  "data": {
    "refunds": [
      {
        "refundId": "552",
        "paymentId": "903",
        "reservationId": "124",
        "amount": 12000,
        "currency": "KRW",
        "status": "DISCREPANT",
        "attemptCount": 1,
        "requestedAt": "2026-08-07T01:10:00Z",
        "updatedAt": "2026-08-07T01:10:31Z"
      },
      {
        "refundId": "553",
        "paymentId": "904",
        "reservationId": null,
        "amount": 12000,
        "currency": "KRW",
        "status": "DISCREPANT",
        "attemptCount": 2,
        "requestedAt": "2026-08-07T01:15:00Z",
        "updatedAt": "2026-08-07T01:15:31Z"
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
| `data.refunds` | Array | 조회 조건을 만족하는 환불 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.refunds[].refundId` | String | 환불 식별자다. |
| `data.refunds[].paymentId` | String | 환불 대상 결제 식별자다. |
| `data.refunds[].reservationId` | String 또는 null | 환불 대상 결제와 연결된 예약 식별자다. 확정 예약 없이 생성된 결제 불일치 환불이면 `null`이다. |
| `data.refunds[].amount` | Integer | 환불 금액이다. |
| `data.refunds[].currency` | String | 통화 코드다. |
| `data.refunds[].status` | String | 환불 상태다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DISCREPANT` 중 하나다. |
| `data.refunds[].attemptCount` | Integer | 지금까지 기록된 `refund_attempt` 총 개수다. |
| `data.refunds[].requestedAt` | String | 환불 요청 시각이다. UTC ISO 8601 형식이다. |
| `data.refunds[].updatedAt` | String | `refund.requested_at`, `refund.completed_at`, 수동 확정 시각 `refund.resolved_at`, 가장 최근 `refund_attempt.attempted_at` 중 가장 늦은 상태 변경 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status`가 정의된 값 중 하나가 아니다. 조회 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 결과를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 조회 결과를 반환하지 않는다. |
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

1. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다. 콘텐츠 운영자와 지역 관리자는 이 API를 호출할 수 없다.
2. `status`를 생략하면 `FAILED`와 `DISCREPANT`를 함께 조회한다. 제공하면 해당 단일 상태만 조회하며, 정의된 값 외에는 `400 INVALID_INPUT`으로 거부한다.
3. 목록은 `updatedAt` 오름차순, 같은 시각이면 `refundId` 오름차순으로 정렬해 오래 대기한 건을 먼저 표시한다.
4. `attemptCount`는 대상 환불에 연결된 `refund_attempt` 전체 개수다.
5. 확정 예약 없이 생성된 결제 불일치 환불은 유효한 목록 항목이며 `reservationId: null`로 반환한다. 예약이 연결된 환불은 예약 식별자를 문자열로 반환하며, 예약 연결이 없다는 이유만으로 정합성 오류로 처리하지 않는다.
6. 이 API는 단순 목록이다. 페이지·커서와 사용자 지정 정렬을 제공하지 않는다.
7. 조회 시 환불, 결제와 감사 이력을 생성·수정·삭제하지 않는다.
