# 미션 검토 요청 API 명세서

## 1. 개요

담당 지역 운영자가 `DRAFT` 미션을 지역 관리자 검토 대상으로 제출한다. 제출 성공 시 상태는 `PENDING_REVIEW`가 된다.

### Request

```http
POST /api/v1/operator/missions/{missionId}/submit
```

#### Request Example

```http
POST /api/v1/operator/missions/701/submit HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 검토 요청할 미션 식별자. 양수여야 한다. |

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
  "message": "미션 검토 요청에 성공했습니다.",
  "data": {
    "missionId": "701",
    "status": "PENDING_REVIEW"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 검토 요청 성공 메시지 |
| `data.missionId` | String | 검토 요청한 미션 식별자 |
| `data.status` | String | 제출 뒤 상태인 `PENDING_REVIEW` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않거나 공개 조건·보상 정의가 완성되지 않았다. 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `missionId`를 식별자로 변환할 수 없다. 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 담당 지역의 `OPERATOR` 역할이 없거나 미션과 보상 정책의 지역이 다르다. 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 미션 또는 보상 쿠폰 정책을 찾을 수 없다. 상태를 변경하지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 잠금 뒤 미션이 `DRAFT`가 아니거나 잠근 보상 정책과 미션의 현재 보상 정책 연결이 다르거나, 보상 정책이 `ENDED` 또는 `MISSION_REWARD`가 아닌 발급 경로다. 상태를 변경하지 않는다. |

### 처리 규칙

1. 최초 조회한 미션의 보상 쿠폰 정책 행을 먼저 잠그고 미션 행을 잠근다.
2. 미션 잠금 획득 뒤 현재 `rewardCouponPolicyId`가 잠근 정책 식별자와 같은지 다시 확인한다. 동시 수정으로 연결이
   달라졌으면 다른 정책을 추가로 잠그지 않고 `409 MISSION_STATE_CONFLICT`로 종료한다.
3. 현재 상태가 `DRAFT`이고 공개 조건과 보상 정의가 확정됐는지 검증한다. 보상 정책은 미션과 같은 지역,
   `issuance_type = MISSION_REWARD`, 상태가 `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`
   중 하나여야 한다. 제출 뒤 정책 상태가 바뀌면 승인 API에서 다시 검증한다.
4. 성공 시 미션 상태와 `DRAFT → PENDING_REVIEW`, `reason_code = MISSION_SUBMITTED`인 감사 이벤트를
   같은 트랜잭션으로 기록한다.
