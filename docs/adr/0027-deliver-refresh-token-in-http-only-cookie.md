# ADR-0027: Refresh Token을 HttpOnly 쿠키로 전달

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-07-29
- 결정일: 2026-07-29
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [ADR-0005](0005-use-jwt-access-and-rotating-refresh-tokens.md#결정), [ADR-0023](0023-manage-refresh-token-revocation-in-redis.md#결정)
- 관련 단계: 단계 0. 정책·설계 확정
- 관련 이슈: 없음
- 대체 대상: 없음

## 맥락

ADR-0005는 짧은 수명의 Access Token과 회전형 Refresh Token을 채택했지만, 토큰 전달 위치는 인증 API 명세에서
정하도록 남겼다. Access Token은 일반 요청에 `Authorization` 헤더로 전달할 수 있지만, 장기 자격 증명인 Refresh
Token을 JavaScript가 읽을 수 있는 응답 본문이나 저장소에 두면 XSS 노출 범위가 커진다.

사용자는 로그인과 이후 토큰 갱신에서 Access Token은 응답 헤더, Refresh Token은 `HttpOnly` 쿠키로 전달하도록
선택했다.

## 결정 동인과 불변 조건

- Refresh Token 원문은 JSON 응답, 일반 응답 헤더, 로그, DB 또는 클라이언트 JavaScript에 노출하지 않는다.
- 보호 업무 API는 Refresh Token을 인증 수단으로 허용하지 않고 Access Token만 사용한다.
- Refresh Token은 HTTPS에서만 전송되고, 같은 사이트가 아닌 요청에는 자동 전송되지 않아야 한다.
- Access Token과 Refresh Token의 수명·회전·폐기·재사용 탐지는 ADR-0005와 ADR-0023을 따른다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | Access Token은 `Authorization` 응답 헤더, Refresh Token은 `HttpOnly`·`Secure`·`SameSite=Strict` 쿠키 | Refresh Token을 JavaScript에서 읽을 수 없고, 일반 업무 API에는 Access Token만 사용하게 분리한다. | 쿠키 기반 토큰 갱신은 SameSite·CSRF·CORS 제약을 함께 관리해야 한다. | 중간 | 채택. 브라우저 클라이언트의 장기 자격 증명 노출을 줄인다. |
| 2 | Access·Refresh Token을 모두 JSON 본문으로 전달 | 모바일·비브라우저 클라이언트의 수신이 단순하다. | Refresh Token이 JavaScript와 클라이언트 저장소에 노출되기 쉽다. | 낮음 | 현재 보안 요구에 부적합하다. |
| 3 | Access·Refresh Token을 모두 쿠키로 전달 | 브라우저 저장 방식이 하나로 단순하다. | 보호 업무 API까지 쿠키 인증과 CSRF 방어를 요구하게 된다. | 중간 | Access Token 헤더 계약과 맞지 않는다. |

## 결정

로그인과 토큰 갱신 성공 응답은 Access Token을 `Authorization: Bearer <accessToken>` 응답 헤더로 전달한다.
Refresh Token은 다음 속성을 가진 `Set-Cookie` 헤더로만 전달한다.

```http
Set-Cookie: refreshToken=<refreshToken>; Max-Age=<refresh-token-ttl>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict
```

쿠키의 `Domain` 속성은 생략해 호스트 전용 쿠키로 유지한다. Refresh Token은 응답 JSON, `Authorization` 헤더 또는
다른 일반 헤더에 넣지 않는다. `SameSite=Strict` 때문에 교차 사이트 클라이언트가 필요해지면 `SameSite=None` 전환과
CSRF 방어를 함께 다루는 후속 ADR을 먼저 작성한다.

## 결과와 트레이드오프

### 기대 효과

- Refresh Token 원문이 JavaScript 접근 경로와 일반 API 응답에서 분리된다.
- 일반 업무 API는 Access Token 헤더만 검사하므로 Refresh Token의 사용 범위를 토큰 발급·갱신으로 제한한다.
- 쿠키의 경로·보안·동일 사이트 속성으로 불필요한 자동 전송 범위를 줄인다.

### 수용한 단점과 위험

- HTTPS와 브라우저 쿠키 정책이 로그인·토큰 갱신의 필수 환경 조건이 된다.
- 교차 사이트 프론트엔드, 네이티브 클라이언트 또는 외부 클라이언트는 현재 Refresh Token 계약을 바로 사용할 수 없다.
- `SameSite=Strict`의 보장 범위를 약화시키는 변경에는 CSRF 방어와 호환성 검토가 필요하다.

## 전환과 롤백

신규 인증 구현이므로 기존 토큰 전달 방식이나 쿠키 이관은 없다. 로그인·토큰 갱신 구현에서 Access Token 헤더와
Refresh Token 쿠키를 함께 발급하고, 로그아웃·탈퇴·계열 폐기 시 같은 이름·경로의 쿠키를 즉시 만료시킨다.

본문 전달 방식으로 전환하려면 현재 쿠키를 만료시키고 클라이언트 저장소 노출, 토큰 회전, CORS와 재로그인 전환을
포함한 후속 ADR을 작성한다.

## 검증 방법

- 로그인 성공 응답에 Access Token `Authorization` 헤더와 지정 속성의 `refreshToken` 쿠키가 함께 있는지 검증한다.
- JSON 본문, 로그, 예외, DB와 Redis 키에 Refresh Token 원문이 없는지 검증한다.
- Refresh Token이 일반 업무 API의 `Authorization` 인증에 사용되지 않고, 토큰 갱신 경로에서만 수신되는지 검증한다.
- `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, 호스트 전용 Domain 생략과 Max-Age가 적용되는지 검증한다.
- Redis 장애 시 두 토큰을 부분 발급하지 않고 재시도 가능한 오류를 반환하는지 검증한다.

## 대체 조건

- 브라우저 외 클라이언트가 Refresh Token을 지원해야 하고, 현재 쿠키 계약으로 제공할 수 없다.
- 교차 사이트 프론트엔드 요구로 `SameSite=Strict`를 유지할 수 없으며 CSRF 방어 방식을 함께 확정한다.
- 외부 IdP 또는 OAuth/OIDC 공급자가 토큰 전달과 갱신을 대신 관리한다.
