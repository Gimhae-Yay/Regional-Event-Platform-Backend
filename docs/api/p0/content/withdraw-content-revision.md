# 심사 중 콘텐츠 수정본 철회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 심사 중인 `EDIT_REQUESTED` 수정본을 사유와 함께 철회한다. 철회는 `EDIT_REQUESTED → EDIT_WITHDRAWN`
전이만 수행한다. 공개 콘텐츠 수정본의 원본은 계속 `PUBLISHED`이고, 공개 전 수정본으로 이미 `PENDING`이 된
원본은 철회 뒤에도 `PENDING`을 유지한다. 같은 수정본의 반복 철회는 저장된 결과를 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `POST /operator/content-revisions/{revisionId}/withdraw` | `content_revision` |
| `CON-05` | `POST /operator/content-revisions/{revisionId}/withdraw` | `content_revision.status`, 철회 사유 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/content-revisions/{revisionId}/withdraw`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_OPERATOR` snapshot으로 1차 인가하고, DB에서 활성 `ORDINARY` 계정, 현재 담당 지역 일치와 원본 콘텐츠 소유 관계를 확인한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 철회 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 상태 전이 명령이므로 적용하지 않는다. |

## 3. 심사 중 콘텐츠 수정본 철회

### Request

```http
POST /api/v1/operator/content-revisions/{revisionId}/withdraw
```

#### Request Example

```http
POST /api/v1/operator/content-revisions/501/withdraw HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "공개 예정 시각을 다시 조정해야 합니다."
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
| `revisionId` | String | Y | 양의 10진 문자열인 철회할 수정본 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "공개 예정 시각을 다시 조정해야 합니다."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 앞뒤 공백을 제거한 비어 있지 않은 철회 사유다. |

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
  "message": "콘텐츠 수정본 철회에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "status": "EDIT_WITHDRAWN",
    "withdrawalReason": "공개 예정 시각을 다시 조정해야 합니다.",
    "withdrawnAt": "2026-07-30T02:20:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.revisionId` | String | 양의 10진 문자열인 철회한 수정본 식별자 |
| `data.contentId` | String | 양의 10진 문자열인 원본 콘텐츠 식별자 |
| `data.status` | String | 철회 후 상태 `EDIT_WITHDRAWN` |
| `data.withdrawalReason` | String | 저장된 철회 사유 |
| `data.withdrawnAt` | String | 철회 처리 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 식별자가 양수가 아니거나 `reason`이 누락·공백이다. 수정본을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 수정본을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 변경하지 않는다. |
| `403` | `FORBIDDEN` | `ROLE_OPERATOR` authority가 없거나 활성 `ORDINARY` 계정, 담당 지역 또는 원본 콘텐츠 소유 관계가 없다. 수정본을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없다. 수정본을 변경하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 수정본이 `EDIT_REQUESTED`도 기존 철회 결과도 아니며, 콘텐츠 중단·전체 철회·종료로 `EDIT_INVALIDATED`가 된 경우를 포함한다. 수정본을 변경하지 않는다. |

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

1. `EDIT_REQUESTED` 상태에서만 최초 철회가 성공한다. 승인·반려·철회·콘텐츠 중단·전체 철회·종료가 경합하면 조건부 상태 전이로 최초 하나만 성공한다. 콘텐츠 중단·전체 철회·종료가 먼저 `EDIT_INVALIDATED`를 커밋하면 이 요청은 `409 CONTENT_STATE_CONFLICT`로 거부한다. 전체 철회 무효화 사유는 [전체 콘텐츠 철회 승인](../region-content/approve-content-withdrawal.md)의 `CONTENT_WITHDRAWN` 계약을 따른다.
2. 이미 `EDIT_WITHDRAWN`인 동일 수정본의 반복 요청은 새 처리 시각·사유 이력·감사 기록을 추가하지 않고 저장된 응답을 반환한다.
3. `EDIT_APPROVED`, `EDIT_REJECTED` 또는 다른 상태의 수정본은 철회할 수 없다.
4. 철회된 수정본은 원본 후보 필드를 반영하지 않으며 상태·처리자·시각·사유를 보존한다. 공개 전 수정 심사로
   `PENDING`이 된 원본은 철회 뒤에도 `PENDING`을 유지해 자동 공개를 재개하지 않는다.
