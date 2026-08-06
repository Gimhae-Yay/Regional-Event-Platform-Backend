# 스탬프북 수정 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 본인 담당 범위의 `DRAFT` 스탬프북에서 대상 콘텐츠 전체 또는 완료 보상 쿠폰 정책을 수정한다.
스탬프북 지역은 생성 뒤 바꾸지 않으며, 심사 요청·공개·종료 상태에서는 핵심 값을 수정할 수 없다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `PATCH /api/v1/operator/stampbooks/{stampbookId}` | `stampbook`, `stampbook_content`, `coupon_policy`, `audit_event` |

## 2. 공통 계약 참조

수정·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다.

## 3. 스탬프북 수정

### Request

```http
PATCH /api/v1/operator/stampbooks/{stampbookId}
```

#### Request Example

```http
PATCH /api/v1/operator/stampbooks/101 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "contentIds": ["201", "202", "203"],
  "rewardCouponPolicyId": "301",
  "reason": "대상 콘텐츠 추가"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `stampbookId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 스탬프북 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "contentIds": ["201", "202", "203"],
  "rewardCouponPolicyId": "301",
  "reason": "대상 콘텐츠 추가"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `contentIds` | Array&lt;String&gt; | N | 제공하면 기존 대상 전체를 이 배열로 교체한다. 비어 있거나 중복되면 안 되며, 각 값은 양의 10진 문자열·signed 64비트 `Long` 범위를 만족해야 한다. |
| `rewardCouponPolicyId` | String | N | 제공하면 완료 보상 쿠폰 정책을 교체한다. 양의 10진 문자열·signed 64비트 `Long` 범위여야 한다. |
| `reason` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 수정 사유다. 빈 문자열 또는 공백만으로 된 값은 허용하지 않으며 성공 감사 이력에 기록한다. |

`contentIds`, `rewardCouponPolicyId` 중 하나 이상을 제공해야 한다.

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
  "message": "스탬프북 수정에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "status": "DRAFT",
    "targetCount": 3,
    "updatedAt": "2026-08-06T02:10:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 수정한 스탬프북 식별자다. |
| `data.status` | String | 항상 `DRAFT`다. |
| `data.targetCount` | Integer | 수정 후 대상 콘텐츠 수다. |
| `data.updatedAt` | String | 수정과 성공 감사 이력 기록 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 수정하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | 수정 필드 미제공, 필드 형식·범위 위반, 빈·중복 콘텐츠 배열 또는 사유 형식 위반이다. 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 스탬프북 지역·대상 콘텐츠의 담당 범위를 벗어난다. 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 대상 스탬프북, 요청 대상 콘텐츠 또는 보상 쿠폰 정책이 없다. 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |
| `409` | `STAMPBOOK_STATE_CONFLICT` | 대상 스탬프북이 `DRAFT`가 아니다. 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 스탬프북·대상 연결·감사 이력을 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "STAMPBOOK_STATE_CONFLICT",
  "message": "스탬프북 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 인증 주체가 활성·승인된 `OPERATOR`이고 대상 스탬프북 지역과 기존·변경 대상 콘텐츠의 소유 운영자인지 검증한다.
2. 스탬프북은 `DRAFT`에서만 수정할 수 있다. `PENDING_REVIEW`, `PUBLISHED`, `ENDED`에서는 `STAMPBOOK_STATE_CONFLICT`를 반환한다.
3. `contentIds`를 제공하면 기존 `stampbook_content` 전체를 교체하며, 수정 후에도 대상 콘텐츠가 하나 이상이어야 한다.
4. 변경 대상 콘텐츠와 보상 쿠폰 정책은 스탬프북의 기존 지역과 각각 일치해야 하고, 보상 쿠폰 정책의 발급 경로는 `STAMPBOOK_COMPLETION`이어야 한다.
5. 지역 변경은 제공하지 않는다. 다른 지역의 스탬프북이 필요하면 새 스탬프북을 생성한다.
6. 스탬프북·대상 콘텐츠 연결 변경과 서버가 부여한 `requestId`를 포함한 `STAMPBOOK` 수정 감사 이력은 하나의 트랜잭션으로 처리한다.
