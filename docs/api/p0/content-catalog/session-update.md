# 지역·콘텐츠 카탈로그 내 콘텐츠 회차 수정 요청 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `FR-04`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ADR-0031](../../../adr/0031-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 이미 운영 중인 `SCHEDULED` 회차의 일정·체크인 창·정원 변경안을 제출하는 HTTP 계약을 구체화한다.
변경안은 수정 전용 `session_revision`에 `PENDING`으로 저장하고, 심사 중인 기존 회차는 일정·정원·공개·예약 가능
상태를 그대로 유지한다.

지역 관리자가 변경안을 반려하면 요청만 `REJECTED`로 종결하고 기존 회차는 그대로 진행한다. 승인 때에는 콘텐츠와
회차 상태, 회차 버전, 활성 홀드와 `CONFIRMED` 예약을 다시 확인한다. 하나라도 만족하지 않으면 변경안을 승인하지
않으며 기존 회차를 유지한다.

요청·응답의 공통 형식, 인증, 페이지네이션, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 해당 API에만 적용되는 값과 규칙만 작성한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03`, `FR-04`, `AUTH-01`, `SES-01`, `SES-02`, `RSV-02` | `POST /api/v1/operator/sessions/{sessionId}/change-requests` | `content`, `content_session`, `session_revision`, `user_role_assignment`, `capacity_hold`, `reservation`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 요청·응답은 `application/json; charset=UTF-8`이다. 일정 시각, 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `OPERATOR` 역할, 담당 지역과 회차 콘텐츠 지역의 일치, 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 심사 대기 수정 요청을 반환한다. 오류 코드는 공통 `ErrorCode`만 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 내 콘텐츠 회차 수정 요청

소유 운영자는 소프트 삭제되지 않은 `APPROVED` 또는 `PUBLISHED` 콘텐츠의 `SCHEDULED` 회차에만 변경안을 제출할 수
있다. 콘텐츠가 `PENDING`일 때는 최초 회차가 콘텐츠 승인 요청에 포함되므로 이 API를 사용할 수 없다. 대상 회차당
`PENDING` 수정 요청은 하나만 둘 수 있다.

### Request

```http
POST /api/v1/operator/sessions/{sessionId}/change-requests
```

#### Request Example

```http
POST /api/v1/operator/sessions/21/change-requests HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "startsAt": "2026-08-22T10:00:00+09:00",
  "endsAt": "2026-08-22T12:00:00+09:00",
  "checkinOpenAt": "2026-08-22T09:30:00+09:00",
  "checkinCloseAt": "2026-08-22T12:30:00+09:00",
  "capacity": 30
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer {accessToken}` 형식의 유효한 Access Token |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- |
| `sessionId` | String | Y | API 공통 규칙을 따르는 수정 대상 회차 식별자다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "startsAt": "2026-08-22T10:00:00+09:00",
  "endsAt": "2026-08-22T12:00:00+09:00",
  "checkinOpenAt": "2026-08-22T09:30:00+09:00",
  "checkinCloseAt": "2026-08-22T12:30:00+09:00",
  "capacity": 30
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- |
| `startsAt` | String | Y | API 공통 규칙에 따른 `Asia/Seoul` 일정 시각이다. 현재 시각과 콘텐츠의 `publishAt` 이후이고 `endsAt`보다 앞서야 한다. |
| `endsAt` | String | Y | API 공통 규칙에 따른 `Asia/Seoul` 일정 시각이다. `startsAt`보다 뒤고 `checkinCloseAt`보다 늦지 않아야 한다. |
| `checkinOpenAt` | String | Y | API 공통 규칙에 따른 `Asia/Seoul` 일정 시각이다. `checkinCloseAt`보다 앞서야 한다. |
| `checkinCloseAt` | String | Y | API 공통 규칙에 따른 `Asia/Seoul` 일정 시각이다. `endsAt`보다 이르면 안 된다. |
| `capacity` | Integer | Y | 1 이상의 정수다. |

### 처리 규칙

1. 인증 주체가 활성 `OPERATOR`인지, 회차 콘텐츠의 소유 운영자이고 담당 지역이 콘텐츠·회차 지역과 일치하는지 확인한다.
2. 콘텐츠가 소프트 삭제되지 않은 `APPROVED` 또는 `PUBLISHED`인지, 대상 회차가 `SCHEDULED`인지 확인한다.
3. 일정이 현재 시각과 콘텐츠의 `publish_at` 이후인지, 체크인 창·정원이 유효한지 확인하고, 대상 회차에 `PENDING`
   수정 요청이 없는지 확인한다.
4. 현재 회차의 `version_no`를 `base_session_version`으로 복사하고 `session_revision`을 `status = PENDING`으로
   생성한다. 이 API는 기존 `content_session`을 변경하지 않는다.
5. 요청 행, 수정 요청 감사 이벤트와 처리자 연결을 하나의 트랜잭션으로 커밋한다.
6. 지역 관리자 승인 때만 콘텐츠 상태·소프트 삭제 여부, 대상 회차 상태·버전, 활성 홀드와 `CONFIRMED` 예약을 같은
   트랜잭션에서 다시 확인한다. 모두 만족하면 후보 값을 반영하고 `content_session.version_no`를 증가시킨다.
   심사는 [심사 대기 회차 수정 요청 목록](list-pending-session-revisions.md),
   [회차 수정 요청 승인](approve-session-revision.md), [회차 수정 요청 반려](reject-session-revision.md) 계약을 따른다.

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
  "message": "콘텐츠 회차 수정 요청에 성공했습니다.",
  "data": {
    "revisionId": "52",
    "status": "PENDING",
    "contentId": "10",
    "targetSessionId": "21",
    "baseSessionVersion": 3,
    "startsAt": "2026-08-22T10:00:00+09:00",
    "endsAt": "2026-08-22T12:00:00+09:00",
    "checkinOpenAt": "2026-08-22T09:30:00+09:00",
    "checkinCloseAt": "2026-08-22T12:30:00+09:00",
    "capacity": 30,
    "requestedAt": "2026-08-01T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `콘텐츠 회차 수정 요청에 성공했습니다.` |
| `data.revisionId` | String | API 공통 규칙에 따른 회차 수정 요청 식별자 |
| `data.status` | String | 심사 대기 상태 `PENDING` |
| `data.contentId` | String | API 공통 규칙에 따른 대상 콘텐츠 식별자 |
| `data.targetSessionId` | String | API 공통 규칙에 따른 수정 대상 회차 식별자 |
| `data.baseSessionVersion` | Integer | 요청 생성 시 복사한 대상 회차 버전. 승인 때 현재 버전과 일치해야 한다. |
| `data.startsAt` | String | 후보 회차 시작 일정 시각 |
| `data.endsAt` | String | 후보 회차 종료 일정 시각 |
| `data.checkinOpenAt` | String | 후보 체크인 시작 일정 시각 |
| `data.checkinCloseAt` | String | 후보 체크인 종료 일정 시각 |
| `data.capacity` | Integer | 후보 총정원 |
| `data.requestedAt` | String | 수정 요청 사건 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 회차 식별자가 양의 정수가 아니거나 일정·체크인 창·정원 규칙을 위반했다. 요청과 감사 기록은 생성되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 요청과 감사 기록은 생성되지 않는다. |
| 400 | `INVALID_TYPE` | 회차 식별자를 정수로 변환할 수 없다. 요청과 감사 기록은 생성되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 요청과 감사 기록은 생성되지 않는다. |
| 403 | `FORBIDDEN` | `OPERATOR` 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 요청과 감사 기록은 생성되지 않는다. |
| 404 | `NOT_FOUND` | 회차가 없거나 콘텐츠가 소프트 삭제됐다. 요청과 감사 기록은 생성되지 않는다. |
| 409 | `SESSION_STATE_CONFLICT` | 대상 회차가 `SCHEDULED`가 아니거나 콘텐츠가 수정 요청 대상 상태(`APPROVED`, `PUBLISHED`)가 아니거나, 대상 회차에 이미 `PENDING` 수정 요청이 있거나 다른 상태 전이가 먼저 처리됐다. 요청과 감사 기록은 생성되지 않으며 최신 상태를 확인해야 한다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "SESSION_STATE_CONFLICT",
  "message": "회차 상태가 요청을 처리할 수 없습니다.",
  "data": null
}
```
