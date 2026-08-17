## 11. 쿠폰 정책 공개

정책 콘텐츠의 소유 운영자가 `DRAFT` 상태의 쿠폰 정책을 공개한다.
공개된 정책만 신규 쿠폰 발급의 근거가 된다.

### Request

```http
POST /operator/coupon-policies/{couponPolicyId}/publish
```

#### Request Example

```http
POST /api/v1/operator/coupon-policies/501/publish HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "reason": "검토 완료 후 공개"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. 인증 주체는 `ACTIVE` 회원이고 현재 `OPERATOR` 역할이어야 하며, 정책 콘텐츠와 `region_id`가 같고 정책 콘텐츠를 소유해야 한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `couponPolicyId` | String | Y | 공개할 쿠폰 정책 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "검토 완료 후 공개"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 공개 사유. 앞뒤 공백 제거 후 1~500자여야 한다. |

### Response

#### Status

```http
200 OK
```

이미 `PUBLISHED`인 정책의 공개 재요청은 저장된 공개 결과를 반환하며 공개 시각과 사유를 변경하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "쿠폰 정책 공개에 성공했습니다.",
  "data": {
    "couponPolicyId": "501",
    "status": "PUBLISHED",
    "publishedAt": "2026-08-06T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.couponPolicyId` | String | 공개한 쿠폰 정책 식별자 |
| `data.status` | String | 정책 상태. 항상 `PUBLISHED` |
| `data.publishedAt` | String | 최초 공개 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `couponPolicyId` 또는 `reason`이 없거나 형식·범위가 올바르지 않다. 정책은 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 정책은 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `couponPolicyId`의 형식이 올바르지 않다. 정책은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 `ACTIVE` 회원 또는 현재 `OPERATOR` 역할이 아니거나, 정책 콘텐츠와 `region_id`가 다르거나, 정책 콘텐츠를 소유하지 않는다. |
| `404` | `NOT_FOUND` | 대상 쿠폰 정책을 찾을 수 없다. |
| `409` | `COUPON_POLICY_CONFLICT` | 정책이 `DRAFT` 또는 `PUBLISHED`가 아니거나 필수 공개 조건을 만족하지 않는다. 정책은 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 정책 공개 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "COUPON_POLICY_CONFLICT",
  "message": "쿠폰 정책을 처리할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 회원이고 현재 `OPERATOR` 역할이어야 하며, 인증 주체와 대상 정책의 `contentId`가 가리키는 콘텐츠는 `region_id`가 같고 인증 주체가 해당 콘텐츠를 소유해야 한다.
2. 최초 공개는 `DRAFT → PUBLISHED` 전이만 허용한다.
3. 이미 `PUBLISHED`인 정책은 멱등하게 최초 공개 결과를 반환한다.
4. `ENDED` 정책은 다시 공개할 수 없다.
5. 공개 이력에는 처리자, 이전·이후 상태, 공개 사유와 공개 시각을 기록한다.
