# 지역 미션 생성 API 명세서

## 1. 개요

담당 지역 운영자가 `DRAFT` 미션을 생성한다. 서버는 인증 주체의 담당 지역을 미션의 `region_id`로 사용하며 요청에서
지역을 지정하지 않는다. 완료 보상은 같은 지역의 `MISSION_REWARD` 쿠폰 정책이어야 한다.

### Request

```http
POST /api/v1/operator/missions
```

#### Request Example

```http
POST /api/v1/operator/missions HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "conditionType": "CONTENT_SET",
  "requiredVisitCount": null,
  "targetContentIds": ["101", "102", "103"],
  "rewardCouponPolicyId": "501",
  "endsAt": "2026-09-30T23:59:59+09:00"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 담당 지역의 `OPERATOR` 역할이 필요하다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `conditionType` | String | Y | `VISIT_COUNT` 또는 `CONTENT_SET` |
| `requiredVisitCount` | Integer | 조건부 | `VISIT_COUNT`이면 양수, `CONTENT_SET`이면 `null` |
| `targetContentIds` | Array | 조건부 | `CONTENT_SET`이면 중복 없는 하나 이상의 담당 지역 콘텐츠 식별자, `VISIT_COUNT`이면 빈 배열 또는 `null` |
| `rewardCouponPolicyId` | String | Y | 같은 지역의 `MISSION_REWARD` 쿠폰 정책 식별자 |
| `endsAt` | String | Y | 현재 시각보다 미래인 `Asia/Seoul` 기준 ISO 8601 `+09:00` 오프셋 종료 예정 시각 |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "미션 생성에 성공했습니다.",
  "data": {
    "missionId": "701",
    "status": "DRAFT"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `201` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 생성 성공 메시지 |
| `data.missionId` | String | 생성한 미션 식별자 |
| `data.status` | String | 생성 상태인 `DRAFT` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 필수값, 조건별 입력, 종료 예정 시각 또는 대상 콘텐츠가 유효하지 않거나 `targetContentIds`에 중복이 있다. 미션을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 미션을 생성하지 않는다. |
| `400` | `INVALID_TYPE` | 요청 필드를 선언된 타입으로 변환할 수 없다. 미션을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 미션을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 담당 지역의 `OPERATOR` 역할이 없거나 대상 보상·콘텐츠의 지역이 다르다. 미션을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠·쿠폰 정책을 찾을 수 없거나 대상 콘텐츠가 삭제됐다. 미션을 생성하지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 쿠폰 정책이 `ENDED`이거나 발급 경로가 `MISSION_REWARD`가 아니다. 미션을 생성하지 않는다. |

### 처리 규칙

1. 인증 주체의 담당 지역을 미션 지역으로 고정한다.
2. `VISIT_COUNT`는 양의 `requiredVisitCount`와 대상 콘텐츠 없음, `CONTENT_SET`은 중복 없는 하나 이상의 대상 콘텐츠와 `requiredVisitCount = null`을 검증한다. 중복 식별자를 임의로 제거하지 않고 `400 INVALID_INPUT`으로 거부한다.
3. 보상 쿠폰 정책 행을 잠근 뒤 지역과 발급 경로, 상태를 검증한다. 미션과 같은 지역이고
   `issuance_type = MISSION_REWARD`이며 상태가 `DRAFT`, `PUBLISHED` 중 하나인 정책만 연결한다.
4. `CONTENT_SET`이면 중복을 검증한 대상 콘텐츠 행을 `contentId` 오름차순으로 잠근다. 모든 대상 콘텐츠가 미션과
   같은 지역이고 `deleted_at IS NULL`인지 확인하며, 작성 단계에서는 콘텐츠 상태를 제한하지 않는다.
5. `ENDED` 쿠폰 정책은 종료된 시점부터 새 미션에 연결하지 않는다.
6. 성공 시 미션, 대상 콘텐츠와 `null → DRAFT`, `reason_code = MISSION_CREATED`인 감사 이벤트를
   같은 트랜잭션으로 기록한다.
