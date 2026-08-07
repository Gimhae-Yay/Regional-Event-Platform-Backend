# 미션 완료 보상 수령 API 명세서

## 1. 개요

방문자가 완료 상태인 본인 미션 참여의 보상을 수령한다. 같은 참여의 반복·동시 수령 요청은 한 건의 보상 효과와 수령 이력으로
수렴하고 최초 결과를 반환한다.

### Request

```http
POST /api/v1/me/mission-participations/{participationId}/rewards/claim
```

#### Request Example

```http
POST /api/v1/me/mission-participations/9001/rewards/claim HTTP/1.1
Authorization: Bearer <accessToken>
Accept: application/json
```

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `participationId` | String | Y | 보상을 수령할 참여 식별자. 양수여야 한다. |

### Response

#### Status

```http
201 Created
```

반복 수령 요청도 저장된 최초 수령 결과를 `201 Created`로 반환한다.

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "미션 보상 수령에 성공했습니다.",
  "data": {
    "missionRewardClaimId": "8101",
    "participationId": "9001",
    "couponId": "6101",
    "couponPolicyId": "501",
    "claimedAt": "2026-08-09T02:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | `201` |
| `code` | String | `SUCCESS` |
| `message` | String | 미션 보상 수령 성공 메시지 |
| `data.missionRewardClaimId` | String | 보상 수령 이력 식별자 |
| `data.participationId` | String | 보상 수령 대상 참여 식별자 |
| `data.couponId` | String | 발급된 쿠폰 식별자 |
| `data.couponPolicyId` | String | 원본 미션의 완료 보상 쿠폰 정책 식별자 |
| `data.claimedAt` | String | 최초 보상 수령·지급 시각. UTC ISO 8601 `Z` 형식 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `participationId`가 유효하지 않다. 수령 이력과 쿠폰을 만들지 않는다. |
| `400` | `INVALID_TYPE` | `participationId`를 식별자로 변환할 수 없다. 수령 이력과 쿠폰을 만들지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 수령 이력과 쿠폰을 만들지 않는다. |
| `403` | `FORBIDDEN` | 본인 참여가 아니거나 활성 방문자가 아니다. 수령 이력과 쿠폰을 만들지 않는다. |
| `404` | `NOT_FOUND` | 참여를 찾을 수 없다. 수령 이력과 쿠폰을 만들지 않는다. |
| `409` | `MISSION_REWARD_CLAIM_CONFLICT` | 기존 수령 이력이 없는 신규 요청에서 미션이 종료됐거나 종료 예정 시각에 도달했거나, 참여가 `COMPLETED`가 아니거나, 보상 정책의 상태·지역·발급 경로·발급 기간·유효 일수·전체 발급 한도가 신규 발급 조건을 만족하지 않는다. 수령 이력과 쿠폰을 만들지 않는다. |

### 처리 규칙

1. 인증·경로 식별자·참여 존재 여부와 본인 소유를 확인한 뒤 기존 보상 수령 이력을 먼저 조회한다.
2. 기존 수령 이력이 있으면 미션이 종료되거나 쿠폰 정책의 현재 상태가 이후에 바뀌었더라도 저장된 최초 수령 결과를
   `201 Created`로 반환한다. 종료 뒤 허용하는 것은 기존 결과 재조회뿐이며 새 쿠폰 효과를 만들지 않는다.
3. 기존 수령 이력이 없으면 검증된 참여에서 보상 정책과 미션 식별자를 구한 뒤 보상 정책 행, 미션 행,
   참여 행 순서로 잠근다.
4. 모든 잠금을 획득한 직후 DB 현재 시각을 한 번만 읽어 `operationAt`으로 고정한다.
5. 미션이 `status = PUBLISHED AND ends_at > operationAt`인지, 참여가 본인 소유의 `COMPLETED`인지 다시 검증한다.
   수동 종료가 먼저 미션 잠금을 얻었거나 `operationAt`이 자동 종료 예정 시각에 도달했으면 신규 수령을 거부한다.
6. 보상 정책은 미션과 같은 지역, `status = PUBLISHED`, `issuance_type = MISSION_REWARD`,
   `issueStartsAt <= operationAt <= issueEndsAt`, `1 <= validDaysAfterIssue <= 365`여야 하며 수령 이력의
   `couponPolicyId`는 원본 미션의 `rewardCouponPolicyId`와 같아야 한다.
7. `UNIQUE (mission_participation_id)`로 참여당 보상 수령 이력을 하나만 허용한다.
8. `mission_reward_claim.claimedAt`, `coupon.issuedAt`, `coupon_issuance.issuedAt`, 최초
   `coupon_status_history.occurredAt`은 모두 자격 검증에 사용한 `operationAt`과 같다.
9. `coupon.expiresAt`은 `operationAt`에 정책의 `validDaysAfterIssue`일을 더한 시각이며,
   `DB 현재 시각 < expiresAt`일 때만 유효하다.
10. 최초 쿠폰 상태는 `AVAILABLE`이며 최초 상태 이력은 `previousStatus = null`, `nextStatus = AVAILABLE`,
   `reasonCode = MISSION_REWARD_ISSUED`, `actorKind = USER`로 기록한다.
11. 전체 발급 한도가 있으면 잠근 정책에서 잔여 한도를 확인하고 발급 한도 사용량 증가를 쿠폰 생성과 원자적으로 처리한다.
12. `mission_reward_claim`, `coupon`, `coupon_issuance`, 최초 `coupon_status_history`와 발급 한도 사용량을 하나의
    트랜잭션으로 커밋하거나 함께 롤백한다.
13. 쿠폰 발급은 `MISSION_REWARD` 발급 근거로 처리하며, 같은 수령 근거는 같은 쿠폰 결과로 수렴한다.
14. 동시 요청의 중복 키 충돌은 선행 트랜잭션의 커밋이 끝난 뒤 기존 수령 이력, 발급 이력, 쿠폰과 최초 상태 이력을
     다시 조회해 최초 결과로 반환한다. 수령 이력만 있고 나머지 발급 결과가 없는 부분 결과는 허용하지 않으며,
     멱등 재요청으로 새 상태 이력을 만들지 않는다.
15. 미션이 `ENDED`가 되거나 `ends_at`에 도달하면 `COMPLETED` 참여도 미수령 보상을 새로 수령할 수 없다.
     별도 유예 기간이나 수동 지급 경로는 제공하지 않는다.
