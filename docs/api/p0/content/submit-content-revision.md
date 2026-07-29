# 콘텐츠 수정본 승인 재요청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-02`, `CON-05`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 반려된 `EDIT_REJECTED` 수정본을 심사에 재요청한다. 서버는 후보 필드와 후보 대표 이미지를 검증한 뒤
`EDIT_REJECTED → EDIT_REQUESTED`로 전이하며, 원본 `PUBLISHED` 콘텐츠는 심사 중에도 그대로 유지한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `POST /operator/content-revisions/{revisionId}/submit` | `content_revision`, `image_object` |
| `CON-05` | `POST /operator/content-revisions/{revisionId}/submit` | `content`, `content_revision` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/content-revisions/{revisionId}/submit`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 원본 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 제출 결과를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 상태 전이 명령이므로 적용하지 않는다. |

## 3. 콘텐츠 수정본 승인 재요청

### Request

```http
POST /api/v1/operator/content-revisions/{revisionId}/submit
```

#### Request Example

```http
POST /api/v1/operator/content-revisions/501/submit HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `revisionId` | String | Y | 양의 10진 문자열인 제출할 수정본 식별자다. |

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
  "message": "콘텐츠 수정본 승인 재요청에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "status": "EDIT_REQUESTED",
    "submittedAt": "2026-07-30T02:10:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.revisionId` | String | 양의 10진 문자열인 제출한 수정본 식별자 |
| `data.contentId` | String | 양의 10진 문자열인 원본 콘텐츠 식별자 |
| `data.status` | String | 제출 후 상태 `EDIT_REQUESTED` |
| `data.submittedAt` | String | 심사 요청 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양수가 아니거나 기존 후보 필드·기존 후보 대표 이미지가 제출 조건을 만족하지 않는다. 수정본을 전이하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 전이하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 원본 콘텐츠 소유 관계가 없다. 수정본을 전이하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없다. 수정본을 전이하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 수정본이 `EDIT_REJECTED`가 아니거나 원본이 더 이상 `PUBLISHED`가 아니다. 원본과 수정본을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. `EDIT_REJECTED` 상태이며 기존 후보 필드와 기존 후보 대표 이미지가 모두 존재하고 유효할 때만 재요청한다.
2. 이 API는 이미지 객체 식별자를 받지 않으며 새 `TEMPORARY` 이미지 객체의 발급·검증·연결을 요구하지 않는다. 이미지 변경이 없으면 기존 후보 대표 이미지 스냅샷을 그대로 유지한다.
3. 이미지 변경이 필요하면 먼저 [콘텐츠 수정본 편집](update-content-revision.md)에서 본인 소유의 유효한 `TEMPORARY` 이미지 객체를 후보 대표 이미지로 연결한 뒤 이 API를 호출한다.
4. 제출 성공 후 후보 필드는 동결하며 지역 관리자의 승인·반려 또는 소유 운영자의 철회만 `EDIT_REQUESTED`를 종결할 수 있다.
5. 수정본 제출은 현재 공개 원본, 공개 콘텐츠 목록·상세와 예약 가능 여부를 변경하지 않는다.
6. 제출 시각과 처리자는 append-only 수정본 상태 이력과 감사 기록으로 재현할 수 있어야 한다.
