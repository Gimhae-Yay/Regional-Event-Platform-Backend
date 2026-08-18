# 내 미션 참여·진행도 상세 조회 API 명세서

## 1. 개요

방문자가 본인의 특정 미션 참여 상세와 방문 근거 기반 진행도를 조회한다.

### Request

```http
GET /api/v1/me/mission-participations/{participationId}
```

#### Request Example

```http
GET /api/v1/me/mission-participations/9001 HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `participationId` | String | Y | 조회할 참여 식별자. 양수여야 한다. |

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
  "message": "내 미션 참여 상세 조회에 성공했습니다.",
  "data": {
    "participationId": "9001",
    "missionId": "701",
    "title": "김해 역사 탐방 미션",
    "status": "IN_PROGRESS",
    "conditionType": "CONTENT_SET",
    "progressCount": 1,
    "requiredCount": 3,
    "rewardClaimed": false,
    "joinedAt": "2026-08-07T05:00:00Z",
    "completedAt": null,
    "progresses": [
      {
        "visitId": "3001",
        "contentId": "101",
        "contentTitle": "가야문화 체험",
        "recordedAt": "2026-08-08T03:00:00Z"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 내 미션 참여 상세 조회 성공 메시지 |
| `data.participationId` | String | 참여 식별자 |
| `data.missionId` | String | 참여한 미션 식별자 |
| `data.title` | String | 참여한 미션에 저장된 Unicode code point 기준 1~255자 방문자 표시 제목 |
| `data.status` | String | `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나 |
| `data.conditionType` | String | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `data.progressCount` | Integer | `VISIT_COUNT`는 반영된 서로 다른 유효 방문 수, `CONTENT_SET`은 최초 방문이 반영된 서로 다른 목표 콘텐츠 수. 0 이상 |
| `data.requiredCount` | Integer | 완료에 필요한 방문 수. 1 이상 |
| `data.rewardClaimed` | Boolean | 완료 보상 수령 여부 |
| `data.joinedAt` | String | 최초 참여 시각. UTC ISO 8601 `Z` 형식 |
| `data.completedAt` | String 또는 null | 완료 시각. `COMPLETED`가 아니면 `null` |
| `data.progresses` | Array | 진행도를 실제로 증가시킨 방문 근거 목록. `CONTENT_SET`의 같은 콘텐츠 재방문은 포함하지 않는다. 결과가 없으면 빈 배열이며 `null`이 아님 |
| `data.progresses[].visitId` | String | 진행도에 반영된 방문 식별자 |
| `data.progresses[].contentId` | String | 방문 콘텐츠 식별자 |
| `data.progresses[].contentTitle` | String | 방문 콘텐츠 제목 |
| `data.progresses[].recordedAt` | String | 진행도 반영 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `participationId`가 유효하지 않다. |
| `400` | `INVALID_TYPE` | `participationId`를 식별자로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_VISITOR` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 본인 참여가 아니다. |
| `404` | `NOT_FOUND` | 참여를 찾을 수 없다. |

### 처리 규칙

1. Access Token의 `ROLE_VISITOR` authority를 1차로 확인하고, DB에서 활성 `ORDINARY` 계정과 인증 사용자 본인의 참여 소유권을 확인한다.
2. 참여가 참조하는 미션의 현재 `title`을 반환한다. 제목은 공개 뒤 수정되지 않으며 대상 콘텐츠 제목에서 파생하지 않는다.
3. 진행도는 `mission_progress`의 방문 근거로 재현 가능한 값만 반환한다. `CONTENT_SET`은 목표 콘텐츠마다 최초 근거만,
   `VISIT_COUNT`는 서로 다른 `visitId`의 근거를 각각 반환한다.
