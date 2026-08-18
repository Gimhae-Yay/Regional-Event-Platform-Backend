# 지역별 공개 미션 목록 조회 API 명세서

## 1. 개요

방문자가 특정 지역의 공개 중인 미션 목록을 조회한다. `PUBLISHED`이고 `endsAt` 전인 미션만 노출한다.

### Request

```http
GET /api/v1/regions/{regionId}/missions
```

#### Request Example

```http
GET /api/v1/regions/11/missions?page=0&size=20 HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 로그인 사용자는 본인 참여 상태를 함께 받을 수 있다. 비로그인 조회도 허용한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `regionId` | String | Y | 조회할 지역 식별자. 양수여야 한다. |

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `page` | Integer | N | 0부터 시작하는 페이지 번호. 기본값 `0`, 음수 불가 |
| `size` | Integer | N | 페이지 크기. 기본값 `20`, 허용 범위 `1~100` |

사용자 지정 정렬은 제공하지 않는다. `endsAt` 오름차순, 같은 종료 시각이면 `missionId` 오름차순으로 고정한다.

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
  "message": "공개 미션 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "missionId": "701",
        "regionId": "11",
        "title": "김해 역사 탐방 미션",
        "conditionType": "CONTENT_SET",
        "requiredVisitCount": null,
        "targetContentCount": 3,
        "endsAt": "2026-09-30T23:59:59+09:00",
        "participationStatus": "IN_PROGRESS"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 공개 미션 목록 조회 성공 메시지 |
| `data.content` | Array | 공개 중이고 종료 전인 미션 목록. 없으면 빈 배열이며 `null`이 아님 |
| `data.content[].missionId` | String | 미션 식별자 |
| `data.content[].regionId` | String | 미션 운영 지역 식별자 |
| `data.content[].title` | String | 미션에 저장된 Unicode code point 기준 1~255자 방문자 표시 제목 |
| `data.content[].conditionType` | String | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `data.content[].requiredVisitCount` | Integer 또는 null | `VISIT_COUNT` 목표 횟수. `CONTENT_SET`이면 `null` |
| `data.content[].targetContentCount` | Integer | `CONTENT_SET` 대상 콘텐츠 수. `VISIT_COUNT`이면 `0` |
| `data.content[].endsAt` | String | 예정 종료 시각. ISO 8601 `+09:00` 오프셋 형식 |
| `data.content[].participationStatus` | String 또는 null | 인증 사용자의 `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 참여 상태. 비로그인이거나 참여하지 않았으면 `null` |
| `data.page` | Integer | 0부터 시작하는 현재 페이지 번호 |
| `data.size` | Integer | 요청에 적용된 페이지 크기 |
| `data.totalElements` | Long | 조건에 맞는 전체 공개 미션 수 |
| `data.totalPages` | Integer | 전체 페이지 수. 결과가 없으면 `0` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `regionId` 또는 쿼리 파라미터가 유효하지 않다. |
| `400` | `INVALID_TYPE` | 요청 값을 선언된 타입으로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | 선택적으로 전달한 Access Token이 유효하지 않다. |
| `404` | `NOT_FOUND` | 지역을 찾을 수 없거나 비공개 지역이다. 비공개 지역의 존재 여부를 노출하지 않는다. |

### 처리 규칙

1. `region.is_public = true`인 공개·운영 지역만 조회한다. 존재하지 않거나 비공개인 지역은 동일하게 `404 NOT_FOUND`로 처리한다.
2. 공개 지역에서 `PUBLISHED`이고 `endsAt` 전인 미션만 반환한다.
3. 각 미션이 직접 소유한 현재 `title`을 반환한다. 제목은 공개 뒤 수정되지 않으며 대상 콘텐츠 제목에서 파생하지 않는다.
4. 인증 사용자의 참여가 있으면 `participationStatus`를 반환하고, 없거나 비로그인이면 `null`을 반환한다.
5. `Authorization` 헤더가 없으면 익명 조회로 처리하고, 헤더가 있으면 유효한 Access Token만 허용한다.
6. 빈 결과는 `200 OK`, 빈 `content` 배열, `totalElements = 0`, `totalPages = 0`으로 반환한다.
