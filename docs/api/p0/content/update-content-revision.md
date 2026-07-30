# 콘텐츠 수정본 편집 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-14`, `AUTH-01`, `CON-05` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 `EDIT_REJECTED` 수정본의 후보 콘텐츠 정보를 전체 교체 방식으로 편집한다. 공개 콘텐츠 원본과 회차는
수정하지 않으며 새 대표 이미지를 사용할 때는 이미 존재하는 `ACTIVE` 이미지 객체를 지정한다. 공개 전 수정본의
후보 공개 예정 시각도 이 수정본의 후보 값으로 함께 편집한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-14` | `PUT /operator/content-revisions/{revisionId}` | `content_revision` |
| `CON-05` | `PUT /operator/content-revisions/{revisionId}` | `content_revision.status`, 후보 필드 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/content-revisions/{revisionId}`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 원본 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 편집된 수정본 상태를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 수정이므로 적용하지 않는다. |

## 3. 콘텐츠 수정본 편집

### Request

```http
PUT /api/v1/operator/content-revisions/{revisionId}
```

#### Request Example

```http
PUT /api/v1/operator/content-revisions/501 HTTP/1.1
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
  "publishAt": "2026-08-20T09:00:00+09:00",
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
| `revisionId` | String | Y | 양의 10진 문자열인 편집할 수정본 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
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
  "publishAt": "2026-08-20T09:00:00+09:00",
  "representativeImageObjectId": "301"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `title` | String | Y | 비어 있지 않은 콘텐츠 제목 |
| `description` | String | Y | 비어 있지 않은 콘텐츠 소개 |
| `locationText` | String | Y | 비어 있지 않은 위치 안내 |
| `operatingHoursText` | String | Y | 비어 있지 않은 운영 시간 안내 |
| `contactText` | String | Y | 비어 있지 않은 연락처 안내 |
| `precautions` | String | Y | 비어 있지 않은 유의사항 |
| `ageRequirement` | String | Y | 비어 있지 않은 연령 조건 |
| `materials` | String | Y | 비어 있지 않은 준비물 |
| `cancellationPolicyText` | String | Y | P0 무료 예약 취소 정책을 안내하는 비어 있지 않은 문구 |
| `publishAt` | String | 조건부 | 기존 수정본의 후보 `publish_at`이 있으면 필수인 후보 공개 예정 시각이다. 후보 `publish_at`이 `NULL`인 공개 콘텐츠 수정본에서는 제공할 수 없다. |
| `representativeImageObjectId` | String | N | 교체할 경우에만 제공하는 양의 10진 문자열인 이미 존재하는 `ACTIVE` 이미지 객체 식별자. 연결 전 S3 객체 체크섬을 검증한다. |

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
  "message": "콘텐츠 수정본 편집에 성공했습니다.",
  "data": {
    "revisionId": "501",
    "contentId": "101",
    "status": "EDIT_REJECTED"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.revisionId` | String | 양의 10진 문자열인 편집한 수정본 식별자 |
| `data.contentId` | String | 양의 10진 문자열인 원본 콘텐츠 식별자 |
| `data.status` | String | 편집 후에도 유지되는 `EDIT_REJECTED` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 식별자 또는 요청 필드가 누락·공백이거나 후보 `publish_at`과 `publishAt` 조건이 맞지 않거나, 지정한 이미지 객체가 존재하지 않거나 `ACTIVE` 상태가 아니거나 S3 객체 체크섬 검증에 실패한다. 수정본을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 수정본을 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `representativeImageObjectId`가 JSON 문자열이 아니다. 수정본을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수정본을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 원본 콘텐츠 소유 관계가 없다. 수정본을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 수정본이 없다. 수정본을 변경하지 않는다. |
| `409` | `CONTENT_STATE_CONFLICT` | 수정본이 `EDIT_REJECTED`가 아니다. 공개본과 수정본을 변경하지 않는다. |

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

1. `contentId`, 지역, 소유자와 콘텐츠 유형은 수정본 편집으로 바꾸지 않는다.
2. 기존 수정본의 후보 `publish_at`이 있으면 `publishAt`이 필수이며 해당 후보 값을 교체한다. 후보 `publish_at`이 `NULL`인 공개 콘텐츠 수정본은 `publishAt`을 제공할 수 없다.
3. `representativeImageObjectId`를 제공하면 서버는 현재 `ACTIVE`이고 S3 `HEAD` 결과의 SHA-256 Base64 체크섬이 이미지 객체에 저장된 값과 같은 이미지 객체일 때만 후보 대표 이미지로 연결한다. 제공하지 않으면 기존 후보 대표 이미지 스냅샷을 유지한다.
4. 공개 회차의 수정 가능 필드는 P0에서 확정되지 않았으므로 이 API는 회차·정원·체크인 창을 수정하지 않는다.
5. `EDIT_REQUESTED`에서는 후보 필드가 동결된다. 심사에서 반려된 수정본은 이 API로 보완할 수 있지만 `EDIT_REJECTED` 상태는 종결 상태로 유지한다. 새 심사를 요청하려면 [콘텐츠 수정본 생성](create-content-revision.md)으로 새 수정본을 생성해야 한다.
6. 편집은 원본 `content`와 공개 캐시의 버전을 변경하지 않는다.
