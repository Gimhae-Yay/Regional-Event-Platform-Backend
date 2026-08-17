## 5. 사용 가능한 내 쿠폰 목록 조회

활성 회원이 특정 활성 홀드의 결제 생성에 사용할 수 있는 본인 쿠폰 목록을 조회한다.
사용 가능 판단 기준 시각은 서버의 현재 시각이며, 응답의 `evaluatedAt`으로 반환한다.

### Request

```http
GET /me/coupons/available
```

#### Request Example

```http
GET /api/v1/me/coupons/available?holdId=789 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `holdId` | String | Y | 쿠폰을 적용할 본인 활성 홀드 식별자. 양수여야 한다. |

#### Request Body

없음.

#### Request Field

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
  "message": "사용 가능한 내 쿠폰 목록 조회에 성공했습니다.",
  "data": {
    "holdId": "789",
    "evaluatedAt": "2026-08-06T03:00:00Z",
    "availableCoupons": [
      {
        "couponId": "1001",
        "couponPolicyId": "501",
        "contentId": "101",
        "regionId": "12",
        "policyName": "재방문 3000원 할인",
        "issueSourceType": "VISIT",
        "status": "AVAILABLE",
        "discountAmount": 3000,
        "minimumPaymentAmount": 10000,
        "issuedAt": "2026-08-01T03:00:00Z",
        "expiresAt": "2026-08-31T03:00:00Z",
        "discountPreview": {
          "baseAmount": 20000,
          "discountAmount": 3000,
          "payableAmount": 17000
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.holdId` | String | 사용 가능 판단 대상 활성 홀드 식별자 |
| `data.evaluatedAt` | String | 사용 가능 판단 기준 시각. API 공통 규칙에 따른 UTC ISO 8601 일시 |
| `data.availableCoupons` | Array | 사용 가능한 내 쿠폰 목록. 결과가 없으면 빈 배열 `[]` |
| `data.availableCoupons[]` | Object | [couponSummary](coupon-common.md#공통-응답-객체)에 할인 미리보기 정보를 더한 객체 |
| `data.availableCoupons[].discountPreview.baseAmount` | Number | 결제 생성과 같은 가격 기준으로 계산한 쿠폰 적용 전 예상 금액 |
| `data.availableCoupons[].discountPreview.discountAmount` | Number | 적용 가능한 할인 금액 |
| `data.availableCoupons[].discountPreview.payableAmount` | Number | 쿠폰 적용 후 결제 금액 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `holdId`가 양수가 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `holdId`의 형식이 올바르지 않다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 홀드의 소유자가 아니다. |
| `404` | `NOT_FOUND` | 대상 홀드를 찾을 수 없다. |
| `409` | `COUPON_AVAILABILITY_CONFLICT` | 홀드가 유효한 `ACTIVE`가 아니거나 결제 생성과 같은 가격 기준으로 쿠폰 적용 금액을 판단할 수 없는 상태다. |
| `500` | `INTERNAL_SERVER_ERROR` | 사용 가능 쿠폰 목록 조회 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "COUPON_AVAILABILITY_CONFLICT",
  "message": "쿠폰 사용 가능 여부를 판단할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 홀드는 인증 회원 소유이고 만료 시각 전의 `ACTIVE` 상태여야 한다.
3. `AVAILABLE` 상태이고 `expiresAt > evaluatedAt`인 쿠폰만 반환한다.
4. 쿠폰 정책의 `contentId`는 홀드 회차의 콘텐츠와 같아야 하며 `regionId`도 일치해야 한다.
5. 결제 생성과 같은 가격 기준으로 계산한 기본 금액이 `minimumPaymentAmount` 미만인 쿠폰은 반환하지 않는다.
6. 정책이 종료됐더라도 이미 발급된 쿠폰은 자체 만료 시각과 사용 조건을 기준으로 판단한다.
7. 이 API는 조회 전용이며 가격 스냅샷 생성, 쿠폰 선점·사용 확정과 상태 이력 생성을 수행하지 않는다.
8. 응답 뒤 홀드·쿠폰 상태 또는 기준 가격이 바뀔 수 있으므로 실제 적용 가능 여부와 할인 금액은 [결제 생성](../payment/create-payment.md)이 트랜잭션 안에서 다시 검증하고 확정한다.
