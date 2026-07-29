# 지역·콘텐츠 카탈로그 내 콘텐츠 회차 수정 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | FR-03, AUTH-01, SES-01 |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

승인된 운영자가 자신이 소유한 `PENDING` 콘텐츠의 `SCHEDULED` 회차를 전체 교체 방식으로 수정한다. 공개 회차의
수정 가능 필드는 P0에서 확정되지 않았으므로 `PUBLISHED` 콘텐츠의 회차는 수정할 수 없다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-03, AUTH-01, SES-01 | `PUT /api/v1/operator/contents/{contentId}/sessions/{sessionId}` | `content`, `content_session`, `user_role_assignment` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 요청·응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `OPERATOR` 역할, 담당 지역과 콘텐츠 지역의 일치, 콘텐츠 소유 관계가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 수정된 회차를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 수정이므로 적용하지 않는다. |

## 3. 내 콘텐츠 회차 수정

경로의 `contentId`와 `sessionId`가 같은 소유 콘텐츠·회차 관계인지 확인한 뒤 회차 일정, 체크인 창과 정원을
교체한다. `PENDING` 콘텐츠에는 예약이 존재하지 않으므로 수정 뒤 `remainingCapacity`는 새 `capacity`와 같다.

### Request

```http
PUT /api/v1/operator/contents/{contentId}/sessions/{sessionId}
```

#### Request Example

```http
PUT /api/v1/operator/contents/10/sessions/21 HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "startsAt": "2026-08-15T10:00:00+09:00",
  "endsAt": "2026-08-15T12:00:00+09:00",
  "checkinOpenAt": "2026-08-15T09:30:00+09:00",
  "checkinCloseAt": "2026-08-15T12:30:00+09:00",
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
| --- | --- | --- | --- |
| `contentId` | Long | Y | 콘텐츠 식별자. 양의 정수다. |
| `sessionId` | Long | Y | 회차 식별자. 양의 정수다. |

#### Query Parameter

없음.

#### Request Body

```json
{
  "startsAt": "2026-08-15T10:00:00+09:00",
  "endsAt": "2026-08-15T12:00:00+09:00",
  "checkinOpenAt": "2026-08-15T09:30:00+09:00",
  "checkinCloseAt": "2026-08-15T12:30:00+09:00",
  "capacity": 30
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `startsAt` | String | Y | ISO 8601 오프셋 일시다. `endsAt`보다 앞서야 한다. |
| `endsAt` | String | Y | ISO 8601 오프셋 일시다. `startsAt`보다 뒤고 `checkinCloseAt`보다 늦지 않아야 한다. |
| `checkinOpenAt` | String | Y | ISO 8601 오프셋 일시다. `checkinCloseAt`보다 앞서야 한다. |
| `checkinCloseAt` | String | Y | ISO 8601 오프셋 일시다. `endsAt`보다 이르면 안 된다. |
| `capacity` | Integer | Y | 1 이상의 정수다. |

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
  "message": "콘텐츠 회차 수정에 성공했습니다.",
  "data": {
    "sessionId": 21,
    "contentId": 10,
    "status": "SCHEDULED",
    "startsAt": "2026-08-15T10:00:00+09:00",
    "endsAt": "2026-08-15T12:00:00+09:00",
    "checkinOpenAt": "2026-08-15T09:30:00+09:00",
    "checkinCloseAt": "2026-08-15T12:30:00+09:00",
    "capacity": 30,
    "remainingCapacity": 30
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 `콘텐츠 회차 수정에 성공했습니다.` |
| `data.sessionId` | Long | 수정한 회차 식별자 |
| `data.contentId` | Long | 회차가 속한 콘텐츠 식별자 |
| `data.status` | String | 수정 후에도 유지되는 `SCHEDULED` |
| `data.startsAt` | String | 수정된 회차 시작 시각. ISO 8601 오프셋 일시다. |
| `data.endsAt` | String | 수정된 회차 종료 시각. ISO 8601 오프셋 일시다. |
| `data.checkinOpenAt` | String | 수정된 체크인 시작 시각. ISO 8601 오프셋 일시다. |
| `data.checkinCloseAt` | String | 수정된 체크인 종료 시각. ISO 8601 오프셋 일시다. |
| `data.capacity` | Integer | 수정된 총 정원 |
| `data.remainingCapacity` | Integer | `PENDING` 콘텐츠 회차이므로 `capacity`와 같은 잔여 정원 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 경로 식별자가 양의 정수가 아니거나 일정·체크인 창·정원 규칙을 위반했다. 회차는 변경되지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 회차는 변경되지 않는다. |
| 400 | `INVALID_TYPE` | 경로 식별자를 정수로 변환할 수 없다. 회차는 변경되지 않는다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. 회차는 변경되지 않는다. |
| 403 | `FORBIDDEN` | `OPERATOR` 역할, 담당 지역 또는 콘텐츠 소유 관계가 없다. 회차는 변경되지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠·회차가 없거나 회차가 해당 콘텐츠에 속하지 않는다. 회차는 변경되지 않는다. |
| 409 | `CONTENT_NOT_EDITABLE` | 콘텐츠가 `PENDING`이 아니거나 삭제되었다. 회차는 변경되지 않는다. |
| 409 | `SESSION_NOT_EDITABLE` | 회차가 `SCHEDULED`가 아니다. 회차는 변경되지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 409,
  "code": "SESSION_NOT_EDITABLE",
  "message": "수정할 수 없는 회차 상태입니다.",
  "data": null
}
```
