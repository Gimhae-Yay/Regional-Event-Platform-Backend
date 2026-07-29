# 콘텐츠 수정본 사유 포함 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [인증·프로필](../../../p0/auth-profile.md), [API 공통 계약](../../common/README.md), 선행 계약 결정 PR |

## 1. 개요

담당 지역 관리자가 생성 즉시 `EDIT_REQUESTED`로 동결된 완전 후보 수정본을 사유와 함께 반려한다. 반려된 수정본은
원본 공개본에 반영하지 않고, 처리자·처리 시각·사유를 수정본 수명주기에 보존한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14`, `CON-05` | `POST /region-admin/content-revisions/{revisionId}/reject` | `content_revision`, `content` |
| `AUTH-01`, `CON-09` | `POST /region-admin/content-revisions/{revisionId}/reject` | `content.region_id`, `user_role_assignment`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-revisions/{revisionId}/reject`; 시각은 ISO 8601 `+09:00` 오프셋 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN`과 원본 콘텐츠의 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 `CONTENT_REVISION_REVIEW_CONFLICT`를 포함한 API별 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 명령 API이므로 적용하지 않음 |

## 3. 콘텐츠 수정본 사유 포함 반려

### Request

```http
POST /region-admin/content-revisions/{revisionId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/content-revisions/201/reject HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "reviewReason": "운영 시간 안내를 보완해 주세요."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 상태의 `REGION_ADMIN`이어야 한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `revisionId` | Long | Y | 반려할 수정본 식별자. 양의 정수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reviewReason": "운영 시간 안내를 보완해 주세요."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `reviewReason` | String | Y | 반려 사유. 공백만으로 구성할 수 없으며 `content_revision.review_reason`에 기록한다. |

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
  "message": "콘텐츠 수정본 반려에 성공했습니다.",
  "data": {
    "contentRevisionId": 201,
    "contentId": 101,
    "revisionStatus": "EDIT_REJECTED",
    "reviewedAt": "2026-07-30T10:00:00+09:00",
    "reviewReason": "운영 시간 안내를 보완해 주세요."
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 반려 성공 메시지 |
| `data.contentRevisionId` | Long | 반려된 수정본 식별자 |
| `data.contentId` | Long | 원본 콘텐츠 식별자 |
| `data.revisionStatus` | String | 항상 `EDIT_REJECTED` |
| `data.reviewedAt` | String | 수정본 반려 처리 시각 |
| `data.reviewReason` | String | 저장된 반려 사유 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양의 정수가 아니거나 `reviewReason`이 없거나 공백뿐이다. 수정본·원본·감사 기록을 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `revisionId`를 `Long`으로 변환할 수 없다. 수정본·원본·감사 기록을 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 수정본·원본·감사 기록을 변경하지 않으며 JSON 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 수정본·원본·감사 기록을 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 원본 콘텐츠의 지역이 담당 지역과 일치하지 않는다. 수정본·원본·감사 기록을 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 심사 대상 `EDIT_REQUESTED`가 아니다. 심사 대상이 아닌 수정본의 존재·상태는 노출하지 않는다. 수정본·원본·감사 기록을 변경하지 않는다. |
| `409` | `CONTENT_REVISION_REVIEW_CONFLICT` | 심사 대상 판정 뒤 동일 수정본의 승인·반려·운영자 철회가 먼저 종결되었다. 수정본·원본·성공 감사 기록을 부분 변경하지 않으며 현재 상태를 다시 조회해야 한다. 롤백 뒤에는 비개인 실패 `audit_event`를 별도 트랜잭션으로 기록한다. |
| `500` | `INTERNAL_SERVER_ERROR` | 수정본·원본 콘텐츠 연결의 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 수정본·원본·감사 기록은 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "CONTENT_REVISION_REVIEW_CONFLICT",
  "message": "콘텐츠 수정본을 심사할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 상태이며 담당 `region_id`가 연결된 `REGION_ADMIN`이어야 한다.
2. 수정본의 원본 콘텐츠 `region_id`와 인증 지역 관리자의 담당 `region_id`가 일치해야 한다.
3. 수정본이 `EDIT_REQUESTED`가 아니면 `404 NOT_FOUND`로 처리한다. 이때 심사 대상이 아닌 수정본의 존재·정확한 상태를 응답으로 노출하지 않는다.
4. 생성 즉시 `EDIT_REQUESTED` 상태가 된 수정본은 필수 후보 필드와 후보 대표 이미지 연결을 검증한 완전 후보이며, 심사 중에는 후보 필드를 변경하지 않는다.
5. 심사 대상 수정본만 `EDIT_REJECTED`로 전이하고, 요청 본문의 `reviewReason`을 `reviewed_at`, `reviewed_by_user_id`, `review_reason`과 함께 기록한다.
6. 반려된 동결 후보의 필드와 후보 대표 이미지 연결은 원본 공개본에 반영하지 않는다.
7. 승인·반려·운영자 철회가 같은 `EDIT_REQUESTED` 수정본을 경합하면 조건부 종결에 성공한 하나만 성공한다. 심사 대상 판정 후 다른 종결이 먼저 커밋되면 `409 CONTENT_REVISION_REVIEW_CONFLICT`로 처리한다.

### 감사 및 정합성

- 수정본 `EDIT_REJECTED` 전이와 성공 `audit_event`는 하나의 MySQL 트랜잭션에서 함께 커밋하거나 함께 롤백한다.
- 성공 감사 기록은 처리자, 처리 시각, 원본 콘텐츠와 수정본 식별자, 반려 사유를 재현할 수 있어야 한다.
- 실패 시 원본 공개본, 수정본 후보와 성공 감사 이벤트를 변경하지 않는다. 롤백된 거부·충돌은 롤백 완료 뒤 별도 트랜잭션에서 비개인 `FAILURE` `audit_event`로 기록한다.
