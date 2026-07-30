# 심사 대기 회차 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `AUTH-01`, `SES-01`, `SES-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ADR-0038](../../../adr/0038-create-sessions-with-lifecycle-and-review-session-changes.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

담당 지역 관리자가 자신의 지역에서 추가 생성된 `PENDING` 회차를 심사하기 전에 목록으로 조회한다.
최초 `PENDING` 콘텐츠에 포함된 회차는 콘텐츠 승인 흐름에서 함께 심사하므로 이 목록에 포함하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-04`, `AUTH-01`, `SES-01`, `SES-02` | `GET /api/v1/region-admin/sessions?status=PENDING` | `content`, `content_session`, `app_user` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이고, 일정 시각·사건 시각·식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `REGION_ADMIN` 역할과 담당 지역 일치가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 공통 오류 코드를 사용한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서는 페이지네이션을 적용하지 않는다. |

## 3. 심사 대기 회차 목록 조회

### Request

```http
GET /api/v1/region-admin/sessions?status=PENDING
```

#### Request Example

```http
GET /api/v1/region-admin/sessions?status=PENDING HTTP/1.1
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

없음.

#### Query Parameter

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `status` | String | Y | 항상 `PENDING`이다. |

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
  "message": "심사 대기 회차 목록 조회에 성공했습니다.",
  "data": {
    "sessions": [
      {
        "sessionId": "21",
        "contentId": "10",
        "contentTitle": "가야문화 체험",
        "status": "PENDING",
        "startsAt": "2026-08-22T10:00:00+09:00",
        "endsAt": "2026-08-22T12:00:00+09:00",
        "checkinOpenAt": "2026-08-22T09:30:00+09:00",
        "checkinCloseAt": "2026-08-22T12:30:00+09:00",
        "capacity": 30,
        "createdAt": "2026-08-01T01:00:00Z",
        "operator": {
          "operatorId": "20",
          "name": "김해운영"
        }
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200` |
| `code` | String | 성공 코드 `SUCCESS` |
| `message` | String | 성공 메시지 |
| `data.sessions` | Array | 담당 지역의 심사 대기 추가 회차 목록. 없으면 빈 배열 `[]` |
| `data.sessions[].sessionId` | String | 회차 식별자 |
| `data.sessions[].contentId` | String | 대상 콘텐츠 식별자 |
| `data.sessions[].contentTitle` | String | 대상 콘텐츠 제목 |
| `data.sessions[].status` | String | 항상 `PENDING` |
| `data.sessions[].startsAt`~`checkinCloseAt` | String | 후보 일정과 체크인 창 |
| `data.sessions[].capacity` | Integer | 후보 총정원 |
| `data.sessions[].createdAt` | String | 회차 생성 사건 시각. UTC ISO 8601 일시 |
| `data.sessions[].operator` | Object | 콘텐츠 소유 운영자 |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `status`가 없거나 `PENDING`이 아니다. |
| 401 | `UNAUTHENTICATED` | Access Token이 없거나 유효하지 않다. |
| 403 | `FORBIDDEN` | 담당 지역 관리자 역할 또는 담당 지역이 없다. |
| 500 | `INTERNAL_SERVER_ERROR` | 회차·콘텐츠·지역 관계가 정책과 일치하지 않는다. |

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

1. 인증 주체의 `REGION_ADMIN` 담당 지역과 `content_session.region_id`가 같은 행만 반환한다.
2. `content_session.status = PENDING`, 콘텐츠가 소프트 삭제되지 않았고 상태가 `APPROVED` 또는 `PUBLISHED`인 추가 회차만 반환한다.
3. `created_at` 오름차순, 같은 시각이면 `session_id` 오름차순으로 정렬한다.
4. 조회는 회차·콘텐츠·감사 기록을 변경하지 않는다.
