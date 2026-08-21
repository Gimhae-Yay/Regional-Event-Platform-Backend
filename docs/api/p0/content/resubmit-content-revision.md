# 반려 콘텐츠 수정본 재제출 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [ADR-0014](../../../adr/0014-store-published-content-edits-in-relational-revision-tables.md), [ADR-0037](../../../adr/0037-block-automatic-publication-during-pre-publication-revision-review.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 가장 최근 `EDIT_REJECTED` 수정본의 저장된 후보 필드와 후보 대표 이미지를 새 수정본으로 복제해 다시 심사를
요청한다. 원본 반려 수정본은 심사 이력으로 보존하고 상태를 되돌리지 않는다. 후보 대표 이미지는 이미 연결된 이미지 객체를
서버가 직접 복제하므로 클라이언트가 이미지 객체 식별자를 다시 제출하거나 같은 이미지를 재업로드하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `POST /operator/content-revisions/{revisionId}/resubmit` | `content_revision`, `content_revision.status`, 후보 필드 |
| `CON-05` | `POST /operator/content-revisions/{revisionId}/resubmit` | 최신 반려 수정본 보존, 새 `EDIT_REQUESTED` 수정본 생성 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/content-revisions/{revisionId}/resubmit`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 원본 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 새 수정본 식별자·상태를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 상태 전이 명령이므로 적용하지 않는다. |

## 3. 반려 콘텐츠 수정본 재제출

### Request

```http
POST /api/v1/operator/content-revisions/{revisionId}/resubmit
```

#### Request Example

```http
POST /api/v1/operator/content-revisions/501/resubmit HTTP/1.1
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
| `revisionId` | String | Y | 양의 10진 문자열인 재제출 원본 `EDIT_REJECTED` 수정본 식별자다. |

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

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
  "message": "콘텐츠 수정본 재제출에 성공했습니다.",
  "data": {
    "revisionId": "502",
    "sourceRevisionId": "501",
    "contentId": "101",
    "status": "EDIT_REQUESTED",
    "baseContentVersion": 3,
    "submittedAt": "2026-08-21T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.revisionId` | String | 새로 생성한 수정본 식별자다. |
| `data.sourceRevisionId` | String | 후보 스냅샷을 복제한 기존 `EDIT_REJECTED` 수정본 식별자다. |
| `data.contentId` | String | 원본 콘텐츠 식별자다. |
| `data.status` | String | 새 수정본의 초기 상태 `EDIT_REQUESTED`다. |
| `data.baseContentVersion` | Integer | 재제출 시점에 잠근 원본 콘텐츠의 현재 버전이다. |
| `data.submittedAt` | String | 새 심사 요청 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양의 10진 문자열 형식이 아니거나 signed 64비트 `Long` 범위를 벗어난다. 수정본을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 원본 콘텐츠 소유 관계가 없다. 수정본을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 원본 콘텐츠가 소프트 삭제됐다. 수정본을 생성하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 대상이 최신 `EDIT_REJECTED` 수정본이 아니거나, 활성 `EDIT_REQUESTED` 수정본이 이미 있거나, 원본 상태와 후보 `publish_at` 조건이 맞지 않는다. 수정본을 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 기존 후보 이미지 연결 정합성 오류가 발생했다. 수정본을 생성하지 않는다. |

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

1. 서버는 원본 콘텐츠 행을 먼저 잠그고 재제출 원본 수정본을 잠근 뒤, 인증된 운영자의 소유 관계와 현재 담당 지역을
   검증한다. 대상은 해당 콘텐츠에서 가장 큰 `revision_no`를 가진 `EDIT_REJECTED` 수정본이어야 한다.
2. 같은 콘텐츠에 `EDIT_REQUESTED` 수정본이 없어야 한다. 일반 수정본 생성·다른 재제출과 경합하면 콘텐츠 잠금과 활성
   수정본 유일 제약으로 하나만 새 `EDIT_REQUESTED` 수정본을 생성한다.
3. 공개 콘텐츠 수정본은 원본이 `PUBLISHED`이고 후보 `publish_at`이 `NULL`일 때만 재제출한다. 공개 전 수정본은 원본이
   `PENDING`이고 후보 `publish_at`이 존재하며 직전 공개 전 수정 요청의 `APPROVED → PENDING` 이력이 있을 때만 재제출한다.
   최초 생성 뒤의 일반 `PENDING` 콘텐츠는 이 경로를 사용할 수 없다.
4. 서버는 기존 수정본의 모든 후보 필드, 후보 `reservation_price`, 후보 `publish_at`, 후보 대표 이미지 객체와 연결 시각을
   새 수정본에 복제한다. 후보 이미지는 `ACTIVE`이고 원본 콘텐츠 지역과 일치해야 한다. 이미 연결된 이미지 객체에 신규
   업로드 연결 검증을 다시 적용하거나 클라이언트에 이미지 객체 식별자를 요구하지 않는다.
5. 새 수정본은 현재 원본 버전을 `base_content_version`으로, 기존 최대 번호보다 1 큰 값을 `revision_no`로 저장하고
   `EDIT_REQUESTED`와 서버 현재 시각을 기록한다. 기존 `EDIT_REJECTED` 수정본의 후보·상태·반려 사유·처리 시각은 변경하지 않는다.
6. 공개 콘텐츠 원본은 `PUBLISHED`를 유지한다. 공개 전 수정본의 원본은 이미 `PENDING`이므로 다시 상태 전이하거나
   `APPROVED → PENDING` 감사 이벤트를 중복 기록하지 않는다.
7. 같은 요청을 성공 뒤 반복하면 기존 결과로 대체하지 않는다. 새 활성 수정본이 있으므로 `409 CONTENT_STATE_CONFLICT`를
   반환하고 추가 수정본을 만들지 않는다.
