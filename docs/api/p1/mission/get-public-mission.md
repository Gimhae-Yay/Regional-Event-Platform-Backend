# 공개 미션 상세 조회 API 명세서

## 1. 개요

방문자가 공개 중인 미션의 상세 조건과 보상 정보를 조회한다. 공개 전·종료 후 미션은 공개 상세에서 조회하지 않는다.

### Request

```http
GET /api/v1/missions/{missionId}
```

#### Request Example

```http
GET /api/v1/missions/701 HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 로그인 사용자는 본인 참여 진행 요약을 함께 받을 수 있다. 비로그인 조회도 허용한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 조회할 미션 식별자. 양수여야 한다. |

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
  "message": "공개 미션 상세 조회에 성공했습니다.",
  "data": {
    "missionId": "701",
    "regionId": "11",
    "conditionType": "CONTENT_SET",
    "requiredVisitCount": null,
    "targetContents": [
      {
        "contentId": "101",
        "title": "가야문화 체험"
      }
    ],
    "rewardCouponPolicyId": "501",
    "endsAt": "2026-09-30T23:59:59+09:00",
    "participation": {
      "participationId": "9001",
      "status": "IN_PROGRESS",
      "progressCount": 1,
      "requiredCount": 3,
      "rewardClaimed": false
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 공개 미션 상세 조회 성공 메시지 |
| `data.missionId` | String | 미션 식별자 |
| `data.regionId` | String | 미션 운영 지역 식별자 |
| `data.conditionType` | String | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `data.requiredVisitCount` | Integer 또는 null | `VISIT_COUNT` 목표 횟수. `CONTENT_SET`이면 `null` |
| `data.targetContents` | Array | `CONTENT_SET` 대상 콘텐츠. `VISIT_COUNT`이면 빈 배열이며 `null`이 아님 |
| `data.targetContents[].contentId` | String | 대상 콘텐츠 식별자 |
| `data.targetContents[].title` | String | 대상 콘텐츠 제목 |
| `data.rewardCouponPolicyId` | String | 완료 보상 쿠폰 정책 식별자 |
| `data.endsAt` | String | 예정 종료 시각. ISO 8601 `+09:00` 오프셋 형식 |
| `data.participation` | Object 또는 null | 인증 사용자의 참여 요약. 비로그인이거나 참여하지 않았으면 `null` |
| `data.participation.participationId` | String | 참여 식별자 |
| `data.participation.status` | String | `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나 |
| `data.participation.progressCount` | Integer | 반영된 유효 방문 수. 0 이상 |
| `data.participation.requiredCount` | Integer | 완료에 필요한 방문 수. 1 이상 |
| `data.participation.rewardClaimed` | Boolean | 완료 보상 수령 여부 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않다. |
| `400` | `INVALID_TYPE` | `missionId`를 식별자로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | 선택적으로 전달한 Access Token이 유효하지 않다. |
| `404` | `NOT_FOUND` | 공개·운영 지역에 속하면서 공개 중이고 종료 전인 미션을 찾을 수 없다. 비공개 지역의 존재 여부를 노출하지 않는다. |

### 처리 규칙

1. 미션의 지역이 `region.is_public = true`이고 미션이 `PUBLISHED`이며 `endsAt` 전인 경우에만 공개 상세로 반환한다.
   존재하지 않는 미션, 비공개 지역의 미션과 공개 조건을 만족하지 않는 미션은 동일하게 `404 NOT_FOUND`로 처리한다.
2. 인증 사용자의 참여가 있으면 진행 요약을 포함하고, 없거나 비로그인이면 `participation`은 `null`이다.
3. `Authorization` 헤더가 없으면 익명 조회로 처리하고, 헤더가 있으면 유효한 Access Token만 허용한다.
