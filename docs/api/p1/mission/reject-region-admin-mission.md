# 미션 반려 API 명세서

## 1. 개요

담당 지역 관리자가 `PENDING_REVIEW` 미션을 반려하고 `DRAFT`로 되돌린다. 비개인 반려 사유 코드는 감사 이력에 남긴다.

### Request

```http
POST /api/v1/region-admin/missions/{missionId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/missions/701/reject HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reasonCode": "MISSION_REWARD_POLICY_INVALID"
}
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 반려할 미션 식별자. 양수여야 한다. |

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reasonCode` | String | Y | 아래 허용 목록 중 하나. 최대 64자이며 운영 설명 원문이나 개인정보를 포함하지 않는다. |

#### 허용 반려 사유 코드

| Code | 설명 |
| --- | --- |
| `MISSION_INFORMATION_INCOMPLETE` | 공개 검토에 필요한 미션 정보가 완성되지 않음 |
| `MISSION_CONDITION_INVALID` | 완료 조건과 목표 횟수 구성이 운영 기준에 맞지 않음 |
| `MISSION_TARGET_CONTENT_INVALID` | 목표 콘텐츠 구성이 운영 기준에 맞지 않음 |
| `MISSION_REWARD_POLICY_INVALID` | 완료 보상 정책 구성이 운영 기준에 맞지 않음 |
| `MISSION_SCHEDULE_INVALID` | 종료 일정이 운영 기준에 맞지 않음 |

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
  "message": "미션 반려에 성공했습니다.",
  "data": {
    "missionId": "701",
    "status": "DRAFT",
    "rejectedAt": "2026-08-07T04:35:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 반려 성공 메시지 |
| `data.missionId` | String | 반려한 미션 식별자 |
| `data.status` | String | 반려 뒤 상태인 `DRAFT` |
| `data.rejectedAt` | String | 반려 처리 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않거나 `reasonCode`가 허용 목록에 없다. 상태를 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 요청 값을 선언된 타입으로 변환할 수 없다. 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 현재 담당 지역과 미션 지역이 다르다. 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 미션을 찾을 수 없다. 상태를 변경하지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 미션이 `PENDING_REVIEW`가 아니다. 상태를 변경하지 않는다. |

### 처리 규칙

1. 미션 행을 잠근 뒤 담당 지역과 현재 상태를 다시 확인한다.
2. 잠금 획득 뒤 상태가 `PENDING_REVIEW`인 경우에만 `DRAFT`로 전이한다. 상태가 달라졌으면 변경하지 않고
   `409 MISSION_STATE_CONFLICT`를 반환한다.
3. 미션의 `PENDING_REVIEW → DRAFT` 전이와 요청의 `reasonCode`, 처리자, 처리 시각을 포함한 성공 감사 이벤트를
   같은 트랜잭션으로 기록하며, 하나라도 실패하면 함께 롤백한다.
