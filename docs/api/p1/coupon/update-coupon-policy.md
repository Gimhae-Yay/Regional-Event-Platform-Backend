## 10. 쿠폰 정책 수정

콘텐츠 소유 운영자가 `DRAFT` 상태의 쿠폰 정책을 부분 수정한다.
이미 공개됐거나 종료된 정책은 기존 발급 기준 보존을 위해 이 API로 수정할 수 없다.

### Request

```http
PATCH /operator/coupon-policies/{couponPolicyId}
```

#### Request Example

```http
PATCH /api/v1/operator/coupon-policies/501 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "discountAmount": 5000,
  "minimumPaymentAmount": 15000
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
| `couponPolicyId` | String | Y | 수정할 쿠폰 정책 식별자. 양수여야 한다. |

#### Query Parameter

없음.

#### Request Body

위 Request Example을 따른다.

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | String | N | 포함하면 정책 이름을 변경한다. 앞뒤 공백 제거 후 비어 있을 수 없고 `null`을 허용하지 않는다. |
| `description` | String or null | N | 포함하면 설명을 변경한다. `null` 또는 빈 문자열은 설명 제거를 의미한다. 생략하면 기존 값을 유지한다. |
| `discountAmount` | Number | N | 포함하면 정액 할인 금액을 변경한다. 1 이상 정수이며 `null`을 허용하지 않는다. |
| `minimumPaymentAmount` | Number | N | 포함하면 최소 결제 금액을 변경한다. 0 이상 정수이며 `null`을 허용하지 않는다. |
| `validDaysAfterIssue` | Number | N | 포함하면 발급 후 유효 일수를 변경한다. 1 이상 365 이하 정수이며 `null`을 허용하지 않는다. |
| `issueStartsAt` | String | N | 포함하면 발급 가능 시작 시각을 변경한다. UTC ISO 8601 일시이며 `null`을 허용하지 않는다. |
| `issueEndsAt` | String | N | 포함하면 발급 가능 종료 시각을 변경한다. UTC ISO 8601 일시이며 `null`을 허용하지 않는다. |
| `totalIssueLimit` | Number or null | N | 포함하면 정책 전체 발급 한도를 변경한다. `null`은 한도 제거를 의미하고 값은 1 이상 정수다. |

변경 필드를 하나 이상 포함해야 한다. 생략한 필드는 기존 값을 유지하고, 변경 후 전체 값으로 기간·금액 조합을 다시 검증한다.

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
  "message": "쿠폰 정책 수정에 성공했습니다.",
  "data": {
    "couponPolicyId": "501",
    "status": "DRAFT",
    "name": "재방문 5000원 할인",
    "discountAmount": 5000,
    "minimumPaymentAmount": 15000,
    "validDaysAfterIssue": 30,
    "updatedAt": "2026-08-06T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `200` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.couponPolicyId` | String | 수정한 쿠폰 정책 식별자 |
| `data.status` | String | 정책 상태. 항상 `DRAFT` |
| `data.name` | String | 정책 이름 |
| `data.discountAmount` | Number | 정액 할인 금액 |
| `data.minimumPaymentAmount` | Number | 최소 결제 금액 |
| `data.validDaysAfterIssue` | Number | 발급 후 유효 일수 |
| `data.updatedAt` | String | 수정 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 필수값이 없거나 형식·범위가 올바르지 않다. 정책은 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 정책은 변경하지 않는다. |
| `400` | `INVALID_TYPE` | `couponPolicyId`의 형식이 올바르지 않다. 정책은 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | 인증 주체가 `ACTIVE` 회원 또는 현재 `OPERATOR` 역할이 아니거나, 정책 콘텐츠와 `region_id`가 다르거나, 정책 콘텐츠를 소유하지 않는다. |
| `404` | `NOT_FOUND` | 대상 쿠폰 정책을 찾을 수 없다. |
| `409` | `COUPON_POLICY_CONFLICT` | 정책이 `DRAFT`가 아니거나 수정 가능한 조건이 아니다. 정책은 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 정책 수정 중 예상하지 못한 서버 오류가 발생했다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "COUPON_POLICY_CONFLICT",
  "message": "쿠폰 정책을 수정할 수 없는 상태입니다.",
  "data": null
}
```

### 처리 규칙

1. 인증 주체는 `ACTIVE` 회원이고 현재 `OPERATOR` 역할이어야 하며, 인증 주체와 대상 정책의 `contentId`가 가리키는 콘텐츠는 `region_id`가 같고 인증 주체가 해당 콘텐츠를 소유해야 한다.
2. 대상 정책은 `DRAFT` 상태여야 한다.
3. `contentId`, `regionId`, `issueSourceType`은 수정하지 않는다. 다른 콘텐츠나 발급 경로가 필요하면 새 정책을 생성한다.
4. 요청에 포함된 필드만 변경한 뒤 `discountAmount >= 1`, `minimumPaymentAmount >= discountAmount`,
   `1 <= validDaysAfterIssue <= 365`, `issueStartsAt < issueEndsAt`를 검증한다.
5. 수정은 정책의 상태를 변경하지 않으므로 공통 감사 이력을 생성하지 않는다.
