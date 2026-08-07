# 미션 참여 API 명세서

## 1. 개요

방문자가 공개 중이고 종료 전인 미션에 참여한다. 같은 사용자와 같은 미션의 반복 참여 요청은 기존 참여 결과로 수렴한다.

### Request

```http
POST /api/v1/missions/{missionId}/participations
```

#### Request Example

```http
POST /api/v1/missions/701/participations HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 활성 방문자 인증에 사용한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 참여할 미션 식별자. 양수여야 한다. |

#### Request Body

없음.

### Response

#### Status

```http
201 Created
```

반복 참여 요청도 기존 참여 결과를 `201 Created`로 반환한다.

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "미션 참여에 성공했습니다.",
  "data": {
    "participationId": "9001",
    "missionId": "701",
    "status": "IN_PROGRESS",
    "joinedAt": "2026-08-07T05:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `201` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 참여 성공 메시지 |
| `data.participationId` | String | 생성되었거나 기존에 존재한 참여 식별자 |
| `data.missionId` | String | 참여한 미션 식별자 |
| `data.status` | String | 참여 상태. 신규 참여는 `IN_PROGRESS`, 기존 결과는 저장된 `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나 |
| `data.joinedAt` | String | 최초 참여 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않다. 참여를 만들지 않는다. |
| `400` | `INVALID_TYPE` | `missionId`를 식별자로 변환할 수 없다. 참여를 만들지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 참여를 만들지 않는다. |
| `403` | `FORBIDDEN` | 활성 방문자가 아니다. 참여를 만들지 않는다. |
| `404` | `NOT_FOUND` | 미션을 찾을 수 없거나 미션의 지역이 비공개다. 비공개 지역의 존재 여부를 노출하지 않고 참여를 만들지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 기존 참여가 없는 신규 요청에서 미션이 `PUBLISHED`가 아니거나 종료 시각이 지났다. 참여를 만들지 않는다. |

### 처리 규칙

1. 인증·경로 식별자를 검증하고 미션과 지역을 조회한다. 미션이 없거나 `region.is_public = false`이면 동일하게
   `404 NOT_FOUND`로 처리하며 기존 참여도 이 API에서 반환하지 않는다.
2. 공개·운영 지역의 미션에 대해서만 `(mission_id, user_id)`의 기존 참여를 먼저 조회한다.
3. 기존 참여가 있으면 미션의 현재 상태나 종료 시각이 이후에 바뀌었더라도 저장된 참여 결과를 `201 Created`로 반환한다.
4. 기존 참여가 없으면 미션 행을 먼저 잠그고 이어서 해당 지역 행을 잠근다. 모든 잠금을 획득한 직후 DB 현재 시각을
   한 번만 읽어 `operationAt`으로 고정하고, `region.is_public = true`와
   `status = PUBLISHED AND endsAt > operationAt`을 다시 검증한 뒤 `joinedAt = operationAt`인 신규 참여를 생성한다.
5. `UNIQUE (mission_id, user_id)`로 사용자·미션당 참여를 하나만 허용한다.
6. 동시 요청의 중복 키 충돌은 선행 트랜잭션 커밋 뒤 지역 공개 여부를 다시 확인하고, 여전히 공개 지역이면 기존 참여를
   반환한다. 지역이 비공개로 바뀌었으면 `404 NOT_FOUND`로 처리하며 새 진행도나 보상을 만들지 않는다.
