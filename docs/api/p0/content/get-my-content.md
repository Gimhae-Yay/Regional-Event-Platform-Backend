# 내 콘텐츠 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `AUTH-01` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 자신의 콘텐츠의 현재 정보를 조회한다. 콘텐츠는 생성 시 필수 정보를 검증해 심사 요청 상태로 만들므로
정적 콘텐츠 필드는 `null`이 아니다. 대표 이미지의 객체 키와 객체 식별자는 노출하지 않으며, 소유자에게만 짧게 만료되는 조회 URL을 제공한다. 회차의 상세 정보는 이 응답에 포함하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03` | `GET /operator/contents/{contentId}` | `content` |
| `AUTH-01` | `GET /operator/contents/{contentId}` | 운영자 역할, `content.operator_id`, `content.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents/{contentId}`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 현재 콘텐츠 정보를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 내 콘텐츠 상세 조회

### Request

```http
GET /api/v1/operator/contents/{contentId}
```

#### Request Example

```http
GET /api/v1/operator/contents/101 HTTP/1.1
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
| `contentId` | String | Y | 양의 10진 문자열인 조회할 콘텐츠 식별자다. |

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
  "message": "내 콘텐츠 상세 조회에 성공했습니다.",
  "data": {
    "contentId": "101",
    "contentType": "EVENT_EXPERIENCE",
    "title": "김해 가야문화 체험",
    "description": "가야 문화를 체험하는 행사입니다.",
    "locationText": "김해시 가야의길 190",
    "operatingHoursText": "매주 토요일 10:00~16:00",
    "contactText": "055-000-0000",
    "precautions": "편한 복장으로 참여해 주세요.",
    "ageRequirement": "초등학생 이상",
    "materials": "필기도구",
    "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다.",
    "publishAt": "2026-08-15T00:00:00Z",
    "status": "REJECTED",
    "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/...",
    "representativeImageUrlExpiresAt": "2026-07-30T05:20:00Z",
    "rejectionReason": "대표 이미지의 안내 문구를 보완해 주세요.",
    "createdAt": "2026-07-30T05:00:00Z",
    "updatedAt": "2026-07-30T05:05:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.contentId` | String | 양의 10진 문자열인 콘텐츠 식별자 |
| `data.contentType` | String | P0 콘텐츠 유형 `EVENT_EXPERIENCE` |
| `data.title` | String | 콘텐츠 제목 |
| `data.description` | String | 콘텐츠 소개 |
| `data.locationText` | String | 위치 안내 |
| `data.operatingHoursText` | String | 운영 시간 안내 |
| `data.contactText` | String | 연락처 안내 |
| `data.precautions` | String | 유의사항 |
| `data.ageRequirement` | String | 연령 조건 |
| `data.materials` | String | 준비물 |
| `data.cancellationPolicyText` | String | P0 무료 예약 취소 정책 안내 문구 |
| `data.publishAt` | String | 공개 예정 시각 |
| `data.status` | String | 현재 콘텐츠 상태 |
| `data.representativeImageUrl` | String | 소유자에게만 발급하는 현재 대표 이미지의 짧은 유효기간 presigned GET URL |
| `data.representativeImageUrlExpiresAt` | String | `representativeImageUrl`의 UTC `Z` 만료 시각 |
| `data.rejectionReason` | String or null | 상태가 `REJECTED`이면 최신 반려 이력의 재제출에 필요한 비개인정보 사유, 그 외 상태이면 `null` |
| `data.createdAt` | String | 콘텐츠 생성 시각 |
| `data.updatedAt` | String | 마지막 수정 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양의 10진 문자열 형식이 아니다. 콘텐츠를 반환하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 콘텐츠를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 운영자 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 콘텐츠를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐다. 콘텐츠를 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 콘텐츠를 반환하지 않는다. |

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

1. 서버는 콘텐츠를 찾은 후 인증된 운영자의 소유 관계와 담당 지역을 다시 검증하고, 소프트 삭제되지 않은 콘텐츠만 반환한다.
2. 소프트 삭제 전 콘텐츠는 필수 콘텐츠 필드, 현재 대표 이미지 한 개와 회차 한 개 이상을 가진다.
3. 서버는 소유자 검증이 끝난 뒤 현재 대표 이미지에 대해 짧은 유효기간의 presigned GET URL과 만료 시각을 발급한다. `PENDING`, `REJECTED`를 포함한 비공개 상태에서도 소유자만 받을 수 있으며, S3 객체 키와 이미지 객체 식별자는 응답에 노출하지 않는다.
4. 상태가 `REJECTED`이면 최신 반려 이력의 재제출에 필요한 비개인정보 사유만 `rejectionReason`에 반환한다. 심사자·운영자 등 개인 식별 정보와 내부 심사 정보는 반환하지 않으며, 그 외 상태에서는 `null`을 반환한다.
5. 조회는 콘텐츠, 대표 이미지 연결, 회차와 감사 기록을 변경하지 않는다.
