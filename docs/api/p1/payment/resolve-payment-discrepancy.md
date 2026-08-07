# 결제 불일치 문제없음 종결 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-04` |
| 소유 도메인 | 결제 |
| 기준 문서 | [결제 API](payment.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 증빙과 사유를 남기고 `OPEN` 결제 불일치를 문제없음으로
종결한다. 이 API는 결제를 승인 처리하거나 취소된 예약을 강제로 확정하지 않는다. 조사 결과 전액환불이
필요하면 이 API를 사용하지 않고 환불 도메인의 결제 불일치 전액환불 요청 API를 사용한다. 이중 승인은
P1에서 적용하지 않으며 권한 있는 전체관리자 1명이 처리한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-10, ADM-04 | `POST /api/v1/platform-admin/payment-discrepancies/{discrepancyId}/manual-actions` | `payment_discrepancy`, `payment_discrepancy_action`, `audit_event` |

## 2. 공통 계약 참조

조치·응답·오류 규칙은 [결제 API](payment.md#2-공통-계약-참조)를 따른다.

## 3. 결제 불일치 문제없음 종결

### Request

```http
POST /api/v1/platform-admin/payment-discrepancies/{discrepancyId}/manual-actions
```

#### Request Example

```http
POST /api/v1/platform-admin/payment-discrepancies/301/manual-actions HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "evidenceReference": "PortOne 재조회 스크린샷 #4821",
  "reason": "PortOne 재조회 결과 금액 불일치는 통화 표시 오류였으며 실제 승인 금액은 스냅샷과 일치함을 확인"
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
| `discrepancyId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 결제 불일치 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "evidenceReference": "PortOne 재조회 스크린샷 #4821",
  "reason": "PortOne 재조회 결과 금액 불일치는 통화 표시 오류였으며 실제 승인 금액은 스냅샷과 일치함을 확인"
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
200 OK
```

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "결제 불일치 문제없음 종결에 성공했습니다.",
  "data": {
    "discrepancyId": "301",
    "status": "RESOLVED_NO_ISSUE",
    "resolvedAt": "2026-08-06T05:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.discrepancyId` | String | 종결한 불일치 식별자다. |
| `data.status` | String | 항상 `RESOLVED_NO_ISSUE`다. |
| `data.resolvedAt` | String | 종결과 성공 감사 이력 기록 시각이다. UTC ISO 8601 형식이다. |

이 응답은 대상 결제(`payment.status`)를 변경하지 않는다. 결제는 조사된 이력을 보존하기 위해 `DISCREPANT`로 남는다.

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `discrepancyId`를 양의 정수 식별자로 처리할 수 없다. 종결하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `evidenceReference` 또는 `reason`이 누락·공백·500자 초과다. 불일치·조치·감사 이력을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 불일치·조치·감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 불일치·조치·감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 아니다. 불일치·조치·감사 이력을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 불일치가 없다. 불일치·조치·감사 이력을 변경하지 않는다. |
| `409` | `PAYMENT_DISCREPANCY_STATE_CONFLICT` | 대상 불일치가 `OPEN`이 아니다(이미 문제없음 종결됐거나 전액환불이 요청됨). 불일치·조치·감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 불일치·조치·감사 이력을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "PAYMENT_DISCREPANCY_STATE_CONFLICT",
  "message": "결제 불일치 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 배정을 가져야 한다. 콘텐츠 운영자와 지역 관리자는 이 API를 호출할 수 없다.
2. 대상 불일치는 `OPEN`이어야 한다. `RESOLVED_NO_ISSUE` 또는 `REFUND_REQUESTED`이면 `409 PAYMENT_DISCREPANCY_STATE_CONFLICT`로 거부해 이미 종결된 건을 중복 처리하지 않는다.
3. 이 조치는 결제를 승인 처리하거나 취소된 예약을 강제로 확정하지 않는다. `payment.status`는 변경하지 않고 `DISCREPANT`로 유지한다.
4. `payment_discrepancy.status`를 `RESOLVED_NO_ISSUE`로 전이하고, `action = NO_ISSUE_CLOSE`, `evidenceReference`, `reason`과 처리자·시각을 포함한 `payment_discrepancy_action`을 생성한다.
5. 이중 승인은 적용하지 않는다. 처리자 1명의 요청만으로 종결이 확정된다.
6. 불일치 종결과 서버가 부여한 `requestId`를 포함한 `PAYMENT_DISCREPANCY` 감사 이력은 하나의 트랜잭션으로 처리한다.
7. 결제 비밀값, PortOne 원문과 전체 결제수단 정보는 `evidenceReference`나 감사 이력에 기록하지 않는다.
