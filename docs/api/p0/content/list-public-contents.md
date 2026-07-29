# 공개 콘텐츠 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-02`, `CON-03`, `SES-01`, `SES-02` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

공개 지역에서 탐색 가능한 콘텐츠를 조회한다. `PUBLISHED`이고 소프트 삭제되지 않은 콘텐츠만 반환하며, 예약 가능 여부는
현재 회차와 잔여 정원을 기준으로 계산한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-02` | `GET /contents` | `region`, `content`, `content_session`, `image_object` |
| `CON-03` | `GET /contents` | `content.status`, `content.publish_at`, `content_log` |
| `SES-02` | `GET /contents` | `content_session.status`, `content_session.remaining_capacity` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/contents`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 인증 없이 호출하는 공개 API다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 콘텐츠 배열을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0 단순 목록으로 페이지네이션을 적용하지 않는다. |

## 3. 공개 콘텐츠 목록 조회

목록은 실제 공개 시각 내림차순, 같은 시각이면 콘텐츠 식별자 내림차순으로 고정 정렬한다. 유형·예약 가능 여부 필터는
동시에 적용하며 사용자 지정 정렬, 검색 및 페이지 파라미터는 제공하지 않는다.

### Request

```http
GET /api/v1/contents
```

#### Request Example

```http
GET /api/v1/contents?regionId=1&contentType=EVENT_EXPERIENCE&reservationAvailable=true HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 인증이 필요하지 않은 공개 API다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- |
| `regionId` | Long | Y | 공개 콘텐츠를 조회할 지역 식별자다. 양수여야 한다. |
| `contentType` | String | N | 콘텐츠 유형 필터다. P0에서는 지정하면 `EVENT_EXPERIENCE`만 허용한다. |
| `reservationAvailable` | Boolean | N | `true`이면 예약 가능한 향후 회차가 있는 콘텐츠만, `false`이면 없는 콘텐츠만 반환한다. |

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
  "message": "공개 콘텐츠 목록 조회에 성공했습니다.",
  "data": {
    "contents": [
      {
        "contentId": 101,
        "contentType": "EVENT_EXPERIENCE",
        "title": "김해 가야문화 체험",
        "locationText": "김해시 가야의길 190",
        "representativeImageUrl": "https://s3.ap-northeast-2.amazonaws.com/example-bucket/contents/101/image?X-Amz-Signature=...",
        "representativeImageUrlExpiresAt": "2026-07-30T12:05:00+09:00",
        "reservationAvailable": true
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
| `data.contents` | Array | 조건에 맞는 공개 콘텐츠 배열이다. 결과가 없으면 `[]`다. |
| `data.contents[].contentId` | Number | 콘텐츠 식별자 |
| `data.contents[].contentType` | String | P0에서는 `EVENT_EXPERIENCE` |
| `data.contents[].title` | String | 콘텐츠 제목 |
| `data.contents[].locationText` | String | 위치 안내 |
| `data.contents[].representativeImageUrl` | String | 현재 대표 이미지의 단기 presigned GET URL |
| `data.contents[].representativeImageUrlExpiresAt` | String | 대표 이미지 조회 URL의 만료 시각 |
| `data.contents[].reservationAvailable` | Boolean | 예약 가능한 향후 회차가 하나 이상 있는지 여부 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `regionId`가 양수가 아니거나 `contentType`, `reservationAvailable` 값이 허용 범위를 벗어났다. 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 쿼리 값을 선언된 타입으로 변환할 수 없다. 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 지역이 없거나 공개 지역이 아니다. 비공개 지역의 존재 여부는 노출하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 콘텐츠·회차·대표 이미지 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. |

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

1. `regionId`의 지역이 공개 상태이고, 콘텐츠의 `region_id`가 같으며 `status = PUBLISHED`, `deleted_at IS NULL`인 행만 대상이다.
2. `reservationAvailable`은 `SCHEDULED`, 현재 시각 이후, `remaining_capacity > 0`인 회차가 하나 이상이면 `true`다. 현재 시각과 회차 상태는 MySQL을 기준으로 계산한다.
3. 서버는 현재 공개 상태와 대표 이미지 객체가 `ACTIVE`인지 확인한 뒤 URL과 정확한 만료 시각을 발급한다. URL·만료 시각·S3 객체 키는 저장하거나 캐시하지 않는다.
4. 이미지 객체 키, 원본 파일명, `imageObjectId`, 운영자 식별정보는 응답에 노출하지 않는다.
5. 조회는 콘텐츠, 회차, 이미지, 상태 로그와 감사 기록을 변경하지 않는다.
