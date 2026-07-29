# 콘텐츠 수정본 심사 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-10`, `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 콘텐츠·지역 관리자 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [인증·프로필](../../../p0/auth-profile.md), [API 공통 계약](../../common/README.md), 선행 계약 결정 PR |

## 1. 개요

담당 지역 관리자가 심사 대기 중인 콘텐츠 수정본의 후보 정보를 조회한다. 생성 시 완전 후보로 동결된
`EDIT_REQUESTED`만 심사 상세 대상이며, 심사 대상이 아닌 수정본은 이 API로 노출하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-10`, `CON-05` | `GET /region-admin/content-revisions/{revisionId}` | `content_revision`, `content`, `image_object` |
| `AUTH-01` | `GET /region-admin/content-revisions/{revisionId}` | `content.region_id`, `user_role_assignment.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/region-admin/content-revisions/{revisionId}`; 시각은 ISO 8601 `+09:00` 오프셋 문자열 |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN`과 원본 콘텐츠의 담당 지역 일치가 필요 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`의 수정 후보와 API별 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않음 |

## 3. 콘텐츠 수정본 심사 상세 조회

### Request

```http
GET /region-admin/content-revisions/{revisionId}
```

#### Request Example

```http
GET /api/v1/region-admin/content-revisions/201 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 상태의 `REGION_ADMIN`이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `revisionId` | Long | Y | 조회할 수정본 식별자. 양의 정수여야 한다. |

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
  "message": "콘텐츠 수정본 심사 상세 조회에 성공했습니다.",
  "data": {
    "contentRevisionId": 201,
    "contentId": 101,
    "status": "EDIT_REQUESTED",
    "editorId": 20,
    "title": "김해 가야문화 체험 여름 프로그램",
    "description": "여름방학 프로그램을 추가한 체험입니다.",
    "locationText": "김해시 가야의길 190",
    "operatingHoursText": "매주 토요일 10:00~16:00",
    "contactText": "055-123-4567",
    "precautions": "편한 복장을 준비해 주세요.",
    "ageRequirement": "8세 이상",
    "materials": "개인 물병",
    "cancellationPolicyText": "회차 시작 전까지 전체 예약을 취소할 수 있습니다.",
    "candidateImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/revisions/201/image?X-Amz-Signature=...",
    "candidateImageUrlExpiresAt": "2026-07-30T09:30:00+09:00",
    "submittedAt": "2026-07-30T09:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.contentRevisionId` | Long | 수정본 식별자 |
| `data.contentId` | Long | 원본 콘텐츠 식별자 |
| `data.status` | String | 항상 `EDIT_REQUESTED` |
| `data.editorId` | Long | 수정본을 생성한 소유 운영자 식별자 |
| `data.title` | String | 동결된 후보 제목 |
| `data.description` | String | 동결된 후보 소개 |
| `data.locationText` | String | 동결된 후보 위치 안내 |
| `data.operatingHoursText` | String | 동결된 후보 운영 시간 안내 |
| `data.contactText` | String | 동결된 후보 연락처 안내 |
| `data.precautions` | String | 동결된 후보 유의사항 |
| `data.ageRequirement` | String | 동결된 후보 연령 조건 |
| `data.materials` | String | 동결된 후보 준비물 안내 |
| `data.cancellationPolicyText` | String | 동결된 후보 무료 예약 취소 안내 |
| `data.candidateImageUrl` | String | 담당 지역 권한 확인 뒤 발급한 후보 대표 이미지의 단기 presigned GET URL |
| `data.candidateImageUrlExpiresAt` | String | 후보 대표 이미지 조회 URL 만료 시각 |
| `data.submittedAt` | String | 생성과 동시에 심사 요청된 수정본의 동결 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `revisionId`가 양의 정수가 아니다. 수정본과 콘텐츠 상태는 변경하지 않으며 요청 값을 수정한 뒤 재시도할 수 있다. |
| `400` | `INVALID_TYPE` | `revisionId`를 `Long`으로 변환할 수 없다. 수정본과 콘텐츠 상태는 변경하지 않으며 값 형식을 수정한 뒤 재시도할 수 있다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. 수정본과 콘텐츠 상태는 변경하지 않으며 유효한 인증 정보로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 `REGION_ADMIN`이 아니거나 원본 콘텐츠의 지역이 담당 지역과 일치하지 않는다. 수정본과 콘텐츠 상태는 변경하지 않으며 동일한 권한 상태로 재시도해도 성공하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없거나 `EDIT_REQUESTED` 심사 대상이 아니다. 심사 대상이 아닌 수정본의 존재·상태는 노출하지 않으며 요청 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 수정본·원본 콘텐츠·후보 이미지 연결의 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 수정본과 콘텐츠 상태는 변경하지 않으며 일시적 장애라면 같은 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 활성 상태이며 담당 `region_id`가 연결된 `REGION_ADMIN`이어야 한다.
2. 수정본의 원본 콘텐츠 `region_id`와 인증 지역 관리자의 담당 `region_id`가 일치해야 한다.
3. `EDIT_REQUESTED` 수정본만 상세를 반환한다. 심사 대상이 아닌 수정본은 `404 NOT_FOUND`로 처리한다.
4. 후보 대표 이미지는 `ACTIVE` 이미지 객체와 유효한 직접 연결이 있어야 한다. 서버는 지역 권한을 재검증한 뒤 단기 presigned GET URL과 정확한 만료 시각을 함께 발급한다.
5. `candidateImageUrl`과 만료 시각은 DB나 Redis에 저장하지 않고 응답을 조립할 때마다 새로 생성한다.
6. 응답에는 `imageObjectId`, S3 객체 키, 원본 파일명과 사용자 식별정보를 포함하지 않는다.
7. 조회는 수정본, 원본 콘텐츠, 이미지 객체와 감사 기록을 생성·수정·삭제하지 않는다.

### 감사 및 정합성

- 이 API는 수정본 상태 전이와 감사 이벤트를 생성하지 않는다.
- 조회 성공과 실패는 `requestId`, 담당 지역 식별자, 수정본 식별자와 결과 코드만 구조화 로그에 남긴다.
- 후보 이미지 객체 키, 후보 필드 원문과 사용자 식별정보는 구조화 로그에 남기지 않는다.
