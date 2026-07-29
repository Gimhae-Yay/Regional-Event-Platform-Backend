# 인증·프로필 로그아웃 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0005](../../../adr/0005-use-jwt-access-and-rotating-refresh-tokens.md), [ADR-0023](../../../adr/0023-manage-refresh-token-revocation-in-redis.md), [ADR-0027](../../../adr/0027-deliver-refresh-token-in-http-only-cookie.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 브라우저의 `refreshToken` 쿠키를 제거하고, 유효한 Refresh Token이면 해당 토큰 계열을 폐기하는 HTTP API
계약을 정의한다. 로그아웃은 현재 Refresh Token 계열만 폐기하므로 다른 기기·브라우저에서 발급한 계열은 유지된다.

Access Token은 개별 폐기하지 않는다. 로그아웃에 성공해도 이미 발급된 Access Token은 만료 전까지 유효할 수 있으며,
보호 업무 API의 권한 검사는 현재 서버 데이터로 추가 검증한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/logout` | Redis Refresh Token 계열 폐기 키 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`을 사용하며 요청 본문은 없다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | Access Token 인증이 필요 없는 인증 API다. `refreshToken` 쿠키가 있으면 해당 계열만 폐기한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 로그아웃

이 API는 서명과 만료가 유효한 `refreshToken`에서 계열 식별자를 확인해 Redis 원자 작업으로 그 계열을 폐기한다.
폐기 성공 뒤에는 같은 이름·경로·보안 속성의 쿠키를 즉시 만료시킨다.

쿠키가 없거나, 만료·변조·이미 소비·이미 폐기된 Refresh Token이면 서버 상태를 추가로 변경하지 않고 쿠키만
만료시킨 뒤 성공으로 처리한다. 이는 클라이언트의 로컬 인증 상태를 안전하게 정리하는 멱등 계약이며, 토큰의 유효성이나
계정 존재 여부를 공개하지 않는다.

토큰 갱신과 로그아웃이 경합하면 Redis 원자 작업으로 순서를 직렬화한다. 로그아웃이 먼저 완료되면 해당 계열의 갱신은
거부되며, 갱신이 먼저 완료되더라도 로그아웃은 같은 계열 전체를 폐기한다.

### Request

```http
POST /api/v1/auth/logout
```

#### Request Example

```http
POST /api/v1/auth/logout HTTP/1.1
Cookie: refreshToken={refreshToken}
Accept: application/json
```

#### Request Headers

| Name | Required | Description |
| --- | --- | --- |
| `Authorization` | N | Access Token은 로그아웃 자격으로 사용하지 않으므로 전송하지 않는다. |
| `Cookie` | N | `refreshToken=<refreshToken>`. 쿠키가 없더라도 로컬 인증 상태 정리를 위해 성공으로 처리한다. |
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
| `Set-Cookie` | Y | `refreshToken=; Max-Age=0; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`. `Domain`은 생략해 호스트 전용 쿠키로 유지한다. |

성공 응답에는 Access Token이나 Refresh Token을 포함하지 않는다.

#### Response Body

```json
{
  "statusCode": 200,
  "code": "SUCCESS",
  "message": "로그아웃에 성공했습니다.",
  "data": null
}
```

#### Response Field

| Name | Type | Description |
| --- | --- | --- |
| `statusCode` | Integer | HTTP 상태와 같은 `200`이다. |
| `code` | String | 성공 코드 `SUCCESS`다. |
| `message` | String | 성공 메시지 `로그아웃에 성공했습니다.`다. |
| `data` | null | 추가 응답 데이터는 없다. |

### Error Code

| HTTP Status | Code | Description |
| --- | --- | --- |
| 503 | `AUTH_SERVICE_UNAVAILABLE` | Redis를 사용할 수 없어 유효한 Refresh Token 계열을 안전하게 폐기할 수 없다. 쿠키를 변경하지 않으며 잠시 뒤 같은 쿠키로 재시도할 수 있다. |

#### Error Response Body

```json
{
  "statusCode": 503,
  "code": "AUTH_SERVICE_UNAVAILABLE",
  "message": "인증 서비스를 일시적으로 사용할 수 없습니다.",
  "data": null
}
```

오류 응답에는 Access Token, Refresh Token, `jti`, `family_id`, Redis 키 또는 내부 예외 정보를 포함하지 않는다.
