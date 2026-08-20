# 운영자 스탬프북 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 담당 지역이고 현재 연결된 모든 대상 콘텐츠를 소유한 스탬프북의 관리 정보를 조회한다.
모든 수명주기 상태를 읽기 전용으로 제공한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `GET /api/v1/operator/stampbooks/{stampbookId}` | `stampbook`, `stampbook_content`, `content`, `coupon_policy` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 단건 조회이므로
페이지네이션을 적용하지 않는다.

## 3. 운영자 스탬프북 상세 조회

### Request

```http
GET /api/v1/operator/stampbooks/{stampbookId}
```

#### Request Example

```http
GET /api/v1/operator/stampbooks/101 HTTP/1.1
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
| `stampbookId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위를 만족하는 스탬프북 식별자다. |

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
  "message": "운영자 스탬프북 상세 조회에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "title": "김해 가야 문화 완주",
    "regionId": "1",
    "status": "ENDED",
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
    "publishedAt": "2026-08-01T01:00:00Z",
    "endedAt": "2026-08-20T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 양의 10진 문자열인 스탬프북 식별자다. |
| `data.title` | String | 스탬프북에 저장된 고유 제목이다. 앞뒤 공백 제거 뒤 1~100자이며 대상 콘텐츠 제목으로 조합하지 않는다. |
| `data.regionId` | String | 인증 운영자의 담당 지역과 같은 스탬프북 소속 지역 식별자다. |
| `data.status` | String | 스탬프북 상태다. `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `ENDED` 중 하나다. |
| `data.targetContents` | Array | 현재 스탬프 적립 대상 콘텐츠 배열이다. 비어 있지 않으며 `contentId` 오름차순으로 반환한다. |
| `data.targetContents[].contentId` | String | 대상 콘텐츠 식별자다. |
| `data.targetContents[].regionId` | String | 스탬프북 지역과 같은 대상 콘텐츠 지역 식별자다. |
| `data.targetContents[].title` | String | 대상 콘텐츠의 현재 제목이다. |
| `data.targetContents[].status` | String | 대상 콘텐츠의 현재 운영 상태다. 이 조회는 상태를 바꾸지 않으며, 이후 명령은 필요한 상태·소유 관계를 다시 검증한다. |
| `data.rewardCouponPolicy` | Object | 현재 연결된 완료 보상 쿠폰 정책 정보다. |
| `data.rewardCouponPolicy.couponPolicyId` | String | 완료 보상 쿠폰 정책 식별자다. |
| `data.rewardCouponPolicy.regionId` | String | 스탬프북 지역과 같은 보상 쿠폰 정책 지역 식별자다. |
| `data.rewardCouponPolicy.issuanceType` | String | 항상 `STAMPBOOK_COMPLETION`이어야 한다. |
| `data.rewardCouponPolicy.status` | String | 완료 보상 쿠폰 정책의 현재 상태다. 공개 심사 요청·승인 등 명령은 필요한 상태를 다시 검증한다. |
| `data.publishedAt` | String 또는 null | `PUBLISHED`, `ENDED`이면 공개 승인 시각이고, `DRAFT`, `PENDING_REVIEW`이면 `null`이다. UTC ISO 8601 형식이다. |
| `data.endedAt` | String 또는 null | `ENDED`이면 종료 시각이고, 그 외 상태이면 `null`이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 상세를 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `400` | `INVALID_INPUT` | `stampbookId`가 양의 10진 문자열 또는 signed 64비트 `Long` 범위를 만족하지 않는다. 상세를 반환하거나 상태·감사 이력을 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상세를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성·승인된 `OPERATOR`가 아니거나 담당 지역 배정이 없거나, 스탬프북 지역·현재 연결된 모든 대상 콘텐츠의 소유 범위를 벗어난다. 상세를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 스탬프북이 없다. 상태·감사 이력을 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 스탬프북·대상 콘텐츠·완료 보상 쿠폰 정책의 연결 정합성 오류 또는 예상하지 못한 서버 오류가 발생했다. 상태·감사 이력을 변경하지 않는다. |

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

1. 서버는 인증 주체가 활성·승인된 `OPERATOR`이고 대상 스탬프북의 `region_id`를 담당하는지 검증한다.
2. 서버는 현재 연결된 모든 `stampbook_content`의 콘텐츠 소유자가 인증 운영자인지 검증한다. 다른 지역, 다른 운영자 또는 일부 대상 콘텐츠만 소유한 경우 `403 FORBIDDEN`을 반환한다.
3. 모든 수명주기 상태를 반환한다. `DRAFT`, `PENDING_REVIEW`는 `publishedAt = null`, `endedAt = null`이고, `PUBLISHED`는 `publishedAt`만, `ENDED`는 두 시각을 모두 반환한다.
4. `targetContents`는 연결된 모든 `stampbook_content`와 현재 콘텐츠 정보를 `contentId` 오름차순으로 조립한다. 대상 콘텐츠가 없거나 스탬프북 지역과 다른 콘텐츠가 연결된 경우 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
5. `rewardCouponPolicy`는 현재 연결된 완료 보상 쿠폰 정책 정보다. 쿠폰 정책이 없거나 스탬프북 지역과 다르거나 발급 경로가 `STAMPBOOK_COMPLETION`이 아니면 정합성 오류로 처리한다.
6. 조회 뒤 상태·대상 콘텐츠·쿠폰 정책이 바뀔 수 있으므로, 생성·수정·공개 심사 요청·종료 명령은 각자의 트랜잭션에서 현재 권한·소유 관계·상태를 다시 검증한다.
7. 조회 시 스탬프북, 대상 콘텐츠 연결, 쿠폰 정책과 감사 이력을 생성·수정·삭제하지 않는다. 성공·실패는 `requestId`, 인증 운영자 식별자와 공개 오류 코드만 구조화 로그로 남기며 제목은 로그에 남기지 않는다.
