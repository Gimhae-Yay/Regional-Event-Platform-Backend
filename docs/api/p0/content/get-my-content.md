# 내 콘텐츠 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `AUTH-01` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

소유 운영자가 자신의 콘텐츠의 현재 정보를 조회한다. 콘텐츠는 생성 시 필수 정보를 검증해 심사 요청 상태로 만들므로
정적 콘텐츠 필드는 `null`이 아니다. 대표 이미지·회차의 상세 정보는 이 응답에 포함하지 않는다.

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
| `contentId` | Long | Y | 조회할 콘텐츠 식별자다. 양수여야 한다. |

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
    "contentId": 101,
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
    "publishAt": "2026-08-15T09:00:00+09:00",
    "status": "PENDING",
    "createdAt": "2026-07-30T14:00:00+09:00",
    "updatedAt": "2026-07-30T14:05:00+09:00"
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
| `data.locationText` | String | 위치 안내 |
| `data.operatingHoursText` | String | 운영 시간 안내 |
| `data.contactText` | String | 연락처 안내 |
| `data.precautions` | String | 유의사항 |
| `data.ageRequirement` | String | 연령 조건 |
| `data.materials` | String | 준비물 |
| `data.cancellationPolicyText` | String | P0 무료 예약 취소 정책 안내 문구 |
| `data.publishAt` | String | 공개 예정 시각 |
| `data.status` | String | 현재 콘텐츠 상태 |
| `data.createdAt` | String | 콘텐츠 생성 시각 |
| `data.updatedAt` | String | 마지막 수정 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `contentId`가 양수가 아니다. 콘텐츠를 반환하지 않는다. |
| `400` | `INVALID_TYPE` | `contentId`를 정수로 변환할 수 없다. 콘텐츠를 반환하지 않는다. |
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

1. 서버는 인증된 운영자가 소유하고 소프트 삭제되지 않은 콘텐츠만 반환한다.
2. 소프트 삭제 전 콘텐츠는 필수 콘텐츠 필드, 현재 대표 이미지 한 개와 회차 한 개 이상을 가진다.
3. 대표 이미지의 S3 객체 키, 이미지 객체 식별자, 회차·예약 정보는 응답에 노출하지 않는다.
4. 조회는 콘텐츠, 대표 이미지 연결, 회차와 감사 기록을 변경하지 않는다.
