# 인증·프로필 Access Token 재발급 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0005](../../../adr/0005-use-jwt-access-and-rotating-refresh-tokens.md), [ADR-0023](../../../adr/0023-manage-refresh-token-revocation-in-redis.md), [ADR-0027](../../../adr/0027-deliver-refresh-token-in-http-only-cookie.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 `refreshToken` 쿠키를 검증해 새 Access Token과 회전된 Refresh Token을 발급하는 HTTP API 계약을
정의한다. 이전 Refresh Token은 성공한 최초 갱신에서 소비되며, 새 Refresh Token은 `HttpOnly` 쿠키로만 전달한다.

Refresh Token은 이 API와 다른 인증 API에서만 수신한다. 보호 업무 API는 Refresh Token을 인증 수단으로 허용하지
않고, 새 Access Token을 `Authorization` 요청 헤더로 사용한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/refresh` | Redis Refresh Token 계열·소비·폐기 키 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`을 사용한다. 요청 본문은 없다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API이며 `refreshToken` 쿠키로만 갱신 자격을 확인한다. 성공 시 Access Token 헤더와 회전된 Refresh Token 쿠키를 발급한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. Access Token 재발급

이 API는 서명·만료·계열 폐기·소비 상태가 유효한 Refresh Token의 최초 요청만 성공시킨다. 성공하면 이전
Refresh Token을 소비하고 같은 계열의 새 Refresh Token과 새 Access Token을 발급한다.

같은 Refresh Token의 동시 갱신은 하나만 성공한다. 진행 중인 다른 갱신 요청은 `REFRESH_TOKEN_CONFLICT`로
반환하며 기존 쿠키를 변경하지 않는다. 이미 소비된 Refresh Token이 이후 재사용되면 계열 전체를 폐기하고
재로그인을 요구한다.

### Request

```http
POST /api/v1/auth/refresh
```

#### Request Example

```http
POST /api/v1/auth/refresh HTTP/1.1
Cookie: refreshToken={refreshToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | Access Token은 갱신 자격으로 사용하지 않으므로 전송하지 않는다. |
| `Cookie` | Y | `refreshToken=<refreshToken>`. 브라우저가 `HttpOnly` 쿠키를 자동으로 전송한다. |
| `Content-Type` | N | 요청 본문이 없으므로 전송하지 않는다. |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

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

#### Response Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | Y | `Bearer <accessToken>`. 새로 발급한 Access Token이며 일반 보호 API 호출에 사용한다. |
| `Set-Cookie` | Y | `refreshToken=<refreshToken>; Max-Age=<refresh-token-ttl>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`. 회전된 새 Refresh Token이며 `Domain`은 생략해 호스트 전용으로 한다. |

Refresh Token은 JSON 본문, `Authorization` 헤더 또는 다른 일반 응답 헤더에 포함하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "Access Token 재발급에 성공했습니다.",
  "data": null
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `Access Token 재발급에 성공했습니다.`다. |
| `data` | null | 토큰은 응답 헤더와 쿠키로 전달하므로 응답 본문 데이터는 없다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | Refresh Token이 없거나 서명·만료·계열 폐기·소비 상태가 유효하지 않다. 토큰을 발급하지 않으며 재로그인이 필요하다. `Set-Cookie`로 기존 `refreshToken`을 즉시 만료시킨다. |
| 409 | `REFRESH_TOKEN_CONFLICT` | 같은 Refresh Token의 다른 갱신 요청이 진행 중이다. 토큰을 발급하지 않고 쿠키를 변경하지 않으며, 클라이언트는 진행 중인 갱신 결과를 사용하거나 완료 뒤 다시 시도한다. |
| 503 | `AUTH_SERVICE_UNAVAILABLE` | Redis를 사용할 수 없어 계열 폐기·소비 상태를 안전하게 확인할 수 없다. 토큰을 발급하지 않고 쿠키를 변경하지 않으며 잠시 뒤 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "UNAUTHENTICATED",
  "message": "인증 정보가 없거나 유효하지 않습니다.",
  "data": null
}
```

`UNAUTHENTICATED` 응답은 다음과 같은 `Set-Cookie` 헤더로 기존 Refresh Token을 제거한다.

```http
Set-Cookie: refreshToken=; Max-Age=0; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict
```

오류 응답에는 Access Token, Refresh Token, `jti`, `family_id`, Redis 키 또는 내부 예외 정보를 포함하지 않는다.
