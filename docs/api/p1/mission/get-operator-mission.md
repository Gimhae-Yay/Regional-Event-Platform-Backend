# 내 미션 상세 조회 API 명세서

## 1. 개요

콘텐츠 운영자가 담당 지역의 미션 상세와 대상 콘텐츠, 운영 상태를 조회한다.

### Request

```http
GET /api/v1/operator/missions/{missionId}
```

#### Request Example

```http
GET /api/v1/operator/missions/701 HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

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
  "message": "내 미션 상세 조회에 성공했습니다.",
  "data": {
    "missionId": "701",
    "regionId": "11",
    "status": "DRAFT",
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
    "publishedAt": null,
    "endedAt": null
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 운영자 미션 상세 조회 성공 메시지 |
| `data.missionId` | String | 미션 식별자 |
| `data.regionId` | String | 미션 운영 지역 식별자 |
| `data.status` | String | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 중 하나 |
| `data.conditionType` | String | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `data.requiredVisitCount` | Integer 또는 null | `VISIT_COUNT` 목표 횟수. `CONTENT_SET`이면 `null` |
| `data.targetContents` | Array | `CONTENT_SET` 대상 콘텐츠. `VISIT_COUNT`이면 빈 배열이며 `null`이 아님 |
| `data.targetContents[].contentId` | String | 대상 콘텐츠 식별자 |
| `data.targetContents[].title` | String | 대상 콘텐츠 제목 |
| `data.rewardCouponPolicyId` | String | 완료 보상 쿠폰 정책 식별자 |
| `data.endsAt` | String | 예정 종료 시각. ISO 8601 `+09:00` 오프셋 형식 |
| `data.publishedAt` | String 또는 null | 공개 승인 처리 시각. 공개 전이면 `null` |
| `data.endedAt` | String 또는 null | 실제 종료 처리 시각. 종료 전이면 `null` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않다. |
| `400` | `INVALID_TYPE` | `missionId`를 식별자로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 담당 지역의 `OPERATOR` 역할이 없거나 미션 지역이 다르다. |
| `404` | `NOT_FOUND` | 미션을 찾을 수 없다. |

### 처리 규칙

인증 운영자의 담당 지역 미션만 조회할 수 있다.
