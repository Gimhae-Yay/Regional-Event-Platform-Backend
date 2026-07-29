# 콘텐츠 철회 요청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-07`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 공개 중인 콘텐츠의 철회를 사유와 함께 요청한다. 요청은 콘텐츠 상태를 `PUBLISHED`로 유지하고
`content_log`에 `WITHDRAWAL_REQUESTED` 이벤트만 기록한다. 같은 운영자의 중복 요청은 저장된 기존 결과를 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `POST /operator/contents/{contentId}/withdrawal-requests` | `content`, `content_log` |
| `CON-07` | `POST /operator/contents/{contentId}/withdrawal-requests` | `content.status`, 철회 요청 사유 |
| `CON-09` | `POST /operator/contents/{contentId}/withdrawal-requests` | `content_log.actor_id`, `content_log.date`, `content_log.reason` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}/withdrawal-requests`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 최초 철회 요청 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 상태 전이 요청이 아니므로 적용하지 않는다. |

## 3. 콘텐츠 철회 요청

### Request

```http
POST /api/v1/operator/contents/{contentId}/withdrawal-requests
```

#### Request Example

```http
POST /api/v1/operator/contents/101/withdrawal-requests HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "운영 일정 변경으로 콘텐츠 철회를 요청합니다."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 철회를 요청할 콘텐츠 식별자다. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "운영 일정 변경으로 콘텐츠 철회를 요청합니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 앞뒤 공백을 제거한 비어 있지 않은 철회 요청 사유다. |

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
  "message": "콘텐츠 철회 요청에 성공했습니다.",
  "data": {
    "contentId": 101,
    "status": "PUBLISHED",
    "reason": "운영 일정 변경으로 콘텐츠 철회를 요청합니다.",
    "requestedAt": "2026-07-30T14:30:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.contentId` | Number | 철회를 요청한 콘텐츠 식별자 |
| `data.status` | String | 요청 뒤에도 유지되는 `PUBLISHED` |
| `data.reason` | String | 최초 요청에 저장된 철회 요청 사유 |
| `data.requestedAt` | String | 최초 철회 요청 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 식별자가 양수가 아니거나 `reason`이 누락·공백이다. 콘텐츠와 로그를 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 콘텐츠와 로그를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `contentId`를 정수로 변환할 수 없다. 콘텐츠와 로그를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 콘텐츠와 로그를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 콘텐츠와 로그를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐다. 콘텐츠와 로그를 변경하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 콘텐츠가 `PUBLISHED`가 아니고 반환할 기존 미종결 철회 요청도 없다. 콘텐츠와 로그를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_STATE_CONFLICT",
  "message": "콘텐츠 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 콘텐츠 행을 잠근 뒤 소유 관계, 현재 상태와 가장 최근 철회 관련 로그를 다시 확인한다.
2. 최초 요청은 `PUBLISHED` 상태에서만 성공한다. 콘텐츠 상태는 바꾸지 않고 요청 사유·요청자·요청 시각을 가진 `WITHDRAWAL_REQUESTED` 로그를 한 건 추가한다.
3. 가장 최근 철회 관련 로그가 `WITHDRAWAL_REQUESTED`이고 요청자가 같은 소유 운영자이면, 새 로그·사유·시각·감사 기록을 만들지 않고 최초 요청 결과를 반환한다.
4. 이 API는 콘텐츠를 비공개 또는 `WITHDRAWN`으로 전이하지 않는다. 담당 지역 관리자의 승인만 `PUBLISHED → WITHDRAWN` 전이를 수행한다.
