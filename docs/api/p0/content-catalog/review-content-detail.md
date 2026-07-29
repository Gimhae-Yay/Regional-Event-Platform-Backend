# 승인 검토 콘텐츠 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-03 콘텐츠·회차 등록](../../../p0/content-catalog.md#fr-03-콘텐츠회차-등록), [FR-04 승인·자동 공개·종료](../../../p0/content-catalog.md#fr-04-승인자동-공개종료), `CON-02`, `CON-03` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 승인·반려 전에 콘텐츠의 필수 정보와 회차를 확인하는 API다. 응답은 운영자가 등록한 콘텐츠 현재 스냅샷과
회차를 제공하며, 승인 또는 반려 권한을 부여하지 않는 조회 API다. 이 API는 `PENDING` 콘텐츠만 반환한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-03, FR-04, CON-02, CON-03 | `GET /api/v1/region-admin/contents/{contentId}` | `content`, `content_session`, `content_representative_image` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 `application/json; charset=UTF-8`을 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 담당 지역의 `REGION_ADMIN` 역할만 허용하며, 콘텐츠의 `region_id`와 인증 주체의 담당 지역이 같아야 한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 공통 네 필드를 사용하며 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 회차는 콘텐츠 상세의 구성 정보로 전체 반환하며 페이지네이션을 적용하지 않는다. |

## 3. 승인 검토할 콘텐츠 상세 조회

서버는 소프트 삭제되지 않은 `PENDING` 콘텐츠를 조회하고 콘텐츠의 대표 이미지 객체 식별자와 모든 회차를 함께 반환한다.
대표 이미지의 접근 URL 발급은 이미지 도메인 API에서 별도로 정의한다.

### Request

```http
GET /api/v1/region-admin/contents/{contentId}
```

#### Request Example

```http
GET /api/v1/region-admin/contents/123 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 담당 지역 관리자 인증에 사용한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | Long | Y | 승인 검토할 콘텐츠 식별자. 양수여야 한다. |

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
  "message": "승인 검토 콘텐츠 상세 조회에 성공했습니다.",
  "data": {
    "contentId": 123,
    "regionId": 1,
    "operatorId": 41,
    "contentType": "EVENT_EXPERIENCE",
    "status": "PENDING",
    "title": "가야 문화 체험",
    "description": "가야 문화를 체험하는 행사입니다.",
    "representativeImageObjectId": 501,
    "locationText": "김해문화의전당",
    "operatingHoursText": "매주 토요일 10:00~16:00",
    "contactText": "055-000-0000",
    "precautions": "편한 복장으로 참여해 주세요.",
    "ageRequirement": "초등학생 이상",
    "materials": "필기도구",
    "cancellationPolicyText": "회차 시작 전까지 예약 전체 취소가 가능합니다.",
    "publishAt": "{ISO 8601 형식과 기준 시간대}",
    "sessions": [
      {
        "sessionId": 701,
        "status": "SCHEDULED",
        "startsAt": "{ISO 8601 형식과 기준 시간대}",
        "endsAt": "{ISO 8601 형식과 기준 시간대}",
        "checkinOpenAt": "{ISO 8601 형식과 기준 시간대}",
        "checkinCloseAt": "{ISO 8601 형식과 기준 시간대}",
        "capacity": 20,
        "remainingCapacity": 20
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `승인 검토 콘텐츠 상세 조회에 성공했습니다.`다. |
| `data.contentId` | Long | 콘텐츠 식별자다. |
| `data.regionId` | Long | 콘텐츠가 속한 지역 식별자다. |
| `data.operatorId` | Long | 콘텐츠를 소유한 운영자 식별자다. |
| `data.contentType` | String | P0 콘텐츠 유형 `EVENT_EXPERIENCE`다. |
| `data.status` | String | 승인 검토 대상 상태 `PENDING`이다. |
| `data.title` | String | 콘텐츠 제목이다. |
| `data.description` | String | 콘텐츠 소개다. |
| `data.representativeImageObjectId` | Long | 현재 대표 이미지 객체 식별자다. 이미지 접근 URL은 별도 이미지 API 계약을 따른다. |
| `data.locationText` | String | 운영자가 등록한 위치 표시 문자열이다. |
| `data.operatingHoursText` | String | 운영자가 등록한 운영 시간 표시 문자열이다. |
| `data.contactText` | String | 운영자가 등록한 연락처 표시 문자열이다. |
| `data.precautions` | String | 운영자가 등록한 유의사항이다. |
| `data.ageRequirement` | String | 운영자가 등록한 연령 조건이다. |
| `data.materials` | String | 운영자가 등록한 준비물이다. |
| `data.cancellationPolicyText` | String | P0 무료 예약 취소 정책 안내 문구다. |
| `data.publishAt` | String | 운영자가 지정한 공개 예정 시각이다. 시간 형식은 API 공통 규칙의 확정 값을 따른다. |
| `data.sessions` | Array&lt;Object&gt; | 콘텐츠에 연결된 전체 회차다. 빈 배열은 허용하지 않으며, PENDING 콘텐츠는 유효한 회차를 하나 이상 가진다. |
| `data.sessions[].sessionId` | Long | 회차 식별자다. |
| `data.sessions[].status` | String | 회차 운영 상태다. 승인 검토 대상에서는 `SCHEDULED`다. |
| `data.sessions[].startsAt` | String | 회차 시작 시각이다. |
| `data.sessions[].endsAt` | String | 회차 종료 시각이다. |
| `data.sessions[].checkinOpenAt` | String | 체크인 가능 시작 시각이다. |
| `data.sessions[].checkinCloseAt` | String | 체크인 가능 종료 시각이다. |
| `data.sessions[].capacity` | Integer | 회차 정원이다. 0보다 크다. |
| `data.sessions[].remainingCapacity` | Integer | 현재 잔여 정원이다. `0` 이상 `capacity` 이하다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- |
| 400 | `INVALID_INPUT` | `contentId`가 양수가 아니다. 데이터를 반환하지 않는다. |
| 400 | `INVALID_TYPE` | `contentId`를 Long으로 변환할 수 없다. 데이터를 반환하지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 데이터를 반환하지 않는다. |
| 403 | `FORBIDDEN` | 지역 관리자 역할이 없거나 콘텐츠의 담당 지역이 다르다. 데이터를 반환하지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠가 존재하지 않거나, 소프트 삭제됐거나, 승인 검토 대상인 `PENDING` 상태가 아니다. 데이터를 반환하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
