# 지역 미션 조기 종료 API 명세서

## 1. 개요

담당 지역 운영자가 공개 중인 미션을 조기 종료한다. 종료 후 신규 참여와 신규 진행도 반영은 중단되며 미완료 참여는
`ENDED_INCOMPLETE`로 보존한다.

### Request

```http
POST /api/v1/operator/missions/{missionId}/end
```

#### Request Example

```http
POST /api/v1/operator/missions/701/end HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reasonCode": "MISSION_OPERATION_SCHEDULE_CHANGED"
}
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 종료할 미션 식별자. 양수여야 한다. |

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reasonCode` | String | Y | 아래 허용 목록 중 하나. 최대 64자이며 운영 설명 원문이나 개인정보를 포함하지 않는다. |

#### 허용 조기 종료 사유 코드

| Code | 설명 |
| --- | --- |
| `MISSION_OPERATION_SCHEDULE_CHANGED` | 운영 일정 변경으로 예정 시각 전에 종료함 |
| `MISSION_TARGET_CONTENT_UNAVAILABLE` | 목표 콘텐츠를 더 이상 정상 운영할 수 없음 |
| `MISSION_REWARD_POLICY_UNAVAILABLE` | 완료 보상 정책을 더 이상 정상 운영할 수 없음 |
| `MISSION_OPERATION_SAFETY_CONCERN` | 안전 문제로 미션 운영을 계속할 수 없음 |

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
  "message": "미션 종료에 성공했습니다.",
  "data": {
    "missionId": "701",
    "status": "ENDED",
    "endedAt": "2026-08-07T04:40:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 종료 성공 메시지 |
| `data.missionId` | String | 조기 종료한 미션 식별자 |
| `data.status` | String | 종료 뒤 상태인 `ENDED` |
| `data.endedAt` | String | 실제 종료 처리 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않거나 `reasonCode`가 허용 목록에 없다. 상태를 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 요청 값을 선언된 타입으로 변환할 수 없다. 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상태를 변경하지 않는다. |
| `403` | `FORBIDDEN` | 담당 지역의 `OPERATOR` 역할이 없거나 미션 지역이 다르다. 상태를 변경하지 않는다. |
| `404` | `NOT_FOUND` | 미션을 찾을 수 없다. 상태를 변경하지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 미션이 `PUBLISHED`가 아니다. 상태를 변경하지 않는다. |

### 처리 규칙

1. 미션 행을 `PESSIMISTIC_WRITE`로 먼저 잠그고 잠금 획득 뒤 `PUBLISHED` 상태를 다시 확인한다.
2. `IN_PROGRESS` 참여 행을 `mission_participation_id` 오름차순으로 잠근 뒤 `ENDED_INCOMPLETE`로 전이한다.
   `COMPLETED`와 이미 `ENDED_INCOMPLETE`인 참여 상태는 변경하지 않지만, `COMPLETED`의 미수령 보상 권리는
   미션 종료와 동시에 만료된다.
3. 진행도 반영은 같은 미션 우선 잠금 순서를 사용하므로 종료와 경합해도 먼저 미션 잠금을 얻은 트랜잭션의 상태
   재검증 결과만 커밋한다.
4. 종료 전에 생성된 기존 보상 수령 결과는 멱등 재조회할 수 있지만 새 수령과 수동 지급은 허용하지 않는다.
5. 미션 종료, 참여 상태 정리와 `PUBLISHED → ENDED`, 요청의 `reasonCode`를 포함한 감사 이벤트를
   같은 트랜잭션으로 기록한다.
