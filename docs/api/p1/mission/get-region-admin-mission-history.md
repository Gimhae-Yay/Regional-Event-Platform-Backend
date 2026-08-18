# 미션 상태/운영 이력 조회 API 명세서

## 1. 개요

지역 관리자가 담당 지역 미션의 생성·수정·검토 요청·승인·반려·수동 종료·자동 종료 감사 이력을 조회한다.
조회 범위는 DB 현재 시각을 기준으로 최근 90일이며, 보관 기간이 지난 이력은 응답하지 않는다.

### Request

```http
GET /api/v1/region-admin/missions/{missionId}/history
```

#### Request Example

```http
GET /api/v1/region-admin/missions/701/history HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 이력을 조회할 미션 식별자. 양수여야 한다. |

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
  "message": "미션 이력 조회에 성공했습니다.",
  "data": {
    "missionId": "701",
    "histories": [
      {
        "auditEventId": "12001",
        "action": "SUBMITTED",
        "previousStatus": "DRAFT",
        "nextStatus": "PENDING_REVIEW",
        "result": "SUCCESS",
        "reasonCode": "MISSION_SUBMITTED",
        "actorKind": "USER",
        "actorUserId": "31",
        "recordedAt": "2026-08-07T04:20:00Z"
      },
      {
        "auditEventId": "12002",
        "action": "REJECTED",
        "previousStatus": "PENDING_REVIEW",
        "nextStatus": "DRAFT",
        "result": "SUCCESS",
        "reasonCode": "MISSION_REWARD_POLICY_INVALID",
        "actorKind": "USER",
        "actorUserId": "41",
        "recordedAt": "2026-08-07T04:35:00Z"
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
| `message` | String | 미션 이력 조회 성공 메시지 |
| `data.missionId` | String | 이력 조회 대상 미션 식별자 |
| `data.histories` | Array | 최근 90일의 성공한 수명주기 이력. 없으면 빈 배열이며 `null`이 아님 |
| `data.histories[].auditEventId` | String | 감사 이벤트 식별자 |
| `data.histories[].action` | String | `CREATED`, `UPDATED`, `SUBMITTED`, `APPROVED`, `REJECTED`, `ENDED`, `AUTO_ENDED` 중 하나 |
| `data.histories[].previousStatus` | String 또는 null | 전이 전 상태. `CREATED`이면 `null` |
| `data.histories[].nextStatus` | String | 전이 뒤 상태 |
| `data.histories[].result` | String | 이 API가 반환하는 이력은 `SUCCESS` |
| `data.histories[].reasonCode` | String | 아래 매핑에 따른 고정 코드 또는 요청에서 받은 비개인 사유 코드 |
| `data.histories[].actorKind` | String | `USER`, `SYSTEM`, `WITHDRAWN_MEMBER` 중 하나 |
| `data.histories[].actorUserId` | String 또는 null | 활성 `USER` 처리자 식별자. `SYSTEM` 또는 `WITHDRAWN_MEMBER`이면 `null` |
| `data.histories[].recordedAt` | String | 감사 이벤트 처리 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId`가 유효하지 않다. |
| `400` | `INVALID_TYPE` | `missionId`를 식별자로 변환할 수 없다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정이 아니거나 현재 담당 지역과 미션 지역이 다르다. |
| `404` | `NOT_FOUND` | 미션을 찾을 수 없다. |
| `500` | `INTERNAL_SERVER_ERROR` | 성공 감사 이벤트가 아래 `action` 매핑과 일치하지 않아 완전한 이력을 만들 수 없다. 일부 이력을 반환하지 않는다. |

### 처리 규칙

1. Access Token의 `ROLE_REGION_ADMIN` authority를 1차로 확인하고, DB에서 활성 `ORDINARY` 계정의 현재 담당 지역 미션 이력만 조회할 수 있다.
2. 이력은 `target_type = MISSION AND target_id = missionId`, `result = SUCCESS`,
   `occurred_at >= DB 현재 시각 - 90일`인 `audit_event`와 탈퇴 전 활성 처리자의
   `audit_event_actor_link`를 기준으로 조회한다. 정리 작업이 아직 삭제하지 않은 90일 초과 행도 반환하지 않는다.
3. 이 API는 최근 90일의 성공한 수명주기 이력만 제공한다. 실패 감사 이벤트와 90일을 초과한 이력은 반환하지 않으며,
   해당 기간에 이력이 없으면 `histories`는 빈 배열이다.
4. `action`은 아래 매핑 표를 순서대로 적용해 결정한다. 표와 일치하지 않는 성공 감사 이벤트가 하나라도 있으면
   일부 이력을 제외한 `200 OK`를 반환하지 않고 요청 전체를 `500 INTERNAL_SERVER_ERROR`로 실패 처리한다.
   구조화 로그에는 `requestId`, `missionId`, `auditEventId`와 비개인 정합성 오류 코드를 기록하되 감사 원문이나
   사용자 정보는 기록하지 않는다.
5. `reasonCode`는 감사 이벤트의 비개인 사유 코드이며 운영 설명 원문이나 개인정보를 포함하지 않는다.
6. 저장된 `audit_event.actor_kind = SYSTEM`이면 응답 `actorKind = SYSTEM`, `actorUserId = null`이다.
   저장된 `audit_event.actor_kind = USER`이고 활성 `audit_event_actor_link`가 있으면 `actorKind = USER`와 연결된
   `actorUserId`를 반환한다. 탈퇴 처리로 연결이 제거됐으면 모든 탈퇴 회원에 공통인
   `actorKind = WITHDRAWN_MEMBER`, `actorUserId = null`로 반환하며 사용자별 안정 식별자를 만들지 않는다.
7. 이력은 `recordedAt` 오름차순, 같은 시각이면 `auditEventId` 오름차순으로 반환한다.

### action·상태 전이·reasonCode 매핑

| `action` | `previousStatus` | `nextStatus` | 저장된 `audit_event.actor_kind` | `reasonCode` | 설명 |
| --- | --- | --- | --- | --- | --- |
| `CREATED` | `null` | `DRAFT` | `USER` | `MISSION_CREATED` | 운영자가 미션을 최초 생성한 성공 이벤트 |
| `UPDATED` | `DRAFT` | `DRAFT` | `USER` | `MISSION_UPDATED` | 운영자가 수정 가능한 핵심 값을 교체한 성공 이벤트 |
| `SUBMITTED` | `DRAFT` | `PENDING_REVIEW` | `USER` | `MISSION_SUBMITTED` | 운영자가 지역 관리자 검토를 요청한 성공 이벤트 |
| `APPROVED` | `PENDING_REVIEW` | `PUBLISHED` | `USER` | `MISSION_APPROVED` | 지역 관리자가 승인해 즉시 공개한 성공 이벤트 |
| `REJECTED` | `PENDING_REVIEW` | `DRAFT` | `USER` | [반려 API의 허용 `reasonCode`](reject-region-admin-mission.md#허용-반려-사유-코드) | 서버가 검증한 고정 반려 사유 코드를 반환 |
| `ENDED` | `PUBLISHED` | `ENDED` | `USER` | [조기 종료 API의 허용 `reasonCode`](end-operator-mission.md#허용-조기-종료-사유-코드) | 서버가 검증한 고정 조기 종료 사유 코드를 반환 |
| `AUTO_ENDED` | `PUBLISHED` | `ENDED` | `SYSTEM` | `MISSION_END_TIME_REACHED` | Scheduler가 종료 예정 시각 도달을 확인한 성공 이벤트 |

`CREATED`와 `UPDATED`는 상태 쌍만으로 구분할 수 없으므로 고정 `reasonCode`를 함께 사용한다. `ENDED`와
`AUTO_ENDED`는 같은 상태 쌍을 사용하므로 저장된 `audit_event.actor_kind`와 `MISSION_END_TIME_REACHED`를 함께 확인한다.
응답의 `WITHDRAWN_MEMBER`는 `action`을 결정한 뒤 탈퇴로 actor 연결이 제거된 `USER` 이벤트에 적용하는 표시값이므로
위 매핑의 `USER` 조건을 바꾸지 않는다.
