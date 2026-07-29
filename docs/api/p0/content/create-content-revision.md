# 공개 콘텐츠 수정본 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 공개 콘텐츠의 완전한 후보 정보를 제출해 즉시 심사 중인 `EDIT_REQUESTED` 수정본을 만든다.
새 대표 이미지를 지정하지 않으면 현재 공개본의 대표 이미지를 수정본에 스냅샷으로 연결한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `POST /operator/contents/{contentId}/revisions` | `content`, `content_revision`, `image_object` |
| `CON-05` | `POST /operator/contents/{contentId}/revisions` | `content.status`, `content.version_no`, `content_revision.status` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}/revisions`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 심사 요청 수정본 식별자를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 공개 콘텐츠 수정본 생성

### Request

```http
POST /api/v1/operator/contents/{contentId}/revisions
```

#### Request Example

```http
POST /api/v1/operator/contents/101/revisions HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "title": "김해 가야문화 체험",
  "description": "가야 문화를 체험하는 행사입니다.",
  "locationText": "김해시 가야의길 190",
  "operatingHoursText": "매주 토요일 10:00~16:00",
  "contactText": "055-000-0000",
  "precautions": "편한 복장으로 참여해 주세요.",
  "ageRequirement": "초등학생 이상",
  "materials": "필기도구",
  "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다.",
  "representativeImageObjectId": "301"
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
| `contentId` | String | Y | 양의 10진 문자열인 수정본을 만들 공개 콘텐츠 식별자다. |

#### Query Parameter

없음.

#### Request Body

요청 예시의 JSON 객체를 사용한다. `representativeImageObjectId`를 생략하면 현재 공개본의 대표 이미지를 후보 이미지로 복사한다.

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | String | Y | 비어 있지 않은 콘텐츠 제목 |
| `description` | String | Y | 비어 있지 않은 콘텐츠 소개 |
| `locationText` | String | Y | 비어 있지 않은 위치 안내 |
| `operatingHoursText` | String | Y | 비어 있지 않은 운영 시간 안내 |
| `contactText` | String | Y | 비어 있지 않은 연락처 안내 |
| `precautions` | String | Y | 비어 있지 않은 유의사항 |
| `ageRequirement` | String | Y | 비어 있지 않은 연령 조건 |
| `materials` | String | Y | 비어 있지 않은 준비물 |
| `cancellationPolicyText` | String | Y | P0 무료 예약 취소 정책을 안내하는 비어 있지 않은 문구 |
| `representativeImageObjectId` | String | N | 이미지 변경 시에만 제공하는 양의 10진 문자열인 이미 존재하는 `ACTIVE` 이미지 객체 식별자. 연결 전 S3 객체 체크섬을 검증한다. |

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
  "message": "콘텐츠 수정본 생성과 승인 요청에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "status": "EDIT_REQUESTED",
    "baseContentVersion": 3,
    "submittedAt": "2026-07-30T02:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.revisionId` | String | 양의 10진 문자열인 생성한 수정본 식별자 |
| `data.contentId` | String | 양의 10진 문자열인 원본 공개 콘텐츠 식별자 |
| `data.status` | String | 생성 직후 심사 요청 상태 `EDIT_REQUESTED` |
| `data.baseContentVersion` | Integer | 수정본 생성 시점 원본의 버전 |
| `data.submittedAt` | String | 심사 요청 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 식별자·후보 필드가 유효하지 않거나 지정한 이미지 객체가 존재하지 않거나 `ACTIVE` 상태가 아니거나 S3 객체 체크섬 검증에 실패한다. 수정본을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 수정본을 생성하지 않는다. |
| `400` | `INVALID_TYPE` | 이미지 객체 식별자가 JSON 문자열이 아니다. 수정본을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 수정본을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐다. 수정본을 생성하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 콘텐츠가 `PUBLISHED`가 아니거나 심사 중인 수정본이 이미 있다. 기존 공개본과 수정본을 변경하지 않는다. |

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

1. 원본은 `PUBLISHED`, `deleted_at IS NULL`이어야 하며 콘텐츠별 `EDIT_REQUESTED` 수정본은 동시에 하나만 허용한다.
2. 서버는 모든 후보 필드를 검증해 수정본에 저장한다. `representativeImageObjectId`가 있으면 현재 `ACTIVE`이고 S3 `HEAD` 결과의 SHA-256 Base64 체크섬이 이미지 객체에 저장된 값과 같은 이미지 객체만 후보 대표 이미지로 연결한다.
3. 이미지 객체 ID를 생략하면 현재 공개본 대표 이미지 객체와 연결 시각을 수정본에 스냅샷으로 저장한다.
4. 수정본 생성은 원본의 상태·내용·대표 이미지 연결과 공개 조회 결과를 변경하지 않는다.
