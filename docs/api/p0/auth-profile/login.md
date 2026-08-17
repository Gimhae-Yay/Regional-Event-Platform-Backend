# 인증·프로필 로그인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0005](../../../adr/0005-use-jwt-access-and-rotating-refresh-tokens.md), [ADR-0023](../../../adr/0023-manage-refresh-token-revocation-in-redis.md), [ADR-0105](../../../adr/0105-deliver-access-token-in-json-response-body.md), [ADR-0043](../../../adr/0043-define-jwt-access-token-security-profile.md), [ADR-0044](../../../adr/0044-use-delegating-bcrypt-password-encoder.md), [ADR-0045](../../../adr/0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md), [ADR-0052](../../../adr/0052-define-refresh-token-security-profile-and-fail-closed-redis-state.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 이메일과 비밀번호를 검증해 짧은 수명의 Access Token과 회전형 Refresh Token을 발급하는 HTTP API
계약을 정의한다. Access Token은 JSON 응답 본문의 `data.accessToken`으로, Refresh Token은 `HttpOnly` 쿠키로만 전달한다.

`PENDING` 운영자 신청 계정도 활성 계정이면 로그인할 수 있지만, 승인 전에는 `OPERATOR` 역할이 없어 운영자 API를
호출할 수 없다. Access Token은 사용자 식별자만 포함하며, 로그인 응답의 역할 목록은 표시용 정보다. 역할·지역·소유권·
승인 상태의 최종 검증은 서버의 현재 데이터를 따른다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/login` | `app_user`, `user_role_assignment`, Redis Refresh Token 계열·폐기 키 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 `application/json; charset=UTF-8`을 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API다. 성공 시 JSON 본문의 Access Token과 Refresh Token 쿠키를 발급한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 로그인

이 API는 정규화한 이메일과 비밀번호가 일치하는 활성 회원에게 새 Access Token과 새 Refresh Token 계열을 발급한다.
자격 증명이 올바르지 않거나 계정이 로그인할 수 없는 상태이면 같은 오류를 반환해 계정 존재 여부를 공개하지 않는다.

### Request

```http
POST /api/v1/auth/login
```

#### Request Example

```http
POST /api/v1/auth/login HTTP/1.1
Content-Type: application/json; charset=UTF-8
Accept: application/json

{
  "email": "visitor@example.com",
  "password": "LocalStamp!2026"
}
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | 공개 API이므로 전송하지 않는다. |
| `Content-Type` | Y | `application/json; charset=UTF-8` |
| `Accept` | N | `application/json` |

#### Path Variable

없음.

#### Query Parameter

없음.

#### Request Body

```json
{
  "email": "visitor@example.com",
  "password": "LocalStamp!2026"
}
```

#### Request Field

| Name | Type | Required | Description |
| --- | --- | --- | --- |
| `email` | String | Y | 이메일 형식, 최대 254자, `null`·빈 문자열 불가. 앞뒤 공백을 제거하고 소문자로 정규화해 가입 시 저장한 로그인 식별자와 비교한다. |
| `password` | String | Y | `null`·빈 문자열·공백만으로 된 값은 허용하지 않는다. 원문을 응답·로그에 남기지 않고 저장된 비밀번호 해시와 비교한다. |

### Response

#### Status

```http
200 OK
```

#### Response Headers

| Name | Required | Description |
| --- | --- | --- |
| `Set-Cookie` | Y | `refreshToken=<refreshToken>; Max-Age=1209600; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`. 새 Refresh Token 계열은 발급 시점부터 14일간 유효하며, `Domain`은 생략해 호스트 전용으로 한다. |

성공 응답에는 Access Token을 담은 `Authorization` 응답 헤더를 포함하지 않는다.

Refresh Token은 JSON 본문, `Authorization` 헤더 또는 다른 일반 응답 헤더에 포함하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": {
    "userId": "1",
    "roles": [
      "VISITOR"
    ],
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `로그인에 성공했습니다.`다. |
| `data.userId` | String | 로그인한 활성 회원의 식별자다. |
| `data.roles` | Array&lt;String&gt; | 현재 부여된 역할 목록이다. 역할이 아직 부여되지 않은 `PENDING` 운영자 신청 계정은 빈 배열을 반환한다. |
| `data.accessToken` | String | 일반 보호 API의 `Authorization: Bearer <accessToken>` 요청 헤더에 사용하는 짧은 수명의 Access Token이다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수 요청 값 누락 또는 `email`, `password`의 형식 위반이다. 토큰·계열을 발급하지 않으며 값을 수정한 뒤 재시도할 수 있다. |
| 400 | `INVALID_JSON` | 요청 본문이 JSON 형식이 아니거나 역직렬화할 수 없다. 토큰·계열을 발급하지 않으며 본문을 수정한 뒤 재시도할 수 있다. |
| 401 | `INVALID_CREDENTIALS` | 이메일·비밀번호가 일치하지 않거나 계정이 로그인할 수 없는 상태다. 계정 존재 여부를 공개하지 않으며 토큰·계열을 발급하지 않는다. |
| 503 | `AUTH_SERVICE_UNAVAILABLE` | Redis를 사용할 수 없어 Refresh Token 계열을 안전하게 발급할 수 없다. 토큰·계열을 발급하지 않으며 잠시 뒤 재시도할 수 있다. 메시지는 `인증 서비스를 일시적으로 사용할 수 없습니다.`다. |

#### Error Response Body

```json
{
  "statusCode": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "data": null
}
```

오류 응답에는 비밀번호 원문·해시, Access Token, Refresh Token, `jti`, `family_id` 또는 내부 예외 정보를 포함하지 않는다.
