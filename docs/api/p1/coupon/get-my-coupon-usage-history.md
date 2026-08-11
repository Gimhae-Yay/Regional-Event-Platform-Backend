## 7. 내 쿠폰 사용 이력 조회

활성 회원이 본인 쿠폰의 사용 확정과 복구 반전 이력을 조회한다.

### Request

```http
GET /me/coupons/{couponId}/usage-history
```

#### Request Example

```http
GET /api/v1/me/coupons/1001/usage-history HTTP/1.1
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
| `couponId` | String | Y | 사용 이력을 조회할 쿠폰 식별자. 양수여야 한다. |

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
  "message": "내 쿠폰 사용 이력 조회에 성공했습니다.",
  "data": {
    "couponId": "1001",
    "usageHistory": [
      {
        "couponRedemptionId": "3001",
        "reservationId": "123",
        "priceSnapshotId": "9001",
        "status": "CONFIRMED",
        "discountAmount": 3000,
        "confirmedAt": "2026-08-06T03:00:00Z",
        "reversedAt": null
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
| `data.couponId` | String | 쿠폰 식별자 |
| `data.usageHistory` | Array | 사용 이력 목록. 결과가 없으면 빈 배열 `[]` |
| `data.usageHistory[].couponRedemptionId` | String | 쿠폰 사용 이력 식별자 |
| `data.usageHistory[].reservationId` | String | 사용 확정에 연결된 예약 식별자 |
| `data.usageHistory[].priceSnapshotId` | String | 사용 확정에 연결된 가격 스냅샷 식별자 |
| `data.usageHistory[].status` | String | `CONFIRMED`, `REVERSED` 중 하나 |
| `data.usageHistory[].discountAmount` | Number | 확정 당시 할인 금액 |
| `data.usageHistory[].confirmedAt` | String | 사용 확정 시각 |
| `data.usageHistory[].reversedAt` | String or null | 복구 반전 시각. 반전되지 않았으면 `null` |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | `couponId`가 양수가 아니다. |
| `400` | `INVALID_TYPE` | `couponId`의 형식이 올바르지 않다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 쿠폰의 소유자가 아니다. |
| `404` | `NOT_FOUND` | 대상 쿠폰을 찾을 수 없다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 사용 이력 조회 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "요청한 작업을 수행할 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 상태의 회원이어야 한다.
2. 인증 회원과 대상 쿠폰의 `user_id`가 일치해야 한다.
3. 사용 이력은 `confirmedAt` 내림차순, 같은 시각이면 `couponRedemptionId` 내림차순으로 정렬한다.
4. 반전된 사용 이력은 삭제하지 않고 `REVERSED` 상태와 반전 시각을 반환한다. 반전 사유는 감사 이력에 보존하며 이 API 응답에는 포함하지 않는다.
