# 지역·콘텐츠 카탈로그 내 콘텐츠 회차 생성 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-03`, `FR-04`, `AUTH-01`, `SES-01`, `SES-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0038](../../../adr/0038-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 `APPROVED` 또는 `PUBLISHED` 콘텐츠에 다음 운영 일정을 추가하는 HTTP 계약을 구체화한다. 서버는
`content_session`을 `PENDING`으로 생성하고 `remainingCapacity = capacity`로 초기화한다. `PENDING` 회차는
공개·예약 대상이 아니며, 담당 지역 관리자의 승인 뒤에만 `SCHEDULED`가 된다.

요청·응답의 공통 형식, 인증, 페이지네이션, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 해당 API에만 적용되는 값과 규칙만 작성한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-03`, `FR-04`, `AUTH-01`, `SES-01`, `SES-02` | `POST /api/v1/operator/contents/{contentId}/sessions` | `content`, `content_session`, `user_role_assignment`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 요청·응답은 `application/json; charset=UTF-8`이다. 일정 시각, 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `OPERATOR` 역할, 담당 지역과 콘텐츠 지역의 일치, 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `201 Created`와 심사 대기 회차를 반환한다. 오류 코드는 공통 `ErrorCode`만 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 생성이므로 적용하지 않는다. |

## 3. 내 콘텐츠 회차 생성

소유 운영자는 소프트 삭제되지 않은 `APPROVED` 또는 `PUBLISHED` 콘텐츠에만 새 회차를 생성할 수 있다.
`PENDING` 콘텐츠의 최초 회차는 콘텐츠 승인 요청에 함께 제출하므로 이 API를 사용할 수 없다. 생성 회차의 심사는
[심사 대기 회차 목록](list-pending-sessions.md), [회차 승인](approve-session.md), [회차 반려](reject-session.md) 계약을 따른다.

### Request

```http
POST /api/v1/operator/contents/{contentId}/sessions
```

#### Request Example

```http
POST /api/v1/operator/contents/10/sessions HTTP/1.1
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
| `contentId` | String | Y | API 공통 규칙을 따르는 콘텐츠 식별자다. |

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

1. 인증 주체가 활성 `OPERATOR`인지, 콘텐츠의 소유 운영자이고 담당 지역이 콘텐츠 지역과 일치하는지 확인한다.
2. 콘텐츠가 존재하고 소프트 삭제되지 않았으며 상태가 `APPROVED` 또는 `PUBLISHED`인지 확인한다.
3. 일정이 현재 시각과 콘텐츠의 `publish_at` 이후인지, 체크인 창·정원이 유효한지 검증한 뒤 `content_session`을
   `status = PENDING`으로 생성한다. `region_id`는 콘텐츠의 `region_id`와 같고 `remaining_capacity`는 `capacity`와 같다.
4. 회차 행, `PENDING` 생성 상태 전이 감사 이벤트와 처리자 연결을 하나의 트랜잭션으로 커밋한다. 홀드·예약은 생성하지 않는다.
5. 지역 관리자 승인 때만 회차를 `SCHEDULED`로 전이한다. `PENDING`·`REJECTED` 회차는 공개 목록, 예약 정보,
   정원 홀드·예약 처리에서 제외한다.

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
  "message": "콘텐츠 회차 생성에 성공했습니다.",
  "data": {
    "sessionId": "21",
    "contentId": "10",
    "status": "PENDING",
    "startsAt": "2026-08-22T10:00:00+09:00",
    "endsAt": "2026-08-22T12:00:00+09:00",
    "checkinOpenAt": "2026-08-22T09:30:00+09:00",
    "checkinCloseAt": "2026-08-22T12:30:00+09:00",
    "capacity": 30,
    "remainingCapacity": 30,
    "createdAt": "2026-08-01T01:00:00Z"
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `201` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `콘텐츠 회차 생성에 성공했습니다.` |
| `data.sessionId` | String | API 공통 규칙에 따른 새 회차 식별자 |
| `data.contentId` | String | API 공통 규칙에 따른 대상 콘텐츠 식별자 |
| `data.status` | String | 생성 직후 심사 대기 상태 `PENDING` |
| `data.startsAt` | String | 회차 시작 일정 시각 |
| `data.endsAt` | String | 회차 종료 일정 시각 |
| `data.checkinOpenAt` | String | 체크인 시작 일정 시각 |
| `data.checkinCloseAt` | String | 체크인 종료 일정 시각 |
| `data.capacity` | Integer | 총정원 |
| `data.remainingCapacity` | Integer | 생성 직후 `capacity`와 같은 잔여 정원 |
| `data.createdAt` | String | 회차 생성 사건 시각. API 공통 규칙에 따른 UTC ISO 8601 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 콘텐츠 식별자가 양의 정수가 아니거나 일정·체크인 창·정원 규칙을 위반했다. 회차와 감사 기록은 생성되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 회차와 감사 기록은 생성되지 않는다. |
| 400 | `INVALID_TYPE` | 콘텐츠 식별자를 정수로 변환할 수 없다. 회차와 감사 기록은 생성되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 회차와 감사 기록은 생성되지 않는다. |
| 403 | `FORBIDDEN` | `OPERATOR` 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 회차와 감사 기록은 생성되지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠가 없거나 소프트 삭제됐거나 생성 대상 상태(`APPROVED`, `PUBLISHED`)가 아니다. 회차와 감사 기록은 생성되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
