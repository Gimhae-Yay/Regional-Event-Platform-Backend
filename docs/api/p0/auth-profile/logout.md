# 인증·프로필 로그아웃 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [ADR-0111](../../../adr/0111-use-stateless-refresh-token.md), [ADR-0114](../../../adr/0114-support-cross-origin-browser-authentication-with-configured-allowlist.md), [ADR-0027](../../../adr/0027-deliver-refresh-token-in-http-only-cookie.md), [ADR-0045](../../../adr/0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 브라우저의 `refreshToken` 쿠키를 제거하는 HTTP API 계약을 정의한다. Refresh Token은 Stateless이므로
로그아웃은 서버의 토큰 상태를 검증·변경하지 않는다.

Access Token은 개별 폐기하지 않는다. 로그아웃에 성공해도 이미 발급된 Access Token은 만료 전까지 유효할 수 있으며,
보호 업무 API의 권한 검사는 현재 서버 데이터로 추가 검증한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/logout` | 브라우저 `refreshToken` 쿠키 |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`을 사용하며 요청 본문은 없다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | Access Token 인증이 필요 없는 인증 API다. `refreshToken` 쿠키의 존재·유효성과 무관하게 같은 쿠키를 만료시킨다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | `statusCode`, `code`, `message`, `data`를 같은 순서로 포함한다. 성공 상태는 `200 OK`다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 목록 API가 아니므로 적용하지 않는다. |

## 3. 로그아웃

이 API는 `refreshToken` 쿠키가 없거나 만료·변조된 값을 담고 있어도 같은 이름·경로·보안 속성의 쿠키를 즉시
만료시키고 성공으로 처리한다. 이는 브라우저의 로컬 인증 상태를 정리하는 멱등 계약이며, 토큰의 유효성이나 계정 존재
여부를 공개하지 않는다.

서버는 Refresh Token을 폐기하거나 로그아웃·재발급 순서를 직렬화하지 않는다. 따라서 브라우저 밖에 복사된 유효
Refresh Token은 14일 만료 또는 계정 비활성·삭제 전까지 Access Token 재발급에 사용될 수 있다.

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
| `Set-Cookie` | Y | `refreshToken=; Max-Age=0; Path=/api/v1/auth; HttpOnly; Secure; SameSite=<configuredSameSite>`. `<configuredSameSite>`는 공통 인증 계약의 환경별 `Strict` 또는 `None`이고, `Domain`은 생략해 호스트 전용 쿠키로 유지한다. |

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

이 API는 Refresh Token의 유효성·계정 존재 여부와 무관하게 쿠키 만료 응답을 반환하므로 별도의 인증·도메인 오류를
정의하지 않는다. 오류 응답이 발생하더라도 Access Token, Refresh Token 또는 내부 예외 정보를 포함하지 않는다.
