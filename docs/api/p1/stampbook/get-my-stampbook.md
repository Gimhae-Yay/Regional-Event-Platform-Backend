# 내 스탬프북 상세·진행도 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-02](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `STB-04` |
| 소유 도메인 | 스탬프북 |
| 기준 문서 | [스탬프북 API](stampbook.md), [스탬프북](../../../p1/stampbook.md), [P1 ERD](../../../p1-erd.md), [ADR-0067](../../../adr/0067-model-stampbook-and-mission-progress-from-immutable-visits.md), [ADR-0104](../../../adr/0104-store-stampbook-title-as-a-not-null-intrinsic-field.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

활성 회원이 공개 중인 스탬프북 또는 본인 진행 이력이 있는 종료 스탬프북의 대상 콘텐츠별 적립 여부와 현재 진행도를 조회한다.
스탬프북이 종료된 뒤에도 본인의 진행과 적립 근거는 조회할 수 있지만, 종료 뒤에는 새 적립이 발생하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-02, STB-04 | `GET /api/v1/me/stampbooks/{stampbookId}` | `stampbook`, `stampbook_content`, `stampbook_progress`, `stamp_earn`, `stampbook_reward_grant`, `coupon_policy`, `content` |

## 2. 공통 계약 참조

조회·응답·오류 규칙은 [스탬프북 API](stampbook.md#2-공통-계약-참조)를 따른다.

## 3. 내 스탬프북 상세·진행도 조회

### Request

```http
GET /api/v1/me/stampbooks/{stampbookId}
```

#### Request Example

```http
GET /api/v1/me/stampbooks/101 HTTP/1.1
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
  "message": "내 스탬프북 상세 조회에 성공했습니다.",
  "data": {
    "stampbook": {
      "stampbookId": "101",
      "title": "김해 가야 문화 완주",
      "regionId": "1",
      "status": "PUBLISHED",
      "publishedAt": "2026-08-01T01:00:00Z",
      "endedAt": null,
      "targetContents": [
        {
          "contentId": "201",
          "title": "김해 가야문화 체험",
          "earned": true,
          "earnedAt": "2026-08-06T01:00:00Z"
        },
        {
          "contentId": "202",
          "title": "대성동고분박물관 해설",
          "earned": true,
          "earnedAt": "2026-08-07T01:00:00Z"
        }
      ]
    },
    "progress": {
      "status": "COMPLETED",
      "earnedCount": 2,
      "targetCount": 2,
      "completedAt": "2026-08-07T01:00:00Z",
      "completionReward": {
        "couponPolicyId": "501",
        "stampbookRewardGrantId": "9001"
      }
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Number | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지다. |
| `data.stampbook.stampbookId` | String | 양의 10진 문자열인 스탬프북 식별자다. |
| `data.stampbook.title` | String | 스탬프북에 저장된 고유 제목이다. 앞뒤 공백 제거 뒤 1~100자이며 대상 콘텐츠 제목으로 조합하지 않는다. |
| `data.stampbook.regionId` | String | 스탬프북 소속 지역 식별자다. |
| `data.stampbook.status` | String | 스탬프북 상태다. `PUBLISHED`, `ENDED` 중 하나다. |
| `data.stampbook.publishedAt` | String | 공개 승인 시각이다. UTC ISO 8601 형식이다. |
| `data.stampbook.endedAt` | String 또는 null | `ENDED` 상태의 종료 시각이며, 공개 중이면 `null`이다. UTC ISO 8601 형식이다. |
| `data.stampbook.targetContents` | Array | 스탬프 적립 대상 콘텐츠 배열이다. |
| `data.stampbook.targetContents[].contentId` | String | 대상 콘텐츠 식별자다. |
| `data.stampbook.targetContents[].title` | String | 대상 콘텐츠의 현재 제목이다. |
| `data.stampbook.targetContents[].earned` | Boolean | 인증 회원이 해당 콘텐츠에서 스탬프를 적립했으면 `true`다. |
| `data.stampbook.targetContents[].earnedAt` | String 또는 null | `earned = true`이면 해당 콘텐츠의 스탬프 적립 시각이고, 그 외에는 `null`이다. UTC ISO 8601 형식이다. |
| `data.progress.status` | String | 사용자 진행 상태다. 진행 행이 없으면 응답 전용 `NOT_STARTED`이고, 그 외에는 `IN_PROGRESS`, `COMPLETED`, `ENDED_INCOMPLETE` 중 하나다. |
| `data.progress.earnedCount` | Integer | 적립한 서로 다른 대상 콘텐츠 수다. |
| `data.progress.targetCount` | Integer | 대상 콘텐츠 수이자 완료 목표 수다. |
| `data.progress.completedAt` | String 또는 null | 진행 상태가 `COMPLETED`이면 완료 시각이고, 그 외에는 `null`이다. UTC ISO 8601 형식이다. |
| `data.progress.completionReward` | Object 또는 null | 응답에는 항상 포함한다. 본인 진행이 `COMPLETED`일 때만 완료 보상 쿠폰 발급에 필요한 식별자를 함께 담는 객체이고, 그 외 상태에서는 `null`이다. |
| `data.progress.completionReward.couponPolicyId` | String | 완료 보상 쿠폰 정책 식별자다. 상위 객체가 `null`이면 이 필드는 반환하지 않는다. 기존 [쿠폰 발급 요청](../coupon/issue-coupon.md#13-쿠폰-발급-요청)의 경로 `couponPolicyId`로 사용한다. |
| `data.progress.completionReward.stampbookRewardGrantId` | String | 본인 완료 보상 근거 식별자다. 상위 객체가 `null`이면 이 필드는 반환하지 않는다. 쿠폰 발급 요청 본문의 `issueSourceType = STAMPBOOK_COMPLETION`일 때 `sourceId`로 사용한다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| `400` | `INVALID_TYPE` | `stampbookId`를 양의 정수 식별자로 처리할 수 없다. 조회 상태를 변경하지 않으며 형식을 수정해 재시도할 수 있다. |
| `400` | `INVALID_INPUT` | `stampbookId`가 양의 10진 문자열 또는 signed 64비트 `Long` 범위를 만족하지 않는다. 조회 상태를 변경하지 않는다. |
| `401` | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 상세와 진행도를 반환하지 않는다. |
| `403` | `FORBIDDEN` | 인증 주체가 활성 회원이 아니거나 대상 스탬프북 진행의 소유자가 아니다. 상세와 적립 근거를 반환하지 않는다. |
| `404` | `NOT_FOUND` | 대상 스탬프북이 없다. 조회 상태를 변경하지 않는다. |
| `500` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 또는 스탬프북·대상 콘텐츠·진행·적립·완료 보상 근거·쿠폰 정책 연결 정합성 오류가 발생했다. 조회 상태를 변경하지 않는다. |

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

1. 서버는 대상 스탬프북이 `PUBLISHED`이거나 `stampbook_progress.user_id = 인증 회원 식별자`인 진행 행이 있는지 검증한다.
2. `PUBLISHED` 스탬프북은 적립 전에도 조회할 수 있다. 진행 행이 없으면 `progress.status = NOT_STARTED`, `earnedCount = 0`, `completedAt = null`, `completionReward = null`으로 응답하며 `NOT_STARTED`는 저장하지 않는 응답 전용 상태다.
3. `DRAFT`, `PENDING_REVIEW`, 다른 회원의 진행만 있는 `ENDED` 스탬프북은 `FORBIDDEN`을 반환한다.
4. 본인 진행이 `COMPLETED`이면 `completionReward`는 해당 진행의 단일 `stampbook_reward_grant`와 그 행의 `coupon_policy_id`를 사용해 `couponPolicyId`, `stampbookRewardGrantId`를 함께 반환한다. 둘 중 하나가 없거나 스탬프북의 완료 보상 정책·진행 소유자와 일치하지 않으면 정상 응답으로 대체하지 않고 정합성 오류로 처리한다.
5. `NOT_STARTED`, `IN_PROGRESS`, `ENDED_INCOMPLETE` 진행은 `completionReward = null`이다. 다른 회원의 진행 또는 완료 보상 근거는 응답에 포함하지 않는다. 다른 회원의 종료 진행만 있는 스탬프북은 규칙 3에 따라 `FORBIDDEN`을 반환한다.
6. 대상 콘텐츠는 `contentId` 오름차순으로 반환한다. 각 콘텐츠는 사용자 진행에 연결된 `stamp_earn.content_id`가 있을 때만 `earned = true`다.
7. 같은 콘텐츠의 다른 회차 방문은 추가 적립이 아니므로 대상 콘텐츠별 `earnedAt`은 최대 하나다.
8. `targetCount`는 `stampbook_content` 행 수이고, `earnedCount`는 해당 진행에 연결된 `stamp_earn` 행 수다.
9. 조회 시 스탬프북, 진행도, 적립 이력, 완료 보상 근거, 쿠폰과 감사 이력을 생성·수정·삭제하지 않는다.
