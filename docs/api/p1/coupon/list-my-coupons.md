## 4. 내 쿠폰 목록 조회

활성 회원이 본인에게 발급된 쿠폰 목록을 조회한다. 결과가 없으면 빈 배열을 반환하며 쿠폰 상태를 변경하지 않는다.

### Request

```http
GET /me/coupons
```

#### Request Example

```http
GET /api/v1/me/coupons?status=AVAILABLE HTTP/1.1
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
| `status` | String | N | 쿠폰 상태 필터. `AVAILABLE`, `RESERVED`, `USED`, `EXPIRED`, `INVALIDATED` 중 하나. 생략하면 전체 상태를 반환한다. |

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
  "message": "내 쿠폰 목록 조회에 성공했습니다.",
  "data": {
    "coupons": [
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
        "expiresAt": "2026-08-31T03:00:00Z"
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
| `data.coupons` | Array | 내 쿠폰 목록. 결과가 없으면 빈 배열 `[]` |
| `data.coupons[]` | Object | [couponSummary](coupon-common.md#공통-응답-객체) |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `status` 값이 허용 범위가 아니다. 조회 대상과 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니다. |
| `500` | `INTERNAL_SERVER_ERROR` | 내 쿠폰 목록 조회 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 서버는 `coupon.user_id = 인증 회원 식별자` 조건을 만족하는 쿠폰만 조회한다.
3. 목록은 `issuedAt` 내림차순, 같은 시각이면 `couponId` 내림차순으로 정렬한다.
4. 이 API는 조회 전용이며 쿠폰, 발급 이력, 사용 이력과 상태 이력을 변경하지 않는다.
