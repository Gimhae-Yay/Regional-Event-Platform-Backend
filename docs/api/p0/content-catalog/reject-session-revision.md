# 회차 수정 요청 반려 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `AUTH-01`, `SES-01`, `CON-09` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0038](../../../adr/0038-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 `PENDING` 회차 수정 요청을 사유와 함께 반려한다. 반려는 후보 요청만 `REJECTED`로 종결하며
기존 `SCHEDULED` 회차의 일정·체크인 창·정원·공개·예약 가능 상태는 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-04`, `AUTH-01`, `SES-01`, `CON-09` | `POST /api/v1/region-admin/session-revisions/{revisionId}/reject` | `content`, `content_session`, `session_revision`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고, 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN` 역할과 담당 지역 일치가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 `SESSION_STATE_CONFLICT`를 포함한 공통 오류 코드를 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 명령이므로 적용하지 않는다. |

## 3. 회차 수정 요청 반려

### Request

```http
POST /api/v1/region-admin/session-revisions/{revisionId}/reject
```

#### Request Example

```http
POST /api/v1/region-admin/session-revisions/52/reject HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "reason": "정원 변경 사유와 운영 계획을 보완해 주세요."
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 담당 지역 관리자 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `revisionId` | String | Y | 반려할 수정 요청 식별자. 양의 10진 문자열이다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "reason": "정원 변경 사유와 운영 계획을 보완해 주세요."
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `reason` | String | Y | 앞뒤 공백을 제거한 뒤 비어 있지 않은 반려 사유 |

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
  "message": "회차 수정 요청 반려에 성공했습니다.",
  "data": {
    "revisionId": "52",
    "revisionStatus": "REJECTED",
    "contentId": "10",
    "targetSessionId": "21",
    "rejectReason": "정원 변경 사유와 운영 계획을 보완해 주세요.",
    "reviewedAt": "2026-08-01T02:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.revisionId` | String | 반려된 수정 요청 식별자 |
| `data.revisionStatus` | String | 반려 뒤 상태 `REJECTED` |
| `data.contentId` | String | 대상 콘텐츠 식별자 |
| `data.targetSessionId` | String | 변경하지 않은 실제 회차 식별자 |
| `data.rejectReason` | String | 기록한 반려 사유 |
| `data.reviewedAt` | String | 반려 처리 시각. UTC ISO 8601 일시 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `revisionId`가 양의 10진 문자열이 아니거나 `reason`이 누락·공백이다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| 403 | `FORBIDDEN` | 담당 지역 관리자 역할이 없거나 담당 지역이 다르다. |
| 404 | `NOT_FOUND` | 수정 요청이 없거나 콘텐츠가 소프트 삭제됐다. |
| 409 | `SESSION_STATE_CONFLICT` | 수정 요청이 `PENDING`이 아니거나 다른 심사가 먼저 종결됐다. 수정 요청·실제 회차·감사 기록은 변경되지 않으며 최신 상태를 확인해야 한다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "SESSION_STATE_CONFLICT",
  "message": "회차 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```

### 처리 규칙

1. 수정 요청의 지역이 인증 주체의 담당 지역과 일치하는지 확인한다.
2. 수정 요청이 `PENDING`인지 조건부로 확인한다.
3. 조건을 만족하면 수정 요청만 `PENDING → REJECTED`로 전이하고 `reviewed_at`, `reviewed_by_user_id`, `reject_reason`을 기록한다.
4. 대상 `content_session`의 일정·체크인 창·정원·버전은 변경하지 않는다.
5. 수정 요청 상태 전이, 반려 사유, 성공 감사 이벤트와 처리자 연결을 하나의 트랜잭션으로 커밋한다.
