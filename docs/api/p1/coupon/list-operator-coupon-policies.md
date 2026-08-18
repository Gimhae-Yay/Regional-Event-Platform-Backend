# 내 쿠폰 정책 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-05](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `CPN-01` |
| 소유 도메인 | 쿠폰 |
| 기준 문서 | [쿠폰 API](coupon.md), [쿠폰](../../../p1/coupon.md), [P1 ERD](../../../p1-erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 콘텐츠 운영자가 본인 소유 콘텐츠에 연결된 쿠폰 정책의 식별자·이름·현재 상태를 조회해 상세·수정·공개·종료 대상을 선택한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-05, CPN-01 | `GET /api/v1/operator/coupon-policies` | `coupon_policy`, `content` |

## 2. 공통 계약 참조

조회·인증·응답·오류 규칙은 [쿠폰 API](coupon.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지·커서·총 건수·상태 필터와 사용자 지정 정렬을 제공하지 않는다.

## 3. 내 쿠폰 정책 목록 조회

### Request

```http
GET /api/v1/operator/coupon-policies
```

#### Request Example

```http
GET /api/v1/operator/coupon-policies HTTP/1.1
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

없음.

#### Query Parameter

없음.

#### Request Body

없음.

#### Request Field

없음.

결과는 `couponPolicyId` 내림차순으로 고정한다.

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
  "message": "내 쿠폰 정책 목록 조회에 성공했습니다.",
  "data": {
    "couponPolicies": [
      {
        "couponPolicyId": "501",
        "contentId": "101",
        "name": "재방문 3000원 할인",
        "status": "PUBLISHED"
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.couponPolicies: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.couponPolicies` | Array | 인증 운영자가 소유한 콘텐츠의 쿠폰 정책 배열이다. 결과가 없으면 빈 배열이다. |
| `data.couponPolicies[].couponPolicyId` | String | 양의 10진 문자열인 쿠폰 정책 식별자다. |
| `data.couponPolicies[].contentId` | String | 정책이 적용되는 콘텐츠 식별자다. |
| `data.couponPolicies[].name` | String | 쿠폰 정책 이름이다. |
| `data.couponPolicies[].status` | String | `DRAFT`, `PUBLISHED`, `ENDED` 중 하나인 현재 정책 상태다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 조회 상태는 변경되지 않으며 유효한 Token으로 다시 요청할 수 있다. |
| `403` | `FORBIDDEN` | Access Token에 `ROLE_OPERATOR` authority가 없거나 활성 `ORDINARY` 계정이 아니다. 쿠폰 정책을 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 쿠폰 정책 목록 조회 중 예상하지 못한 서버 오류가 발생했다. 조회 상태는 변경되지 않으며 일시적 장애라면 동일 요청으로 재시도할 수 있다. |

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
2. 서버는 정책 콘텐츠의 소유자가 인증 운영자이고, 정책 콘텐츠의 `regionId`가 인증 운영자의 담당 지역과 같은 쿠폰 정책만 반환한다. 다른 운영자 또는 다른 지역의 정책은 존재 여부와 관계없이 반환하지 않는다.
3. 목록은 `couponPolicyId` 내림차순, 같은 식별자는 없으므로 추가 동률 정렬 없이 고정한다.
4. 이 API는 조회 전용이며 쿠폰 정책, 발급 수, 정책 이력, 쿠폰, 쿠폰 상태 이력과 감사 이력을 생성·수정·삭제하지 않는다.
