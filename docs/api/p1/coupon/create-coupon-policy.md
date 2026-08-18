## 9. 쿠폰 정책 생성

콘텐츠 소유 운영자가 본인 콘텐츠에 적용할 작성 중 상태의 쿠폰 정책을 생성한다.
생성된 정책은 `DRAFT` 상태이며 공개 전까지 발급 근거가 될 수 없다.

### Request

```http
POST /operator/coupon-policies
```

#### Request Example

```http
POST /api/v1/operator/coupon-policies HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "contentId": "101",
  "name": "재방문 3000원 할인",
  "description": "유효 방문 완료 후 발급되는 재방문 쿠폰",
  "issueSourceType": "VISIT",
  "discountAmount": 3000,
  "minimumPaymentAmount": 10000,
  "validDaysAfterIssue": 30,
  "issueStartsAt": "2026-08-01T00:00:00Z",
  "issueEndsAt": "2026-08-31T14:59:59Z",
  "totalIssueLimit": 1000
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}`. Access Token에 `ROLE_OPERATOR` authority가 있어야 하며, DB에서 활성 `ORDINARY` 계정, 현재 담당 지역 관계, 대상 콘텐츠 소유권을 확인한다. |
| `Content-Type` | Y | `application/json` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

위 Request Example을 따른다.

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | 쿠폰 정책을 적용할 본인 소유 콘텐츠 식별자. 양수여야 한다. 정책 지역은 콘텐츠에서 계산한다. |
| `name` | String | Y | 쿠폰 정책 이름. 앞뒤 공백 제거 후 비어 있을 수 없다. |
| `description` | String or null | N | 사용자와 운영자에게 노출할 설명. `null` 또는 빈 문자열을 허용한다. |
| `issueSourceType` | String | Y | `VISIT`, `MISSION_REWARD`, `STAMPBOOK_COMPLETION` 중 하나 |
| `discountAmount` | Number | Y | 정액 할인 금액. 1 이상 정수 |
| `minimumPaymentAmount` | Number | Y | 최소 결제 금액. 0 이상 정수 |
| `validDaysAfterIssue` | Number | Y | 발급 후 유효 일수. 1 이상 365 이하 정수 |
| `issueStartsAt` | String | Y | 발급 가능 시작 시각. UTC ISO 8601 일시 |
| `issueEndsAt` | String | Y | 발급 가능 종료 시각. UTC ISO 8601 일시. `issueStartsAt`보다 뒤여야 한다. |
| `totalIssueLimit` | Number or null | N | 정책 전체 발급 한도. `null`이면 한도를 두지 않는다. 값이 있으면 1 이상 정수 |

### Response

#### Status

```http
201 Created
```

#### Response Body

```json
{
  "statusCode": 201,
  "code": "SUCCESS",
  "message": "쿠폰 정책 생성에 성공했습니다.",
  "data": {
    "couponPolicyId": "501",
    "contentId": "101",
    "regionId": "12",
    "name": "재방문 3000원 할인",
    "status": "DRAFT",
    "issueSourceType": "VISIT",
    "discountAmount": 3000,
    "minimumPaymentAmount": 10000,
    "validDaysAfterIssue": 30,
    "issueStartsAt": "2026-08-01T00:00:00Z",
    "issueEndsAt": "2026-08-31T14:59:59Z",
    "totalIssueLimit": 1000,
    "createdAt": "2026-08-06T03:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태 코드. 항상 `201` |
| `code` | String | 성공 코드. 항상 `SUCCESS` |
| `message` | String | 공개 성공 메시지 |
| `data.couponPolicyId` | String | 생성된 쿠폰 정책 식별자 |
| `data.contentId` | String | 정책 적용 콘텐츠 식별자 |
| `data.regionId` | String | 정책 지역 식별자 |
| `data.name` | String | 정책 이름 |
| `data.status` | String | 정책 상태. 항상 `DRAFT` |
| `data.issueSourceType` | String | 발급 근거 유형 |
| `data.discountAmount` | Number | 정액 할인 금액 |
| `data.minimumPaymentAmount` | Number | 최소 결제 금액 |
| `data.validDaysAfterIssue` | Number | 발급 후 유효 일수 |
| `data.issueStartsAt` | String | 발급 가능 시작 시각 |
| `data.issueEndsAt` | String | 발급 가능 종료 시각 |
| `data.totalIssueLimit` | Number or null | 정책 전체 발급 한도 |
| `data.createdAt` | String | 생성 시각 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 필수값이 없거나 형식·범위가 올바르지 않다. 정책은 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문 형식이 올바르지 않다. 정책은 생성하지 않는다. |
| `400` | `INVALID_TYPE` | 필드 값의 JSON 타입이 계약과 다르다. 정책은 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | 인증 정보가 없거나 유효하지 않다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_OPERATOR` authority가 없거나, 활성 `ORDINARY` 계정이 아니거나, 대상 콘텐츠와 현재 담당 지역이 다르거나, 대상 콘텐츠를 소유하지 않는다. |
| `404` | `NOT_FOUND` | 대상 콘텐츠를 찾을 수 없다. |
| `409` | `COUPON_POLICY_CONFLICT` | 종료 시각이 시작 시각보다 빠르거나 정책 조건 조합이 발급 가능한 상태가 아니다. 정책은 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 정책 생성 중 예상하지 못한 서버 오류가 발생했다. |

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

1. Access Token의 `ROLE_OPERATOR` authority를 1차로 확인한다. DB에서는 활성 `ORDINARY` 계정, 인증 주체와 대상 `contentId` 콘텐츠의 현재 담당 지역 일치 및 소유권을 확인한다. 정책의 `regionId`는 콘텐츠에서 계산한다.
2. 정액 할인 금액은 1 이상, 최소 결제 금액은 0 이상이며 발급 후 유효 일수는 1 이상 365 이하여야 한다.
3. `minimumPaymentAmount`는 `discountAmount`보다 크거나 같아야 한다.
4. 생성 성공 시 정책 상태는 `DRAFT`로 기록한다.
