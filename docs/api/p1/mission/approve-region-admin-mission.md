# 미션 승인 API 명세서

## 1. 개요

담당 지역 관리자가 `PENDING_REVIEW` 미션을 승인해 공개한다. 승인 시 잠근 완료 보상 정책과 `CONTENT_SET` 목표
콘텐츠가 모두 `PUBLISHED`인지 확인하며, 미션은 공개 즉시 참여 가능해진다.

### Request

```http
POST /api/v1/region-admin/missions/{missionId}/approve
```

#### Request Example

```http
POST /api/v1/region-admin/missions/701/approve HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 승인할 미션 식별자. 양수여야 한다. |

#### Request Body

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
  "message": "미션 승인에 성공했습니다.",
  "data": {
    "missionId": "701",
    "status": "PUBLISHED",
    "publishedAt": "2026-08-07T04:30:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 승인 성공 메시지 |
| `data.missionId` | String | 승인한 미션 식별자 |
| `data.status` | String | 승인 뒤 상태인 `PUBLISHED` |
| `data.publishedAt` | String | 공개 승인 처리 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않거나 `endsAt`이 이미 지났다. 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `missionId`를 식별자로 변환할 수 없다. 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 담당 지역의 `REGION_ADMIN` 역할이 없거나 미션과 보상 정책의 지역이 다르다. 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 미션 또는 보상 쿠폰 정책을 찾을 수 없다. 상태를 변경하지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 잠금 뒤 지역이 비공개이거나 미션이 `PENDING_REVIEW`가 아니거나 잠근 보상 정책과 미션의 현재 보상 정책 연결이 다르거나, 보상 쿠폰 정책 또는 `CONTENT_SET` 목표 콘텐츠가 `PUBLISHED`가 아니다. 보상 정책의 발급 경로가 `MISSION_REWARD`가 아닌 경우도 포함하며 상태를 변경하지 않는다. |

### 처리 규칙

1. 최초 조회한 미션의 보상 쿠폰 정책 행을 먼저 잠그고 미션 행, 해당 지역 행 순서로 잠근다. `CONTENT_SET`이면
   그 뒤 모든 목표 콘텐츠 행을 `contentId` 오름차순으로 잠근다.
2. 미션 잠금 획득 뒤 현재 `rewardCouponPolicyId`가 잠근 정책 식별자와 같은지 다시 확인한다. 반려·수정·재제출과
   경합해 연결이 달라졌으면 다른 정책을 추가로 잠그지 않고 `409 MISSION_STATE_CONFLICT`로 종료한다.
3. 지역이 `is_public = true`이고 미션이 `PENDING_REVIEW`인지 다시 확인한다. 보상 정책은 미션과 같은 지역이며
   `status = PUBLISHED`, `issuance_type = MISSION_REWARD`인지 검증한다. 비공개 지역이면
   `409 MISSION_STATE_CONFLICT`로 종료한다.
4. `CONTENT_SET`이면 잠근 모든 목표 콘텐츠가 미션과 같은 지역이고 `deleted_at IS NULL`,
   `status = PUBLISHED`인지 재검증한다. 하나라도 불일치하면 `409 MISSION_STATE_CONFLICT`로 종료한다.
5. `publishedAt < endsAt`을 만족하는 경우에만 `PUBLISHED`로 전이한다.
6. 미션 상태와 `PENDING_REVIEW → PUBLISHED`, `reason_code = MISSION_APPROVED`인 감사 이벤트를
   같은 트랜잭션으로 기록한다.
