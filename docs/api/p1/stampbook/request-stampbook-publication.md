# 스탬프북 공개 심사 요청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 `DRAFT` 스탬프북의 공개 심사를 요청한다. 이 API는 즉시 방문자에게 공개하지 않으며,
상태를 `DRAFT → PENDING_REVIEW`로 전이한다. 담당 지역 관리자의 별도 승인 후에만 `PUBLISHED`가 된다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/operator/stampbooks/{stampbookId}/publish` | `stampbook.status`, `coupon_policy`, `audit_event` |

## 2. 공통 계약 참조

공개 심사 요청·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다.

## 3. 스탬프북 공개 심사 요청

### Request

```http
POST /api/v1/operator/stampbooks/{stampbookId}/publish
```

#### Request Example

```http
POST /api/v1/operator/stampbooks/101/publish HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "지역 관리자 공개 심사 요청"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `stampbookId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 스탬프북 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "지역 관리자 공개 심사 요청"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `reason` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 심사 요청 사유다. 빈 문자열 또는 공백만으로 된 값은 허용하지 않으며 성공 감사 이력에 기록한다. |

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
  "message": "스탬프북 공개 심사 요청에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "status": "PENDING_REVIEW",
    "requestedAt": "2026-08-06T02:20:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 공개 심사를 요청한 스탬프북 식별자다. |
| `data.status` | String | 항상 `PENDING_REVIEW`다. 이 응답만으로 방문자에게 공개되지 않는다. |
| `data.requestedAt` | String | 심사 요청과 성공 감사 이력 기록 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 상태와 감사 이력을 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | 사유가 누락·공백·500자 초과다. 상태와 감사 이력을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 상태와 감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태와 감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 대상 스탬프북의 담당 범위를 벗어난다. 상태와 감사 이력을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 스탬프북이 없다. 상태와 감사 이력을 변경하지 않는다. |
| `409` | `STAMPBOOK_STATE_CONFLICT` | 대상 스탬프북이 `DRAFT`가 아니다. 상태와 감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 상태와 감사 이력을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "STAMPBOOK_STATE_CONFLICT",
  "message": "스탬프북 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증 주체가 활성·승인된 `OPERATOR`이고 대상 스탬프북 지역 및 모든 대상 콘텐츠의 소유 운영자인지 검증한다.
2. 상태가 `DRAFT`일 때만 `PENDING_REVIEW`로 전이한다. `PENDING_REVIEW`, `PUBLISHED`, `ENDED`에서는 `STAMPBOOK_STATE_CONFLICT`를 반환한다.
3. 대상 콘텐츠는 하나 이상이어야 하고 모두 스탬프북 지역과 같아야 한다. 완료 보상 정책은 같은 지역의 `STAMPBOOK_COMPLETION` 정책이어야 한다.
4. 이 요청은 보상 정책을 공개 상태로 전이하지 않는다. 담당 지역 관리자의 승인 시점에 잠근 보상 정책이 `PUBLISHED`인지 최종 검증하고 스탬프북을 `PUBLISHED`로 전이하는 별도 API가 필요하다.
5. 상태 전이와 대상·처리자·이전·이후 상태·사유·시각 및 서버가 부여한 `requestId`를 포함한 `STAMPBOOK` 공개 심사 요청 감사 이력은 하나의 트랜잭션으로 처리한다.
