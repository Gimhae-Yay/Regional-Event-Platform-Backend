# 심사 대기 회차 상세 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `AUTH-01`, `SES-01`, `SES-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0038](../../../adr/0038-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 추가 생성된 `PENDING` 회차의 콘텐츠·운영자·일정·체크인 창·정원을 확인한다.
조회는 심사 결과나 실제 예약 상태를 변경하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-04`, `AUTH-01`, `SES-01`, `SES-02` | `GET /api/v1/region-admin/sessions/{sessionId}` | `content`, `content_session`, `app_user` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고, 일정 시각·사건 시각·식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_REGION_ADMIN` snapshot으로 1차 인가하고, DB에서 활성 `ORDINARY` 계정과 현재 담당 지역 일치를 확인한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 공통 오류 코드를 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 심사 대기 회차 상세 조회

### Request

```http
GET /api/v1/region-admin/sessions/{sessionId}
```

#### Request Example

```http
GET /api/v1/region-admin/sessions/21 HTTP/1.1
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
| `sessionId` | String | Y | 심사 대기 회차 식별자. 양의 10진 문자열이다. |

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
  "message": "심사 대기 회차 상세 조회에 성공했습니다.",
  "data": {
    "sessionId": "21",
    "contentId": "10",
    "contentTitle": "가야문화 체험",
    "contentStatus": "APPROVED",
    "status": "PENDING",
    "startsAt": "2026-08-22T10:00:00+09:00",
    "endsAt": "2026-08-22T12:00:00+09:00",
    "checkinOpenAt": "2026-08-22T09:30:00+09:00",
    "checkinCloseAt": "2026-08-22T12:30:00+09:00",
    "capacity": 30,
    "remainingCapacity": 30,
    "createdAt": "2026-08-01T01:00:00Z",
    "operator": {
      "operatorId": "20",
      "name": "김해운영"
    }
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.sessionId` | String | 회차 식별자 |
| `data.contentId`, `data.contentTitle` | String | 대상 콘텐츠 식별자와 제목 |
| `data.contentStatus` | String | 현재 콘텐츠 상태. `APPROVED` 또는 `PUBLISHED` |
| `data.status` | String | 항상 `PENDING` |
| `data.startsAt`~`data.checkinCloseAt` | String | 후보 일정과 체크인 창 |
| `data.capacity`, `data.remainingCapacity` | Integer | 총정원과 잔여 정원. 심사 대기 중 두 값은 같다. |
| `data.createdAt` | String | 회차 생성 사건 시각. UTC ISO 8601 일시 |
| `data.operator` | Object | 콘텐츠 소유 운영자 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `sessionId`가 양의 10진 문자열이 아니다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| 403 | `FORBIDDEN` | `ROLE_REGION_ADMIN` authority가 없거나 활성 `ORDINARY` 계정 또는 담당 지역이 다르다. |
| 404 | `NOT_FOUND` | 회차가 없거나 `PENDING`이 아니거나, 콘텐츠가 소프트 삭제됐거나 심사 대상 상태가 아니다. |
| 500 | `INTERNAL_SERVER_ERROR` | 회차·콘텐츠·지역 관계가 정책과 일치하지 않는다. |

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

1. 회차의 콘텐츠 지역이 인증 주체의 담당 지역과 일치해야 한다.
2. 추가 생성 심사 대상인 `PENDING` 회차와 소프트 삭제되지 않은 `APPROVED` 또는 `PUBLISHED` 콘텐츠 조합만 반환한다.
3. 조회는 회차·콘텐츠·감사 기록을 변경하지 않는다.
