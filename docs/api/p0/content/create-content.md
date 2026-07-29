# 콘텐츠 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `FR-09`, `AUTH-01`, `CON-01`, `CON-02`, `CON-09` |
| 소유 도메인 | 콘텐츠 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 운영자가 완전한 콘텐츠 정보, 유효 회차와 본인 소유 임시 대표 이미지를 함께 등록한다. 서버는 인증된
운영자와 담당 지역을 연결하고, 검증을 통과한 콘텐츠를 즉시 `PENDING` 상태로 만들어 심사 대기 대상으로 둔다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03` | `POST /operator/contents` | `content`, `content_session`, `image_object`, `content_log` |
| `AUTH-01` | `POST /operator/contents` | 운영자 역할, 담당 지역, `content.operator_id`, `content.region_id` |
| `CON-01` | `POST /operator/contents` | `content.status`, `content_log.status` |
| `CON-02` | `POST /operator/contents` | 콘텐츠 필수 필드, 대표 이미지, 회차 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 | [API 공통 규칙](../../common/api-conventions.md) | 실제 경로는 `/api/v1/operator/contents`다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 승인된 `OPERATOR` 역할과 담당 지역이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 심사 요청 상태의 콘텐츠 식별자를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 콘텐츠 생성

### Request

```http
POST /api/v1/operator/contents
```

#### Request Example

```http
POST /api/v1/operator/contents HTTP/1.1
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
  "publishAt": "2026-08-15T09:00:00+09:00",
  "representativeImageObjectId": 301,
  "sessions": [
    {
      "startsAt": "2026-08-16T10:00:00+09:00",
      "endsAt": "2026-08-16T12:00:00+09:00",
      "checkinOpenAt": "2026-08-16T09:30:00+09:00",
      "checkinCloseAt": "2026-08-16T10:30:00+09:00",
      "capacity": 20
    }
  ]
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

요청 예시의 JSON 객체를 사용한다.

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
| `publishAt` | String | Y | ISO 8601 `+09:00` 오프셋 일시인 공개 예정 시각 |
| `representativeImageObjectId` | Long | Y | 본인 소유의 만료되지 않고 업로드가 검증된 `TEMPORARY` 이미지 객체 식별자 |
| `sessions` | Array | Y | 하나 이상의 생성할 회차 배열 |
| `sessions[].startsAt` | String | Y | ISO 8601 `+09:00` 오프셋 시작 시각 |
| `sessions[].endsAt` | String | Y | ISO 8601 `+09:00` 오프셋 종료 시각 |
| `sessions[].checkinOpenAt` | String | Y | ISO 8601 `+09:00` 오프셋 체크인 시작 시각 |
| `sessions[].checkinCloseAt` | String | Y | ISO 8601 `+09:00` 오프셋 체크인 종료 시각 |
| `sessions[].capacity` | Integer | Y | 양수인 회차 정원 |

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
  "message": "콘텐츠 생성과 승인 요청에 성공했습니다.",
  "data": {
    "contentId": 101,
    "contentType": "EVENT_EXPERIENCE",
    "status": "PENDING",
    "submittedAt": "2026-07-30T14:00:00+09:00"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.contentId` | Number | 생성한 콘텐츠 식별자 |
| `data.contentType` | String | P0에서 고정된 `EVENT_EXPERIENCE` |
| `data.status` | String | 생성 직후 심사 요청 상태 `PENDING` |
| `data.submittedAt` | String | 심사 요청 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 필수 콘텐츠 필드·회차가 유효하지 않거나, 회차의 체크인 종료 시각이 회차 종료 시각보다 이르거나, 대표 이미지 객체가 임시·업로드 검증·만료 조건을 만족하지 않는다. 콘텐츠·회차·로그를 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 콘텐츠·회차·로그를 생성하지 않는다. |
| `400` | `INVALID_TYPE` | 식별자·정원 또는 시각 값을 선언된 타입으로 변환할 수 없다. 콘텐츠·회차·로그를 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 콘텐츠·회차·로그를 생성하지 않는다. |
| `403` | `FORBIDDEN` | 승인된 운영자 역할·담당 지역이 없거나 임시 이미지 객체의 소유자가 아니다. 콘텐츠·회차·로그를 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 콘텐츠·회차·로그를 생성하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증된 승인 운영자를 `operator_id`로, 그 운영자의 담당 지역을 `region_id`로 설정한다. 요청에서 소유자·지역·콘텐츠 유형을 지정하거나 변경할 수 없다.
2. 모든 정적 콘텐츠 필드, 업로드가 검증된 현재 임시 대표 이미지 한 개와 하나 이상의 유효 회차를 검증한다. 각 회차는 `startsAt < endsAt`, `checkinOpenAt < checkinCloseAt`, `endsAt <= checkinCloseAt`, 양수 정원을 만족해야 한다.
3. 성공 시 콘텐츠와 회차를 만들고 임시 이미지 객체를 대표 이미지로 연결한 뒤, 콘텐츠를 `PENDING`으로 만들고 `PENDING` 로그를 같은 트랜잭션에서 기록한다. 연결된 이미지 객체에는 임시 만료를 적용하지 않는다.
4. 생성된 `PENDING` 콘텐츠는 심사 결과가 나올 때까지 직접 편집하거나 재요청할 수 없다.
