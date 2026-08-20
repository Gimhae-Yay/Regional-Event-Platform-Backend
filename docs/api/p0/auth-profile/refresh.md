# 인증·프로필 Access Token 재발급 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0111](../../../adr/0111-use-stateless-refresh-token.md), [ADR-0105](../../../adr/0105-deliver-access-token-in-json-response-body.md), [ADR-0043](../../../adr/0043-define-jwt-access-token-security-profile.md), [ADR-0045](../../../adr/0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 `refreshToken` 쿠키를 검증해 새 Access Token만 발급하는 HTTP API 계약을 정의한다. Refresh Token은
회전·소비·교체하지 않으며, 새 Access Token은 JSON 응답 본문의 `data.accessToken`으로만 전달한다.

Refresh Token은 이 API와 다른 인증 API에서만 수신한다. 보호 업무 API는 Refresh Token을 인증 수단으로 허용하지
않고, 새 Access Token을 `Authorization` 요청 헤더로 사용한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/refresh` | `app_user` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`을 사용한다. 요청 본문은 없다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API이며 `refreshToken` 쿠키로만 갱신 자격을 확인한다. 성공 시 JSON 본문의 Access Token만 발급하고 Refresh Token 쿠키를 변경하지 않는다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. Access Token 재발급

이 API는 서명·issuer·audience·token type·만료가 유효하고, `sub`가 현재 활성 회원인 Stateless Refresh Token으로
새 Access Token을 발급한다. Refresh Token은 로그인 시점부터 14일 절대 만료를 유지하며, 갱신 요청으로 수명을
연장하지 않는다.

서버는 Refresh Token의 소비·폐기·계열 상태를 저장하지 않는다. 같은 유효 토큰의 반복 또는 동시 갱신은 모두
성공할 수 있으며, 성공 응답은 Refresh Token 쿠키를 교체하지 않는다.

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
| `Set-Cookie` | N | 성공 응답은 Refresh Token 쿠키를 변경하지 않는다. |

성공 응답에는 Access Token을 담은 `Authorization` 응답 헤더를 포함하지 않는다.

Refresh Token은 JSON 본문, `Authorization` 헤더 또는 다른 일반 응답 헤더에 포함하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "Access Token 재발급에 성공했습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `Access Token 재발급에 성공했습니다.`다. |
| `data.accessToken` | String | 일반 보호 API의 `Authorization: Bearer <accessToken>` 요청 헤더에 사용하는 새 Access Token이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | Refresh Token이 없거나 서명·claim·만료가 유효하지 않거나, `sub`가 현재 활성 회원이 아니다. Access Token을 발급하지 않으며 재로그인이 필요하다. `Set-Cookie`로 기존 `refreshToken`을 즉시 만료시킨다. |

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

오류 응답에는 Access Token, Refresh Token 또는 내부 예외 정보를 포함하지 않는다.
