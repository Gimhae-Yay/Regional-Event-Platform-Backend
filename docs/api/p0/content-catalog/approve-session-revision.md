# 회차 수정 요청 승인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-02`, `CON-09` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ADR-0031](../../../adr/0031-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 `PENDING` 회차 수정 요청을 승인한다. 심사 중에도 기존 회차는 유지되며, 승인 시점의 모든 정합성 조건을
만족할 때만 후보 일정·체크인 창·정원을 실제 `SCHEDULED` 회차에 반영한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-04`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-02`, `CON-09` | `POST /api/v1/region-admin/session-revisions/{revisionId}/approve` | `content`, `content_session`, `session_revision`, `capacity_hold`, `reservation`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고, 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN` 역할과 담당 지역 일치가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 `SESSION_STATE_CONFLICT`를 포함한 공통 오류 코드를 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 명령이므로 적용하지 않는다. |

## 3. 회차 수정 요청 승인

### Request

```http
POST /api/v1/region-admin/session-revisions/{revisionId}/approve
```

#### Request Example

```http
POST /api/v1/region-admin/session-revisions/52/approve HTTP/1.1
Authorization: Bearer {accessToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 담당 지역 관리자 Access Token |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `revisionId` | String | Y | 승인할 수정 요청 식별자. 양의 10진 문자열이다. |

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
  "message": "회차 수정 요청 승인에 성공했습니다.",
  "data": {
    "revisionId": "52",
    "revisionStatus": "APPROVED",
    "contentId": "10",
    "targetSessionId": "21",
    "sessionVersion": 4,
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
| `data.revisionId` | String | 승인된 수정 요청 식별자 |
| `data.revisionStatus` | String | 승인 뒤 상태 `APPROVED` |
| `data.contentId` | String | 대상 콘텐츠 식별자 |
| `data.targetSessionId` | String | 수정된 실제 회차 식별자 |
| `data.sessionVersion` | Integer | 후보 반영 뒤 증가한 실제 회차 버전 |
| `data.reviewedAt` | String | 승인 처리 시각. UTC ISO 8601 일시 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `revisionId`가 양의 10진 문자열이 아니다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| 403 | `FORBIDDEN` | 담당 지역 관리자 역할이 없거나 담당 지역이 다르다. |
| 404 | `NOT_FOUND` | 수정 요청이 없거나 콘텐츠가 소프트 삭제됐다. |
| 409 | `SESSION_STATE_CONFLICT` | 수정 요청이 `PENDING`이 아니거나, 콘텐츠가 `APPROVED`·`PUBLISHED`가 아니거나, 대상 회차가 `SCHEDULED`가 아니거나, 기준 버전이 다르거나, 활성 홀드·`CONFIRMED` 예약이 있거나 다른 심사가 먼저 종결됐다. 수정 요청·실제 회차·감사 기록은 변경되지 않으며 최신 상태를 확인해야 한다. |

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
2. 같은 MySQL 트랜잭션에서 수정 요청이 `PENDING`인지, 콘텐츠가 소프트 삭제되지 않은 `APPROVED` 또는 `PUBLISHED`인지 확인한다.
3. 대상 회차가 `SCHEDULED`이고 `content_session.version_no = session_revision.base_session_version`인지 확인한다.
4. 대상 회차에 활성 `capacity_hold`와 `CONFIRMED` 예약이 없는지 확인한다.
5. 모든 조건을 만족하면 후보 일정·체크인 창·정원을 실제 회차에 반영하고 `version_no`를 증가시킨다. 수정 요청은 `PENDING → APPROVED`로 전이하며 `reviewed_at`, `reviewed_by_user_id`를 기록한다.
6. 실제 회차 반영, 수정 요청 종결, 성공 감사 이벤트와 처리자 연결을 하나의 트랜잭션으로 커밋한다.
