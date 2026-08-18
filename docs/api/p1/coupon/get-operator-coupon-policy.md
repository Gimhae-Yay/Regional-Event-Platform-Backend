# 내 쿠폰 정책 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-05](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `CPN-01` |
| 소유 도메인 | 쿠폰 |
| 기준 문서 | [쿠폰 API](coupon.md), [쿠폰](../../../p1/coupon.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 콘텐츠 운영자가 목록에서 선택한 본인 소유 콘텐츠의 쿠폰 정책 상세 값과 현재 발급 사용량을 조회한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-05, CPN-01 | `GET /api/v1/operator/coupon-policies/{couponPolicyId}` | `coupon_policy`, `content` |

## 2. 공통 계약 참조

조회·인증·응답·오류 규칙은 [쿠폰 API](coupon.md#2-공통-계약-참조)를 따른다.

## 3. 내 쿠폰 정책 상세 조회

### Request

```http
GET /api/v1/operator/coupon-policies/{couponPolicyId}
```

#### Request Example

```http
GET /api/v1/operator/coupon-policies/501 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. Access Token에 `ROLE_OPERATOR` authority가 있어야 하며, DB에서 활성 `ORDINARY` 계정과 현재 담당 지역 관계를 확인한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `couponPolicyId` | String | Y | 조회할 쿠폰 정책 식별자다. 양의 10진 문자열이며 signed 64비트 `Long` 범위여야 한다. |

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
  "message": "내 쿠폰 정책 상세 조회에 성공했습니다.",
  "data": {
    "couponPolicyId": "501",
    "contentId": "101",
    "regionId": "12",
    "name": "재방문 3000원 할인",
    "description": "다음 유료 예약에 사용할 수 있습니다.",
    "status": "PUBLISHED",
    "issueSourceType": "VISIT",
    "discountAmount": 3000,
    "minimumPaymentAmount": 10000,
    "validDaysAfterIssue": 30,
    "issueStartsAt": "2026-08-01T00:00:00Z",
    "issueEndsAt": "2026-08-31T14:59:59Z",
    "totalIssueLimit": 1000,
    "issuedCount": 42,
    "publishedAt": "2026-08-01T00:00:00Z",
    "endedAt": null
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.couponPolicyId` | String | 양의 10진 문자열인 쿠폰 정책 식별자다. |
| `data.contentId` | String | 정책이 적용되는 콘텐츠 식별자다. |
| `data.regionId` | String | 정책 콘텐츠의 지역 식별자다. |
| `data.name` | String | 쿠폰 정책 이름이다. |
| `data.description` | String 또는 null | 쿠폰 정책 설명이다. |
| `data.status` | String | `DRAFT`, `PUBLISHED`, `ENDED` 중 하나인 현재 정책 상태다. |
| `data.issueSourceType` | String | `VISIT`, `MISSION_REWARD`, `STAMPBOOK_COMPLETION` 중 하나인 발급 근거 유형이다. |
| `data.discountAmount` | Number | 정액 할인 금액이다. 1 이상 정수다. |
| `data.minimumPaymentAmount` | Number | 최소 결제 금액이다. `discountAmount` 이상 정수다. |
| `data.validDaysAfterIssue` | Number | 발급 뒤 유효 일수다. 1 이상 365 이하 정수다. |
| `data.issueStartsAt` | String | 발급 가능 시작 시각이다. UTC ISO 8601 형식이다. |
| `data.issueEndsAt` | String | 발급 가능 종료 시각이다. UTC ISO 8601 형식이다. |
| `data.totalIssueLimit` | Number 또는 null | 정책 전체 발급 한도다. `null`이면 한도가 없다. |
| `data.issuedCount` | Number | 현재까지 발급된 쿠폰 수다. 0 이상 정수다. |
| `data.publishedAt` | String 또는 null | 최초 공개 시각이다. `DRAFT`이면 `null`이다. UTC ISO 8601 형식이다. |
| `data.endedAt` | String 또는 null | 종료 시각이다. `ENDED`가 아니면 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- |
| `400` | `INVALID_INPUT` | `couponPolicyId`가 양의 10진 문자열이 아니거나 signed 64비트 `Long` 범위를 벗어난다. 조회 상태는 변경되지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 상태는 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_OPERATOR` authority가 없거나, 활성 `ORDINARY` 계정이 아니거나, 대상 정책 콘텐츠의 지역이 현재 담당 지역과 다르거나, 대상 콘텐츠를 소유하지 않는다. 쿠폰 정책을 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 쿠폰 정책이 없다. 조회 상태는 변경되지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 정책 상세 조회 중 예상하지 못한 서버 오류가 발생했다. 조회 상태는 변경되지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 403,
  "code": "FORBIDDEN",
  "message": "접근 권한이 없습니다.",
  "data": null
}
```

### 처리 규칙

1. Access Token의 `ROLE_OPERATOR` authority를 1차로 확인하고, DB에서 활성 `ORDINARY` 계정과 현재 담당 지역 관계를 확인한다.
2. 대상 쿠폰 정책이 없으면 `404 NOT_FOUND`를 반환한다.
3. 대상 정책 콘텐츠의 소유자가 인증 운영자가 아니거나 정책 콘텐츠의 `regionId`가 인증 운영자의 담당 지역과 다르면 `403 FORBIDDEN`을 반환한다.
4. 이 API는 조회 전용이며 쿠폰 정책, 발급 수, 정책 이력, 쿠폰, 쿠폰 상태 이력과 감사 이력을 생성·수정·삭제하지 않는다.
