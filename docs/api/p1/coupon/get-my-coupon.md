## 6. 내 쿠폰 상세 조회

활성 회원이 본인 쿠폰의 정책, 발급 근거와 현재 상태를 조회한다.

### Request

```http
GET /me/coupons/{couponId}
```

#### Request Example

```http
GET /api/v1/me/coupons/1001 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 활성 회원이어야 한다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `couponId` | String | Y | 조회할 쿠폰 식별자. 양수여야 한다. |

#### Query Parameter

없음.

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
  "message": "내 쿠폰 상세 조회에 성공했습니다.",
  "data": {
    "coupon": {
      "couponId": "1001",
      "couponPolicyId": "501",
      "policyName": "재방문 3000원 할인",
      "issueSourceType": "VISIT",
      "sourceId": "7001",
      "status": "AVAILABLE",
      "discountAmount": 3000,
      "minimumPaymentAmount": 10000,
      "issuedAt": "2026-08-01T03:00:00Z",
      "expiresAt": "2026-08-31T03:00:00Z"
    },
    "policy": {
      "contentId": "101",
      "regionId": "12",
      "status": "PUBLISHED",
      "validDaysAfterIssue": 30
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.coupon` | Object | 쿠폰 상세 |
| `data.coupon.couponId` | String | 쿠폰 식별자 |
| `data.coupon.couponPolicyId` | String | 쿠폰 정책 식별자 |
| `data.coupon.policyName` | String | 쿠폰 정책 이름 |
| `data.coupon.issueSourceType` | String | 발급 근거 유형 |
| `data.coupon.sourceId` | String | 발급 근거 식별자. 방문, 미션 보상 수령 또는 스탬프북 완료 보상 식별자 |
| `data.coupon.status` | String | 쿠폰 현재 상태 |
| `data.coupon.discountAmount` | Number | 정액 할인 금액 |
| `data.coupon.minimumPaymentAmount` | Number | 최소 결제 금액 |
| `data.coupon.issuedAt` | String | 발급 시각 |
| `data.coupon.expiresAt` | String | 쿠폰 자체 만료 시각 |
| `data.policy.contentId` | String | 쿠폰 정책 적용 콘텐츠 식별자 |
| `data.policy.regionId` | String | 쿠폰 정책 지역 식별자 |
| `data.policy.status` | String | 쿠폰 정책 현재 상태 |
| `data.policy.validDaysAfterIssue` | Number | 발급 후 유효 일수 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `couponId`가 양수가 아니다. |
| `400` | `INVALID_TYPE` | `couponId`의 형식이 올바르지 않다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 쿠폰의 소유자가 아니다. |
| `404` | `NOT_FOUND` | 대상 쿠폰을 찾을 수 없다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 상세 조회 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 쿠폰의 `user_id`가 일치해야 한다.
3. 이 API는 조회 전용이며 쿠폰 상태를 변경하지 않는다.
