# 내 미션 참여 목록 조회 API 명세서

## 1. 개요

방문자가 본인의 미션 참여 목록과 진행 요약을 조회한다.

### Request

```http
GET /api/v1/me/mission-participations
```

#### Request Example

```http
GET /api/v1/me/mission-participations?status=IN_PROGRESS&page=0&size=20 HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | N | `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나 |
| `page` | Integer | N | 0부터 시작하는 페이지 번호. 기본값 `0`, 음수 불가 |
| `size` | Integer | N | 페이지 크기. 기본값 `20`, 허용 범위 `1~100` |

사용자 지정 정렬은 제공하지 않는다. `joinedAt` 내림차순, 같은 참여 시각이면 `participationId` 내림차순으로 고정한다.

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
  "message": "내 미션 참여 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "participationId": "9001",
        "missionId": "701",
        "status": "IN_PROGRESS",
        "progressCount": 1,
        "requiredCount": 3,
        "rewardClaimed": false,
        "joinedAt": "2026-08-07T05:00:00Z",
        "completedAt": null
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
| `message` | String | 내 미션 참여 목록 조회 성공 메시지 |
| `data.content` | Array | 참여 목록. 없으면 빈 배열이며 `null`이 아님 |
| `data.content[].participationId` | String | 참여 식별자 |
| `data.content[].missionId` | String | 참여한 미션 식별자 |
| `data.content[].status` | String | `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나 |
| `data.content[].progressCount` | Integer | `VISIT_COUNT`는 반영된 서로 다른 유효 방문 수, `CONTENT_SET`은 최초 방문이 반영된 서로 다른 목표 콘텐츠 수. 0 이상 |
| `data.content[].requiredCount` | Integer | 완료에 필요한 방문 수. 1 이상 |
| `data.content[].rewardClaimed` | Boolean | 완료 보상 수령 여부 |
| `data.content[].joinedAt` | String | 최초 참여 시각. UTC ISO 8601 `Z` 형식 |
| `data.content[].completedAt` | String 또는 null | 완료 시각. `COMPLETED`가 아니면 `null` |
| `data.page` | Integer | 0부터 시작하는 현재 페이지 번호 |
| `data.size` | Integer | 요청에 적용된 페이지 크기 |
| `data.totalElements` | Long | 조건에 맞는 전체 참여 수 |
| `data.totalPages` | Integer | 전체 페이지 수. 결과가 없으면 `0` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 쿼리 파라미터가 유효하지 않다. |
| `400` | `INVALID_TYPE` | 쿼리 파라미터를 선언된 타입으로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 활성 방문자가 아니다. |

### 처리 규칙

1. 인증 사용자 본인의 참여만 반환한다.
2. 빈 결과는 `200 OK`, 빈 `content` 배열, `totalElements = 0`, `totalPages = 0`으로 반환한다.
