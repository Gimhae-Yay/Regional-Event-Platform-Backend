# 내 콘텐츠 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `AUTH-01` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

로그인한 운영자가 소유한 콘텐츠를 단순 목록으로 조회한다. P0에서는 페이지네이션, 사용자 지정 정렬,
상태·지역 필터를 제공하지 않으며 생성 시각 기준의 고정 순서를 사용한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03` | `GET /operator/contents` | `content` |
| `AUTH-01` | `GET /operator/contents` | 운영자 역할, `content.operator_id`, `content.region_id` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할과 본인 소유 콘텐츠 조건이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 콘텐츠 배열을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0 단순 목록으로 페이지네이션을 적용하지 않는다. |

## 3. 내 콘텐츠 목록 조회

### Request

```http
GET /api/v1/operator/contents
```

#### Request Example

```http
GET /api/v1/operator/contents HTTP/1.1
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

없음.

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
  "message": "내 콘텐츠 목록 조회에 성공했습니다.",
  "data": {
    "contents": [
      {
        "contentId": 101,
        "contentType": "EVENT_EXPERIENCE",
        "title": "김해 가야문화 체험",
        "status": "PENDING",
        "createdAt": "2026-07-30T14:00:00+09:00"
      },
      {
        "contentId": 102,
        "contentType": "EVENT_EXPERIENCE",
        "title": "동해 바다 공예 체험",
        "status": "REJECTED",
        "createdAt": "2026-07-30T13:00:00+09:00"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.contents` | Array | 소유 콘텐츠 배열. 결과가 없으면 `[]`다. |
| `data.contents[].contentId` | Number | 콘텐츠 식별자 |
| `data.contents[].contentType` | String | P0에서 고정된 `EVENT_EXPERIENCE` |
| `data.contents[].title` | String | 콘텐츠 제목 |
| `data.contents[].status` | String | 현재 콘텐츠 상태 |
| `data.contents[].createdAt` | String | 콘텐츠 생성 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 콘텐츠를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자 역할 또는 담당 지역이 없다. 콘텐츠를 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 콘텐츠를 반환하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증이 필요합니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증된 운영자가 `operator_id`로 소유한, 소프트 삭제되지 않은 콘텐츠만 반환한다.
2. 목록은 `created_at` 내림차순, 같은 시각이면 콘텐츠 식별자 내림차순으로 고정 정렬한다.
3. 이 API는 P0 단순 목록이다. 페이지·커서·총 건수·사용자 지정 정렬·추가 필터를 제공하지 않는다.
