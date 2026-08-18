# 내 스탬프북 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-02](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-04` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0104](../../../adr/0104-store-stampbook-title-as-a-not-null-intrinsic-field.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 회원이 공개 중인 스탬프북과 본인의 종료된 진행 이력을 조회한다. 적립이 아직 없어 `stampbook_progress`가 없는 공개
스탬프북은 응답 전용 `NOT_STARTED` 진행도로 반환하며, 공개 종료 뒤에는 본인의 완료·미완료 진행 이력만 유지한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-02, STB-04 | `GET /api/v1/me/stampbooks` | `stampbook_progress`, `stampbook`, `stampbook_content`, `stamp_earn`, `stampbook_reward_grant`, `coupon_policy` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다. 이 API는 단순 목록이므로
페이지·커서·총 건수·상태 필터와 사용자 지정 정렬을 제공하지 않는다.

## 3. 내 스탬프북 목록 조회

### Request

```http
GET /api/v1/me/stampbooks
```

#### Request Example

```http
GET /api/v1/me/stampbooks HTTP/1.1
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

없음.

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
  "message": "내 스탬프북 목록 조회에 성공했습니다.",
  "data": {
    "stampbooks": [
      {
        "stampbookId": "101",
        "title": "김해 가야 문화 완주",
        "regionId": "1",
        "status": "PUBLISHED",
        "publishedAt": "2026-08-01T01:00:00Z",
        "progress": {
          "status": "IN_PROGRESS",
          "earnedCount": 2,
          "targetCount": 4,
          "completedAt": null,
          "lastEarnedAt": "2026-08-06T01:00:00Z",
          "completionReward": null
        }
      },
      {
        "stampbookId": "102",
        "title": "김해 역사 산책",
        "regionId": "1",
        "status": "ENDED",
        "publishedAt": "2026-07-01T01:00:00Z",
        "progress": {
          "status": "COMPLETED",
          "earnedCount": 3,
          "targetCount": 3,
          "completedAt": "2026-07-12T01:00:00Z",
          "lastEarnedAt": "2026-07-12T01:00:00Z",
          "completionReward": {
            "couponPolicyId": "501",
            "stampbookRewardGrantId": "9001"
          }
        }
      }
    ]
  }
}
```

결과가 없으면 `200 OK`와 `data.stampbooks: []`를 반환한다.

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbooks` | Array | 공개 중인 스탬프북과 인증 회원의 종료된 진행 이력 배열이다. 결과가 없으면 빈 배열 `[]`이다. |
| `data.stampbooks[].stampbookId` | String | 양의 10진 문자열인 스탬프북 식별자다. |
| `data.stampbooks[].title` | String | 스탬프북에 저장된 고유 제목이다. 앞뒤 공백 제거 뒤 1~100자이며 대상 콘텐츠 제목으로 조합하지 않는다. |
| `data.stampbooks[].regionId` | String | 스탬프북 소속 지역 식별자다. |
| `data.stampbooks[].status` | String | 스탬프북 상태다. 조회 대상에서는 `PUBLISHED`, `ENDED` 중 하나다. |
| `data.stampbooks[].publishedAt` | String | 스탬프북 공개 승인 시각이다. UTC ISO 8601 형식이다. |
| `data.stampbooks[].progress.status` | String | 사용자 진행 상태다. 진행 행이 없으면 응답 전용 `NOT_STARTED`이고, 그 외에는 `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나다. |
| `data.stampbooks[].progress.earnedCount` | Integer | 서로 다른 대상 콘텐츠에 대해 적립된 스탬프 수다. |
| `data.stampbooks[].progress.targetCount` | Integer | 스탬프북에 지정된 대상 콘텐츠 수이자 완료 목표 수다. |
| `data.stampbooks[].progress.completedAt` | String 또는 null | 진행 상태가 `COMPLETED`이면 완료 시각이고, 그 외에는 `null`이다. UTC ISO 8601 형식이다. |
| `data.stampbooks[].progress.lastEarnedAt` | String 또는 null | 해당 진행의 가장 최근 스탬프 적립 시각이다. `NOT_STARTED`이면 `null`이다. UTC ISO 8601 형식이다. |
| `data.stampbooks[].progress.completionReward` | Object 또는 null | 응답에는 항상 포함한다. 본인 진행이 `COMPLETED`일 때만 완료 보상 쿠폰 발급에 필요한 식별자를 함께 담는 객체이고, 그 외 상태에서는 `null`이다. |
| `data.stampbooks[].progress.completionReward.couponPolicyId` | String | 완료 보상 쿠폰 정책 식별자다. 상위 객체가 `null`이면 이 필드는 반환하지 않는다. 기존 [쿠폰 발급 요청](../coupon/issue-coupon.md#13-쿠폰-발급-요청)의 경로 `couponPolicyId`로 사용한다. |
| `data.stampbooks[].progress.completionReward.stampbookRewardGrantId` | String | 본인 완료 보상 근거 식별자다. 상위 객체가 `null`이면 이 필드는 반환하지 않는다. 쿠폰 발급 요청 본문의 `issueSourceType = STAMPBOOK_COMPLETION`일 때 `sourceId`로 사용한다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 진행 정보는 반환하지 않으며 유효한 Token으로 재시도할 수 있다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니다. 진행 정보는 반환하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 스탬프북·진행·적립·완료 보상 근거·쿠폰 정책 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증 정보가 없거나 유효하지 않습니다.",
  "data": null
}
```

### 처리 규칙

1. 서버는 현재 `PUBLISHED`인 스탬프북과 `stampbook_progress.user_id = 인증 회원 식별자`인 종료 스탬프북을 반환한다. 다른 회원의 종료 이력은 반환하지 않는다.
2. 목록은 `publishedAt` 내림차순, 같은 시각이면 `stampbookId` 내림차순으로 고정 정렬한다.
3. `earnedCount`는 해당 진행에 연결된 `stamp_earn` 행 수이고, 진행 행이 없으면 `0`이다. `targetCount`는 `stampbook_content` 행 수다.
4. 진행 행이 없는 공개 스탬프북은 `progress.status = NOT_STARTED`, `completedAt = null`, `lastEarnedAt = null`, `completionReward = null`으로 응답한다. `NOT_STARTED`는 저장하지 않는 응답 전용 상태다.
5. `COMPLETED` 진행은 스탬프북이 `ENDED`가 된 뒤에도 `COMPLETED` 상태를 유지한다. 종료 시 미완료 진행만 `ENDED_INCOMPLETE`로 보존한다.
6. 본인 진행이 `COMPLETED`이면 `completionReward`는 해당 진행의 단일 `stampbook_reward_grant`와 그 행의 `coupon_policy_id`를 사용해 `couponPolicyId`, `stampbookRewardGrantId`를 함께 반환한다. 둘 중 하나가 없거나 스탬프북의 완료 보상 정책·진행 소유자와 일치하지 않으면 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
7. `NOT_STARTED`, `IN_PROGRESS`, `ENDED_INCOMPLETE` 진행은 `completionReward = null`이다. 목록은 인증 회원의 진행만 조립하므로 다른 회원의 `COMPLETED` 진행과 완료 보상 근거는 반환하지 않는다.
8. 이 API는 단순 목록이다. 페이지·커서·총 건수·상태 필터와 사용자 지정 정렬을 제공하지 않는다.
9. 조회 시 스탬프북, 진행도, 적립 이력, 완료 보상 근거, 쿠폰과 감사 이력을 생성·수정·삭제하지 않는다.
