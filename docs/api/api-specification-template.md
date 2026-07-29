# {도메인명} API 명세서

> 이 문서는 새 도메인 API 명세 작성용 템플릿이다. `{...}` 자리표시자는 해당 API 계약을 확정할 때 실제 값으로
> 교체하며, 이 문서 자체는 구현 계약이 아니다.

| 항목      | 내용                                                                    |
|---------|-----------------------------------------------------------------------|
| 대상 릴리스  | `{기능 요구사항 우선순위}`                                                      |
| 관련 요구사항 | `{FR-xx}`, `{정책 ID}`                                                  |
| 소유 도메인  | `{도메인명}`                                                              |
| 기준 문서   | `{기능 요구사항 명세 경로}` (예: `../p0-spec.md`), [API 공통 계약](common/README.md) |

## 1. 개요

이 문서는 `{도메인명}`의 요구사항을 HTTP API 계약으로 구체화한다.
요청·응답의 공통 형식, 인증, 페이지네이션, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 해당 API에만 적용되는 값과 규칙만 작성한다.

### 요구사항 추적

| 요구사항      | HTTP 계약           | 주요 데이터            |
|-----------|-------------------|-------------------|
| `{FR-xx}` | `{METHOD} {path}` | `{테이블 또는 도메인 객체}` |

## 2. 공통 계약 참조

| 대상                    | 기준 문서                                  | 이 도메인에서 명시할 내용                  |
|-----------------------|----------------------------------------|---------------------------------|
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](common/api-conventions.md) | 공통 규칙과 다른 값이 필요한 경우에만 사유와 값을 명시 |
| 인증·인가                 | [인증·인가](common/authentication.md)      | API별 허용 역할, 지역·소유권 조건           |
| 성공·오류 응답              | [응답·오류](common/response-and-error.md)  | API별 성공 상태, `data` 필드와 오류 코드    |
| 페이지네이션                | [페이지네이션](common/pagination.md)         | 목록 API의 정렬·필터·페이지 제한            |

## 3. {API 이름}

{API가 제공하는 기능, 대상 자원과 정책상 목적을 한두 문장으로 설명한다.}

### Request

```http
{METHOD} {path}
```

#### Request Example

```http
{METHOD} {path} HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json
Accept: application/json

{
  "{field}": "{value}"
}
```

#### Request Headers

| Name            | Required | Description                     |
|-----------------|----------|---------------------------------|
| `Authorization` | `{Y/N}`  | `{인증 불필요 시 없음}`                 |
| `Content-Type`  | `{Y/N}`  | `{요청 본문이 있으면 application/json}` |
| `Accept`        | N        | `application/json`              |
| `{멱등성 요청 식별자}`  | `{Y/N}`  | `{적용 API만 작성}`                  |

#### Path Variable

| Name             | Type     | Required | Description |
|------------------|----------|----------|-------------|
| `{pathVariable}` | `{Type}` | Y        | `{설명}`      |

없으면 `없음.`으로 작성한다.

#### Query Parameter

| Name               | Type     | Required | Description |
|--------------------|----------|----------|-------------|
| `{queryParameter}` | `{Type}` | `{Y/N}`  | `{설명}`      |

없으면 `없음.`으로 작성한다.

#### Request Body

```json
{
  "{field}": "{value}"
}
```

없으면 `없음.`으로 작성한다.

#### Request Field

| Name      | Type     | Required | Description            |
|-----------|----------|----------|------------------------|
| `{field}` | `{Type}` | `{Y/N}`  | `{형식, 범위, null 허용 여부}` |

### Response

#### Status

```http
{성공 HTTP 상태}
```

#### Response Body

```json
{
  "statusCode": {성공 HTTP 상태},
  "code": "SUCCESS",
  "message": "{성공 메시지}",
  "data": {
    "{field}": "{value}"
  }
}
```

`204 No Content` 응답이면 본문과 `Response Field`를 작성하지 않는다.

#### Response Field

| Name                  | Type     | Description |
|-----------------------|----------|-------------|
| `statusCode`          | Number   | `{성공 HTTP 상태와 같은 값}` |
| `code`                | String   | `SUCCESS` |
| `message`             | String   | `{성공 메시지}` |
| `data.{field}`        | `{Type}` | `{설명}` |

목록 응답이면 빈 결과의 HTTP 상태와 빈 컬렉션 반환 규칙을 함께 명시한다.

### Error Code

| HTTP Status | Code           | Description             |
|-------------|----------------|-------------------------|
| `{4xx/5xx}` | `{ERROR_CODE}` | `{오류 조건과 상태 변경·미변경 여부}` |

#### Error Response Body

```json
{
  "statusCode": {오류 HTTP 상태},
  "code": "{ERROR_CODE}",
  "message": "{공개 오류 메시지}",
  "data": null
}
```
