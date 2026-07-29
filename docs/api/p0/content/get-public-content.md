# 공개 콘텐츠 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-02`, `CON-03`, `CON-04`, `SES-01`, `SES-02` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

방문자에게 공개된 콘텐츠의 현재 정보를 조회한다. 공개 전·중단·철회·종료·삭제 콘텐츠와 심사 중 수정본은 반환하지 않는다.
회차와 인증 후기 목록은 각각의 전용 조회 API가 소유하므로 이 응답에 포함하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-02` | `GET /contents/{contentId}` | `content`, `image_object` |
| `CON-03` | `GET /contents/{contentId}` | `content.status`, `content_log` |
| `CON-05` | `GET /contents/{contentId}` | `content`, `content_revision` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/contents/{contentId}`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 인증이 필요하지 않은 공개 API다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 현재 공개본을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 공개 콘텐츠 상세 조회

### Request

```http
GET /api/v1/contents/{contentId}
```

#### Request Example

```http
GET /api/v1/contents/101 HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 인증이 필요하지 않은 공개 API다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 조회할 공개 콘텐츠 식별자다. 양수여야 한다. |

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
  "message": "공개 콘텐츠 상세 조회에 성공했습니다.",
  "data": {
    "contentId": 101,
    "contentType": "EVENT_EXPERIENCE",
    "title": "김해 가야문화 체험",
    "description": "가야 문화를 체험하는 행사입니다.",
    "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/101/image?X-Amz-Signature=...",
    "representativeImageUrlExpiresAt": "2026-07-30T12:05:00+09:00",
    "locationText": "김해시 가야의길 190",
    "operatingHoursText": "매주 토요일 10:00~16:00",
    "contactText": "055-000-0000",
    "precautions": "편한 복장으로 참여해 주세요.",
    "ageRequirement": "초등학생 이상",
    "materials": "필기도구",
    "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다."
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.contentId` | Number | 콘텐츠 식별자 |
| `data.contentType` | String | P0 콘텐츠 유형 `EVENT_EXPERIENCE` |
| `data.title` | String | 콘텐츠 제목 |
| `data.description` | String | 콘텐츠 소개 |
| `data.representativeImageUrl` | String | 현재 대표 이미지의 단기 presigned GET URL |
| `data.representativeImageUrlExpiresAt` | String | 대표 이미지 조회 URL 만료 시각 |
| `data.locationText` | String | 위치 안내 |
| `data.operatingHoursText` | String | 운영 시간 안내 |
| `data.contactText` | String | 연락처 안내 |
| `data.precautions` | String | 유의사항 |
| `data.ageRequirement` | String | 연령 조건 |
| `data.materials` | String | 준비물 |
| `data.cancellationPolicyText` | String | P0 무료 예약 취소 정책 안내 문구 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양수가 아니다. 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `contentId`를 정수로 변환할 수 없다. 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 현재 공개 대상이 아니다. 상태별 존재 여부는 구분하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠와 대표 이미지 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. |

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

1. 콘텐츠가 `PUBLISHED`, `deleted_at IS NULL`일 때만 현재 `content` 행을 반환한다. `EDIT_REQUESTED`를 포함한 수정본 후보는 공개 응답에 반영하지 않는다.
2. 공개본의 현재 대표 이미지는 `ACTIVE` 객체여야 한다. 서버는 발급 직전에 공개 상태와 이미지 연결을 다시 검증한다.
3. 공개 콘텐츠의 회차·잔여 정원·예약 가능 여부와 인증 후기 목록은 이 API가 아닌 전용 API에서 조회한다.
4. 조회는 콘텐츠 버전, 회차, 이미지 참조와 감사 기록을 변경하지 않는다.
