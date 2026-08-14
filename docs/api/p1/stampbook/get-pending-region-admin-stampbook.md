# 스탬프북 심사 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 같은 지역의 `PENDING_REVIEW` 스탬프북과 대상 콘텐츠, 완료 보상 정책, 심사 요청 정보를
확인한다. 이 API는 심사 대기 상태를 읽기만 하며 승인·반려 권한을 부여하거나 상태와 감사 이력을 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `GET /api/v1/region-admin/stampbooks/{stampbookId}` | `stampbook`, `stampbook_content`, `content`, `coupon_policy`, `audit_event` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 단건 조회이므로
페이지네이션을 적용하지 않는다.

## 3. 스탬프북 심사 상세 조회

### Request

```http
GET /api/v1/region-admin/stampbooks/{stampbookId}
```

#### Request Example

```http
GET /api/v1/region-admin/stampbooks/101 HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `stampbookId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 심사 대상 스탬프북 식별자다. |

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
  "message": "스탬프북 심사 상세 조회에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "regionId": "1",
    "status": "PENDING_REVIEW",
    "targetContents": [
      {
        "contentId": "201",
        "regionId": "1",
        "title": "김해 가야문화 체험",
        "status": "PUBLISHED"
      },
      {
        "contentId": "202",
        "regionId": "1",
        "title": "대성동고분박물관 해설",
        "status": "PUBLISHED"
      }
    ],
    "rewardCouponPolicy": {
      "couponPolicyId": "301",
      "regionId": "1",
      "issuanceType": "STAMPBOOK_COMPLETION",
      "status": "PUBLISHED"
    },
    "requestedAt": "2026-08-14T02:20:00Z",
    "requestReason": "지역 관리자 공개 심사 요청"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 양의 10진 문자열인 심사 대상 스탬프북 식별자다. |
| `data.regionId` | String | 인증 지역 관리자의 담당 지역과 같은 스탬프북 지역 식별자다. |
| `data.status` | String | 항상 `PENDING_REVIEW`다. |
| `data.targetContents` | Array | 스탬프 적립 대상 콘텐츠 배열이다. 비어 있지 않으며 `contentId` 오름차순으로 반환한다. |
| `data.targetContents[].contentId` | String | 대상 콘텐츠 식별자다. |
| `data.targetContents[].regionId` | String | 스탬프북 지역과 같은 대상 콘텐츠 지역 식별자다. |
| `data.targetContents[].title` | String | 심사 시점의 대상 콘텐츠 제목이다. |
| `data.targetContents[].status` | String | 심사 시점의 대상 콘텐츠 운영 상태다. 승인 시에는 대상 콘텐츠의 지역·소유 관계를 잠금 뒤 다시 검증한다. |
| `data.rewardCouponPolicy` | Object | 완료 보상 쿠폰 정책의 심사 시점 정보다. |
| `data.rewardCouponPolicy.couponPolicyId` | String | 완료 보상 쿠폰 정책 식별자다. |
| `data.rewardCouponPolicy.regionId` | String | 스탬프북 지역과 같은 보상 정책 지역 식별자다. |
| `data.rewardCouponPolicy.issuanceType` | String | 항상 `STAMPBOOK_COMPLETION`이어야 한다. |
| `data.rewardCouponPolicy.status` | String | 심사 시점의 보상 쿠폰 정책 상태다. 승인 시에는 다시 `PUBLISHED`인지 검증한다. |
| `data.requestedAt` | String | 가장 최근 성공한 `DRAFT → PENDING_REVIEW` 심사 요청 감사 이벤트의 발생 시각이다. UTC ISO 8601 형식이다. |
| `data.requestReason` | String | 같은 심사 요청 감사 이벤트에 기록된 앞뒤 공백 제거 뒤 1~500자의 심사 요청 사유다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 데이터를 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `400` | `INVALID_INPUT` | `stampbookId`가 양의 10진 문자열 또는 signed 64비트 `Long` 범위를 만족하지 않는다. 데이터를 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상세를 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성·승인된 `REGION_ADMIN`이 아니거나 담당 지역 배정이 없다. 상세를 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `404` | `NOT_FOUND` | 스탬프북이 없거나 인증 지역 관리자의 담당 지역에 속하지 않거나 현재 `PENDING_REVIEW`가 아니다. 상태와 감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 스탬프북·대상 콘텐츠·완료 보상 정책·심사 요청 감사 이력의 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 상태와 감사 이력을 변경하지 않는다. |

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

1. 서버는 인증 주체가 활성·승인된 `REGION_ADMIN`이고 담당 `region_id`를 보유하는지 확인한다. 스탬프북의 `region_id`와 담당 지역이 다르면 존재 여부를 숨기기 위해 `404 NOT_FOUND`로 처리한다.
2. 같은 지역이면서 현재 `PENDING_REVIEW`인 스탬프북만 반환한다. `DRAFT`, `PUBLISHED`, `ENDED` 상태는 심사 상세 대상이 아니며 `404 NOT_FOUND`로 처리한다.
3. `targetContents`는 연결된 모든 `stampbook_content`와 현재 콘텐츠 정보를 `contentId` 오름차순으로 조립한다. 대상 콘텐츠가 없거나 스탬프북 지역과 다른 콘텐츠가 연결된 경우 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
4. `rewardCouponPolicy`는 현재 연결된 완료 보상 정책 정보다. 목록·상세 조회는 정책 또는 콘텐츠 상태를 바꾸지 않으며, 승인 API가 동일 지역·`STAMPBOOK_COMPLETION` 발급 경로·`PUBLISHED` 상태를 잠금 뒤 다시 검증한다.
5. `requestedAt`과 `requestReason`은 해당 스탬프북의 가장 최근 성공 `STAMPBOOK` 감사 이벤트 중 이전 상태가 `DRAFT`, 이후 상태가 `PENDING_REVIEW`인 이벤트에서 함께 읽는다. 이벤트가 없거나 사유가 비어 있거나 500자를 초과하면 정합성 오류로 처리한다.
6. 이 조회와 승인·반려 명령이 경합해 조회 뒤 상태가 바뀔 수 있다. 이 응답은 승인·반려 권한이나 상태 보장을 제공하지 않으며, 명령 API는 잠금을 얻은 뒤 현재 상태와 정책을 다시 검증한다.
7. 조회 시 스탬프북, 대상 콘텐츠, 완료 보상 정책과 감사 이력을 생성·수정·삭제하지 않는다. 성공·실패는 `requestId`, 담당 지역 식별자와 공개 오류 코드만 구조화 로그로 남기며, `requestReason`과 콘텐츠 제목은 로그에 남기지 않는다.
