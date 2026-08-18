## 12. 쿠폰 정책 종료

정책 콘텐츠의 소유 운영자가 공개 중인 쿠폰 정책을 종료한다.
종료는 신규 발급만 중단하며 이미 발급된 쿠폰의 자체 만료 시각과 사용 조건을 바꾸지 않는다.

### Request

```http
POST /operator/coupon-policies/{couponPolicyId}/end
```

#### Request Example

```http
POST /api/v1/operator/coupon-policies/501/end HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "reason": "프로모션 기간 종료"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. Access Token에 `ROLE_OPERATOR` authority가 있어야 하며, DB에서 활성 `ORDINARY` 계정, 현재 담당 지역 관계, 정책 콘텐츠 소유권을 확인한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `couponPolicyId` | String | Y | 종료할 쿠폰 정책 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "프로모션 기간 종료"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 종료 사유. 앞뒤 공백 제거 후 비어 있을 수 없다. |

### Response

#### Status

```http
200 OK
```

이미 `ENDED`인 정책의 종료 재요청은 저장된 종료 결과를 반환하며 종료 시각과 사유를 변경하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "쿠폰 정책 종료에 성공했습니다.",
  "data": {
    "couponPolicyId": "501",
    "status": "ENDED",
    "endedAt": "2026-08-06T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.couponPolicyId` | String | 종료한 쿠폰 정책 식별자 |
| `data.status` | String | 정책 상태. 항상 `ENDED` |
| `data.endedAt` | String | 최초 종료 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `couponPolicyId` 또는 `reason`이 없거나 형식·범위가 올바르지 않다. 정책은 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 정책은 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `couponPolicyId`의 형식이 올바르지 않다. 정책은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_OPERATOR` authority가 없거나, 활성 `ORDINARY` 계정이 아니거나, 정책 콘텐츠와 현재 담당 지역이 다르거나, 정책 콘텐츠를 소유하지 않는다. |
| `404` | `NOT_FOUND` | 대상 쿠폰 정책을 찾을 수 없다. |
| `409` | `COUPON_POLICY_REFERENCED` | 공개 중인 미션 또는 스탬프북이 완료 보상으로 참조 중이다. 정책은 변경하지 않는다. |
| `409` | `COUPON_POLICY_CONFLICT` | 정책이 `PUBLISHED` 또는 `ENDED`가 아니거나 종료 가능한 상태가 아니다. 정책은 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 정책 종료 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "COUPON_POLICY_REFERENCED",
  "message": "참조 중인 쿠폰 정책은 종료할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. Access Token의 `ROLE_OPERATOR` authority를 1차로 확인한다. DB에서는 활성 `ORDINARY` 계정, 인증 주체와 대상 정책 `contentId` 콘텐츠의 현재 담당 지역 일치 및 소유권을 확인한다.
2. 최초 종료는 `PUBLISHED → ENDED` 전이만 허용한다.
3. 이미 `ENDED`인 정책은 멱등하게 최초 종료 결과를 반환한다.
4. 공개 중인 미션 또는 스탬프북이 완료 보상으로 참조하는 정책은 종료할 수 없다.
5. 종료 성공 시 신규 쿠폰 발급만 중단하며 기존 쿠폰의 `expiresAt`과 상태를 소급 변경하지 않는다.
6. 종료 이력에는 처리자, 이전·이후 상태, 종료 사유와 종료 시각을 기록한다.
