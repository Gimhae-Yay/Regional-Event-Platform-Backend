# 지역·콘텐츠 카탈로그 회차 예약 정보 조회 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-02`, `RSV-02`, `SES-01`, `SES-02` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 공개 회차의 가격, 실시간 잔여 정원과 예약 가능 여부를 조회하는 HTTP API 계약을 정의한다.
P0 예약은 무료이므로 가격은 원 단위 정수 `0`으로 반환한다. 잔여 정원과 예약 가능 여부는 캐시하지 않고 MySQL
조회 시점의 값을 사용하며, 이 응답은 정원을 확보하거나 예약 성공을 보장하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-02, RSV-02, SES-01, SES-02 | `GET /api/v1/sessions/{sessionId}` | `content`, `content_session` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 API에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 응답은 `application/json; charset=UTF-8`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API다. `Authorization` 헤더, 역할·지역·소유 관계 검증을 요구하지 않는다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `200 OK`와 회차 예약 정보를 반환한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 단건 조회이므로 적용하지 않는다. |

## 3. 회차 예약 정보 조회

`PUBLISHED` 콘텐츠에 속한 `SCHEDULED` 회차만 조회할 수 있다. `reservable`은 MySQL 기준 현재 시각이 회차 시작 전이고,
잔여 정원이 1 이상이며 콘텐츠가 `PUBLISHED`, 회차가 `SCHEDULED`일 때만 `true`다. 조회와 홀드 생성 사이에는
다른 요청으로 정원이 변할 수 있으므로 실제 예약은 홀드 생성 API의 조건부 정원 차감 결과를 따른다.

### Request

```http
GET /api/v1/sessions/{sessionId}
```

#### Request Example

```http
GET /api/v1/sessions/21 HTTP/1.1
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
| `sessionId` | Long | Y | 공개 회차 식별자. 양의 정수다. |

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
  "message": "회차 예약 정보 조회에 성공했습니다.",
  "data": {
    "sessionId": 21,
    "contentId": 10,
    "startsAt": "2026-08-15T10:00:00+09:00",
    "endsAt": "2026-08-15T12:00:00+09:00",
    "price": 0,
    "remainingCapacity": 12,
    "reservable": true
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `회차 예약 정보 조회에 성공했습니다.`다. |
| `data.sessionId` | Long | 조회한 공개 회차 식별자다. |
| `data.contentId` | Long | 회차가 속한 공개 콘텐츠 식별자다. |
| `data.startsAt` | String | 회차 시작 시각. ISO 8601 오프셋 일시다. |
| `data.endsAt` | String | 회차 종료 시각. ISO 8601 오프셋 일시다. |
| `data.price` | Integer | P0 무료 예약 가격. 원 단위 정수 `0`이다. |
| `data.remainingCapacity` | Integer | MySQL 조회 시점의 잔여 정원. 0 이상의 정수다. |
| `data.reservable` | Boolean | 조회 시점에 홀드 생성 조건을 만족하면 `true`다. `true`여도 동시 요청으로 실제 홀드 생성은 실패할 수 있다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `sessionId`가 양의 정수가 아니다. 상태를 변경하지 않으며 값을 수정해 다시 요청할 수 있다. |
| 400 | `INVALID_TYPE` | `sessionId`를 정수로 변환할 수 없다. 상태를 변경하지 않는다. |
| 404 | `NOT_FOUND` | 회차가 없거나 회차의 콘텐츠가 공개 상태가 아니거나 회차가 `SCHEDULED`가 아니다. 상태를 변경하지 않는다. |

#### Error Response Body

```json
{
  "statusCode": 404,
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```
