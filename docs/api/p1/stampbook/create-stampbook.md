# 스탬프북 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-01](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-01`, `STB-02` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0066](../../../adr/0066-require-regional-admin-approval-for-p1-benefit-publication.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 콘텐츠 운영자가 담당 지역의 하나 이상 콘텐츠와 완료 보상 쿠폰 정책을 연결한 `DRAFT` 스탬프북을 생성한다.
대상 콘텐츠 수가 완료 목표 수이고, 콘텐츠별 적립 수는 유효 방문 한 건당 한 개로 고정된다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-01, STB-01, STB-02 | `POST /api/v1/operator/stampbooks` | `stampbook`, `stampbook_content`, `coupon_policy`, `audit_event` |

## 2. 공통 계약 참조

생성·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 생성 명령이므로
페이지네이션과 멱등성 헤더를 적용하지 않는다.

## 3. 스탬프북 생성

### Request

```http
POST /api/v1/operator/stampbooks
```

#### Request Example

```http
POST /api/v1/operator/stampbooks HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "regionId": "1",
  "contentIds": ["201", "202"],
  "rewardCouponPolicyId": "301",
  "reason": "가야 문화 체험 재방문 스탬프북 생성"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token이다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "regionId": "1",
  "contentIds": ["201", "202"],
  "rewardCouponPolicyId": "301",
  "reason": "가야 문화 체험 재방문 스탬프북 생성"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `regionId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위의 담당 지역 식별자다. |
| `contentIds` | Array&lt;String&gt; | Y | 대상 콘텐츠 식별자 배열이다. 비어 있거나 중복되면 안 되며, 각 값은 양의 10진 문자열·signed 64비트 `Long` 범위를 만족해야 한다. |
| `rewardCouponPolicyId` | String | Y | 양의 10진 문자열이며 signed 64비트 `Long` 범위의 완료 보상 쿠폰 정책 식별자다. |
| `reason` | String | Y | 앞뒤 공백 제거 뒤 1~500자인 생성 사유다. 빈 문자열 또는 공백만으로 된 값은 허용하지 않으며 성공 감사 이력에 기록한다. |

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
  "message": "스탬프북 생성에 성공했습니다.",
  "data": {
    "stampbookId": "101",
    "status": "DRAFT",
    "targetCount": 2,
    "createdAt": "2026-08-06T02:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `201`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbookId` | String | 생성된 스탬프북 식별자다. |
| `data.status` | String | 항상 초기 상태 `DRAFT`다. |
| `data.targetCount` | Integer | 중복 제거가 아닌 검증을 통과한 요청 대상 콘텐츠 수다. |
| `data.createdAt` | String | 생성과 성공 감사 이력 기록 시각이다. UTC ISO 8601 형식이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_INPUT` | 필수값 누락, 식별자 범위 위반, 빈·중복 콘텐츠 배열 또는 사유 형식 위반이다. 스탬프북·대상 연결·감사 이력을 생성하지 않는다. |
| `400` | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 스탬프북·대상 연결·감사 이력을 생성하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 스탬프북·대상 연결·감사 이력을 생성하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 승인된 `OPERATOR`가 아니거나 요청 지역·콘텐츠의 담당 범위를 벗어난다. 스탬프북·대상 연결·감사 이력을 생성하지 않는다. |
| `404` | `NOT_FOUND` | 요청 지역, 대상 콘텐츠 또는 보상 쿠폰 정책이 없다. 스탬프북·대상 연결·감사 이력을 생성하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류가 발생했다. 트랜잭션이 커밋되지 않은 경우 스탬프북·대상 연결·감사 이력을 생성하지 않는다. |

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

1. 인증 주체는 활성·승인된 `OPERATOR`이고, `regionId`의 담당 지역 및 모든 `contentIds`의 소유 운영자여야 한다.
2. 모든 대상 콘텐츠의 지역은 `regionId`와 같아야 한다. 다른 지역·회차 단위·지역 전체 자동 적용은 허용하지 않는다.
3. 보상 쿠폰 정책은 `regionId`와 같고 발급 경로가 `STAMPBOOK_COMPLETION`이어야 한다.
4. 스탬프북, 모든 `stampbook_content` 연결과 서버가 부여한 `requestId`를 포함한 `STAMPBOOK` 생성 감사 이력은 하나의 트랜잭션으로 생성한다.
5. 생성된 스탬프북은 `DRAFT`이며 방문자에게 노출하거나 신규 적립 대상으로 사용하지 않는다.
