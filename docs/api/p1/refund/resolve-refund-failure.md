# 환불 실패 수동 조치 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-04` |
| 소유 도메인 | 환불 |
| 기준 문서 | [환불 API](refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

`ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot을 가진 활성 `PRIVILEGED` 계정이 PortOne 재조회 증빙을 근거로 `DISCREPANT` 환불의 실제 외부
처리 결과를 확정한다. 재조회로 실제 성공이 확인되면 `SUCCEEDED`로, 실제 미처리가 확인되면 `FAILED`로
전이한다. `FAILED`로 확정된 뒤에만 남은 횟수 안에서 [환불 재시도](retry-refund.md)를 사용할 수 있다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-10, ADM-04 | `POST /api/v1/platform-admin/refund-failures/{refundId}/manual-actions` | `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history` |

## 2. 공통 계약 참조

조치·응답·오류 규칙은 [환불 API](refund.md#2-공통-계약-참조)를 따른다.

## 3. 환불 실패 수동 조치

### Request

```http
POST /api/v1/platform-admin/refund-failures/{refundId}/manual-actions
```

#### Request Example

```http
POST /api/v1/platform-admin/refund-failures/552/manual-actions HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "confirmedStatus": "FAILED",
  "evidenceReference": "PortOne 재조회 스크린샷 #5013",
  "reason": "PortOne 재조회 결과 응답 미수신 구간에서 실제 취소 호출이 접수되지 않았음을 확인"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. 인증 주체는 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot을 가지고 활성 `PRIVILEGED` 계정이어야 한다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `refundId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 환불 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "confirmedStatus": "FAILED",
  "evidenceReference": "PortOne 재조회 스크린샷 #5013",
  "reason": "PortOne 재조회 결과 응답 미수신 구간에서 실제 취소 호출이 접수되지 않았음을 확인"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `confirmedStatus` | String | Y | PortOne 재조회로 확인한 실제 결과다. `SUCCEEDED` 또는 `FAILED` 중 하나만 허용한다. |
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
  "message": "환불 실패 수동 조치에 성공했습니다.",
  "data": {
    "refundId": "552",
    "status": "FAILED",
    "resolvedAt": "2026-08-07T05:00:00Z"
  }
}
```

`confirmedStatus`가 `SUCCEEDED`이면 같은 형식으로 `data.status`가 `SUCCEEDED`로 반환된다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.refundId` | String | 조치한 환불 식별자다. |
| `data.status` | String | 확정된 환불 상태다. `SUCCEEDED` 또는 `FAILED` 중 하나다. |
| `data.resolvedAt` | String | 조치와 감사 이력 기록 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `refundId`를 양의 정수 식별자로 처리할 수 없다. 조치하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `confirmedStatus`가 `SUCCEEDED`·`FAILED`가 아니거나 `evidenceReference`·`reason`이 누락·공백·500자 초과다. 환불·시도·감사 이력을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 환불·시도·감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 환불·시도·감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체에게 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` authority가 없거나 활성 `PRIVILEGED` 계정이 아니다. 환불·시도·감사 이력을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 환불이 없다. 환불·시도·감사 이력을 변경하지 않는다. |
| `409` | `REFUND_STATE_CONFLICT` | 대상 환불이 `DISCREPANT`가 아니다. 환불·시도·감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 환불·시도·감사 이력을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "REFUND_STATE_CONFLICT",
  "message": "환불 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot을 가지고 활성 `PRIVILEGED` 계정이어야 한다. 콘텐츠 운영자와 지역 관리자는 이 API를 호출할 수 없다.
2. 대상 환불은 `DISCREPANT`여야 한다. `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`이면 `409 REFUND_STATE_CONFLICT`로 거부해 이미 확정된 건을 중복 처리하지 않는다.
3. `confirmedStatus = SUCCEEDED`이면 `refund.status`를 `SUCCEEDED`로 전이하고 `completed_at`을 기록한 뒤 [환불 공통 쿠폰 복구 계약](refund.md#쿠폰-복구-계약)을 같은 상태 반영 트랜잭션에 적용한다. 이후 재시도할 수 없다.
4. `confirmedStatus = FAILED`이면 `refund.status`를 `FAILED`로 전이한다. `completed_at`은 기록하지 않으며, 남은 시도 횟수 안에서 [환불 재시도](retry-refund.md)를 사용할 수 있다.
5. 이 조치는 새 외부 호출을 만들지 않는다. 기존 `refund_attempt` 이력은 덮어쓰지 않고 그대로 보존한다.
6. 이중 승인은 적용하지 않는다. 처리자 1명의 요청만으로 확정된다.
7. 조치와 서버가 부여한 `requestId`를 포함한 `REFUND` 감사 이력을 처리하고, 쿠폰을 복구한 경우 같은 `requestId`의 `COUPON` 감사 이력을 함께 기록한다.
8. 결제 비밀값, PortOne 원문과 전체 결제수단 정보는 `evidenceReference`나 감사 이력에 기록하지 않는다.
