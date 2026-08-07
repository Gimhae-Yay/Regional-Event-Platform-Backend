## 13. 쿠폰 발급 요청

활성 회원이 공개된 쿠폰 정책과 본인 방문 또는 스탬프북 완료 보상 근거를 기준으로 쿠폰 발급을 요청한다.
방문은 정책·수령자, 스탬프북은 정책·수령자·보상 근거가 같은 반복 요청에서 기존 쿠폰을 반환한다.
`MISSION_REWARD` 쿠폰은 이 API로 발급하지 않고 [미션 완료 보상 수령](../mission/claim-mission-reward.md)이
보상 수령 이력과 쿠폰 발급을 하나의 트랜잭션으로 처리한다.

### Request

```http
POST /coupon-policies/{couponPolicyId}/coupons
```

#### Request Example

```http
POST /api/v1/coupon-policies/501/coupons HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "issueSourceType": "VISIT",
  "sourceId": "7001"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `couponPolicyId` | String | Y | 발급 근거가 되는 쿠폰 정책 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "issueSourceType": "VISIT",
  "sourceId": "7001"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `issueSourceType` | String | Y | `VISIT`, `STAMPBOOK_COMPLETION` 중 하나. 정책의 발급 경로와 같아야 한다. `MISSION_REWARD`는 이 API에서 허용하지 않는다. |
| `sourceId` | String | Y | 발급 근거 식별자. 방문 또는 스탬프북 완료 보상 식별자 |

### Response

#### Status

```http
201 Created
```

같은 발급 식별 키의 재요청은 새 쿠폰을 만들지 않고 기존 쿠폰을 `201 Created`로 반환한다.
기존 발급 결과에는 현재 정책 상태·발급 기간·전체 발급 한도를 다시 적용하지 않는다.

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "쿠폰 발급에 성공했습니다.",
  "data": {
    "couponId": "1001",
    "couponPolicyId": "501",
    "contentId": "101",
    "regionId": "12",
    "policyName": "재방문 3000원 할인",
    "issueSourceType": "VISIT",
    "status": "AVAILABLE",
    "discountAmount": 3000,
    "minimumPaymentAmount": 10000,
    "issuedAt": "2026-08-06T03:00:00Z",
    "expiresAt": "2026-09-05T03:00:00Z",
    "duplicate": false
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data` | Object | 발급된 쿠폰. [couponSummary](coupon-common.md#공통-응답-객체)에 중복 여부를 더한 객체 |
| `data.duplicate` | Boolean | 기존 발급 결과를 반환한 경우 `true`, 최초 발급이면 `false` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `couponPolicyId`, `issueSourceType` 또는 `sourceId`가 없거나 형식·범위가 올바르지 않거나 `issueSourceType = MISSION_REWARD`다. 쿠폰은 발급하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 쿠폰은 발급하지 않는다. |
| `400` | `INVALID_TYPE` | 식별자의 형식이 올바르지 않다. 쿠폰은 발급하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 발급 근거가 인증 회원의 것이 아니다. |
| `404` | `NOT_FOUND` | 대상 쿠폰 정책 또는 발급 근거를 찾을 수 없다. |
| `409` | `COUPON_POLICY_NOT_PUBLISHED` | 신규 발급에서 정책이 `PUBLISHED`가 아니거나 발급 가능 기간이 아니다. 쿠폰은 발급하지 않는다. |
| `409` | `COUPON_ISSUE_CONFLICT` | 신규 발급에서 발급 한도, 콘텐츠·지역 또는 근거 상태를 만족하지 않는다. 쿠폰은 발급하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 발급 중 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 같은 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "COUPON_ISSUE_CONFLICT",
  "message": "쿠폰을 발급할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 요청의 `issueSourceType`은 `VISIT`, `STAMPBOOK_COMPLETION` 중 하나이며 쿠폰 정책의 발급 경로와 같아야 한다. `MISSION_REWARD`는 발급 근거를 조회하기 전에 `400 INVALID_INPUT`으로 거부한다.
3. 발급 근거가 존재하고 인증 회원의 소유인지 검증한다.
4. 방문 발급 식별 키는 정책·수령자로 계산하며 `sourceId`는 자격 증거로만 보존한다. 스탬프북 발급 식별 키는 정책·수령자·보상 근거로 계산한다.
5. 같은 발급 식별 키의 쿠폰이 이미 있으면 현재 정책 상태·발급 기간·전체 발급 한도와 근거의 현재 유효 상태를 다시 검증하지 않고 기존 쿠폰을 반환한다. 쿠폰과 발급 이력은 새로 만들지 않는다.
6. 기존 발급 결과가 없는 경우에만 쿠폰 정책이 `PUBLISHED` 상태이고 서버 현재 시각이 `issueStartsAt` 이상, `issueEndsAt` 이하인지 검증한다.
7. 신규 발급의 근거는 인증 회원의 유효 방문 또는 스탬프북 완료 보상이어야 한다.
8. 신규 발급에서 스탬프북 완료 보상 근거는 원본 보상 정책이 요청 정책과 일치하고 정책 콘텐츠와 같은 지역이어야 한다.
9. 신규 발급에서 방문 근거는 정책의 `contentId`와 같은 콘텐츠에서 생성된 유효 방문이어야 한다.
10. 신규 발급 시 `validDaysAfterIssue`는 1 이상 365 이하이고, 쿠폰 상태는 `AVAILABLE`이며 `expiresAt = issuedAt + validDaysAfterIssue`로 계산한다.
11. 신규 발급 트랜잭션은 정책 잠금 또는 동등한 직렬화 수단을 확보한 뒤 같은 발급 식별 키를 다시 조회한다. 기존 쿠폰이 확인되면 발급 한도 사용량을 증가시키지 않고 `duplicate = true`인 기존 결과를 반환한다.
12. 재조회 후에도 기존 결과가 없고 전체 발급 한도가 있으면 잔여 한도를 확인하고 사용량 증가와 쿠폰 생성을 원자적으로 처리한다.
13. 동시 요청으로 발급 식별 키 유일 제약 충돌이 발생하면 시도한 신규 발급 트랜잭션을 롤백하고 기존 쿠폰을 조회해 `duplicate = true`로 반환한다. 알려진 발급 식별 키 충돌을 `500 INTERNAL_SERVER_ERROR`로 노출하거나 발급 한도 사용량을 추가로 증가시키지 않는다.
14. 쿠폰, 발급 이력, 상태 이력과 발급 한도 사용량은 하나의 트랜잭션에서 함께 커밋한다.
15. 발급 성공과 실패 사유는 구조화 로그와 감사 이력으로 추적한다.
