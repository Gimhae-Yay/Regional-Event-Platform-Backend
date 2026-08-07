# 지역 미션 수정 API 명세서

## 1. 개요

담당 지역 운영자가 자기 지역의 `DRAFT` 미션만 수정한다. `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 미션의 기간·조건·보상은
수정할 수 없다.

### Request

```http
PATCH /api/v1/operator/missions/{missionId}
```

#### Request Example

```http
PATCH /api/v1/operator/missions/701 HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "conditionType": "VISIT_COUNT",
  "requiredVisitCount": 4,
  "targetContentIds": [],
  "rewardCouponPolicyId": "501",
  "endsAt": "2026-10-31T23:59:59+09:00"
}
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `missionId` | String | Y | 수정할 미션 식별자. 양수여야 한다. |

#### Request Field

지역 미션 생성 API의 요청 필드와 같다. `targetContentIds`의 중복은 생성 API와 동일하게 `400 INVALID_INPUT`으로
거부한다. `PATCH`는 부분 수정이 아니라 수정 가능한 핵심 값을 새 계약으로 교체한다.

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
  "message": "미션 수정에 성공했습니다.",
  "data": {
    "missionId": "701",
    "status": "DRAFT"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `200` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 수정 성공 메시지 |
| `data.missionId` | String | 수정한 미션 식별자 |
| `data.status` | String | 수정 가능한 상태인 `DRAFT` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `missionId` 또는 요청 값이 유효하지 않다. 미션을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문을 역직렬화할 수 없다. 미션을 변경하지 않는다. |
| `400` | `INVALID_TYPE` | 요청 값을 선언된 타입으로 변환할 수 없다. 미션을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 미션을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 담당 지역의 `OPERATOR` 역할이 없거나 미션·대상 콘텐츠·보상 정책의 지역이 다르다. 미션을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 미션·대상 콘텐츠·쿠폰 정책을 찾을 수 없거나 대상 콘텐츠가 삭제됐다. 미션을 변경하지 않는다. |
| `409` | `MISSION_STATE_CONFLICT` | 잠금 뒤 미션이 `DRAFT`가 아니거나 보상 정책 연결이 달라졌거나, 요청한 보상 정책이 `ENDED` 또는 `MISSION_REWARD`가 아닌 발급 경로다. 미션을 변경하지 않는다. |

### 처리 규칙

1. 미션의 담당 지역과 인증 주체의 담당 지역이 같아야 한다.
2. 현재 미션에 연결된 보상 정책과 요청한 보상 정책이 다르면 두 정책 행을 식별자 오름차순으로 잠그고,
   같으면 해당 정책 행을 한 번만 잠근다. 그 뒤 미션 행을 잠근다.
3. 잠금 획득 뒤 미션 상태가 `DRAFT`이고 현재 보상 정책 연결이 최초 조회 결과와 같은지 다시 확인한다.
   조건이 달라졌으면 미션을 변경하지 않고 `409 MISSION_STATE_CONFLICT`를 반환한다.
4. 요청한 보상 정책의 지역·발급 경로와 `DRAFT`, `PENDING_REVIEW`, `PUBLISHED` 허용 상태를 생성 API와
   동일하게 검증한다.
5. `CONTENT_SET`이면 요청한 대상 콘텐츠 행을 `contentId` 오름차순으로 잠근 뒤 모두 미션과 같은 지역이고
   `deleted_at IS NULL`인지 검증한다. 작성 단계에서는 콘텐츠 상태를 제한하지 않는다.
6. 성공 시 기존 대상 콘텐츠 구성을 요청 값으로 교체하고 `DRAFT → DRAFT`,
   `reason_code = MISSION_UPDATED`인 감사 이벤트를 같은 트랜잭션으로 기록한다.
