# 지역·콘텐츠 카탈로그 공개 콘텐츠 회차 목록 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-02`, `SES-01`, `SES-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 공개된 콘텐츠의 예약 가능한 회차 일정 목록을 조회하는 HTTP API 계약을 정의한다.
`PUBLISHED` 콘텐츠에 속한 `SCHEDULED` 회차만 시작 시각 오름차순으로 반환하며, 취소·완료 회차와 공개되지 않은 콘텐츠의
회차는 노출하지 않는다. 가격과 실시간 잔여 정원·예약 가능 여부는 회차 단건 조회 API에서 확인한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-02, SES-01, SES-02 | `GET /api/v1/contents/{contentId}/sessions` | `content`, `content_session` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 응답은 `application/json; charset=UTF-8`이다. 일정 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API다. `Authorization` 헤더, 역할·지역·소유 관계 검증을 요구하지 않는다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 회차 배열을 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0에서 콘텐츠별 회차 목록은 페이지네이션을 적용하지 않는다. |

## 3. 공개 콘텐츠 회차 목록 조회

공개 콘텐츠가 존재하면 공개할 `SCHEDULED` 회차가 없더라도 빈 배열을 반환한다. 콘텐츠가 없거나 `PUBLISHED` 상태가 아니거나
삭제된 경우에는 존재 여부를 구분하지 않고 `NOT_FOUND`를 반환한다.

### Request

```http
GET /api/v1/contents/{contentId}/sessions
```

#### Request Example

```http
GET /api/v1/contents/10/sessions HTTP/1.1
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 공개 API이므로 전송하지 않는다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `contentId` | String | Y | API 공통 규칙을 따르는 공개 콘텐츠 식별자다. |

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
  "message": "콘텐츠 회차 목록 조회에 성공했습니다.",
  "data": {
    "contentId": "10",
    "sessions": [
      {
        "sessionId": "21",
        "startsAt": "2026-08-15T10:00:00+09:00",
        "endsAt": "2026-08-15T12:00:00+09:00"
      }
    ]
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `콘텐츠 회차 목록 조회에 성공했습니다.`다. |
| `data.contentId` | String | API 공통 규칙에 따른 공개 콘텐츠 식별자다. |
| `data.sessions` | Array | 시작 시각 오름차순의 공개 `SCHEDULED` 회차 배열이다. 없으면 빈 배열이다. |
| `data.sessions[].sessionId` | String | API 공통 규칙에 따른 회차 식별자다. |
| `data.sessions[].startsAt` | String | 회차 시작 일정 시각. API 공통 규칙에 따른 `Asia/Seoul` ISO 8601 오프셋 일시다. |
| `data.sessions[].endsAt` | String | 회차 종료 일정 시각. API 공통 규칙에 따른 `Asia/Seoul` ISO 8601 오프셋 일시다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `contentId`가 양의 정수가 아니다. 상태를 변경하지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_TYPE` | `contentId`를 정수로 변환할 수 없다. 상태를 변경하지 않는다. |
| 404 | `NOT_FOUND` | 콘텐츠가 없거나 공개 상태가 아니거나 삭제됐다. 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
