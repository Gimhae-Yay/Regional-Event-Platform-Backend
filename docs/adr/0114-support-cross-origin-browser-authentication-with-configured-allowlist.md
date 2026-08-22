# ADR-0114: 환경별 허용 Origin과 Origin 검증으로 교차 출처 브라우저 인증을 지원한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-22
- 결정일: 2026-08-22
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [인증·인가 공통 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#1023 교차 출처 배포에서 인증 API가 CORS 사전 요청으로 차단된다](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/1023), [#1024 CORS 교차 출처 정책과 인증 계약을 확정한다](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/1024)
- 대체 대상: [ADR-0027](0027-deliver-refresh-token-in-http-only-cookie.md)의 Refresh Cookie `SameSite=Strict` 고정 범위, [ADR-0045](0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md)의 CORS 미허용·단일 동일 사이트 범위, [ADR-0105](0105-deliver-access-token-in-json-response-body.md)의 Refresh Cookie `SameSite=Strict` 고정 범위, [ADR-0111](0111-use-stateless-refresh-token.md)의 Refresh Cookie `SameSite=Strict` 고정 범위

## 맥락

#1023의 배포 조건은 프런트엔드와 API가 스킴·호스트·포트 중 하나 이상이 다른 Origin으로 공개되는 것이다. 현재 보안
체인은 CORS를 명시적으로 비활성화했고, Refresh Cookie는 호스트 전용 `SameSite=Strict`로 고정했으며 CSRF 검사를
적용하지 않는다. 따라서 JSON `POST`, `Authorization` 헤더 또는 Refresh Cookie를 쓰는 브라우저 요청은 사전 요청,
credential Cookie 또는 CSRF 경계에서 계약을 잃는다.

프런트엔드와 API Origin은 배포 환경에 따라 달라질 수 있으므로 소스 코드나 문서 상수가 아니라 환경별 설정으로 관리한다.
다만 `SameSite=Strict` Refresh Cookie는 API와 같은 HTTPS site의 프런트엔드에서만 전송된다. 허용 Origin을 넓게
해석하거나 와일드카드로 대체하면 credential Cookie가 있는 브라우저 인증 경계를 무너뜨린다. Stateless Bearer 업무
API와 Refresh Cookie의 경계를 유지한 채, API와 같은 HTTPS site의 정확한 Origin만 구성으로 받고 Cookie 기반 인증
명령의 Origin을 서버에서 검증해야 한다.

## 결정 동인과 불변 조건

- 허용 Origin은 API와 같은 HTTPS site인 환경별 구성의 정확한 `scheme + host + port` 값만 허용하며, 와일드카드·정규식·경로·쿼리·fragment를 허용하지 않는다.
- Refresh Token은 계속 호스트 전용 `HttpOnly`·`Secure`·`SameSite=Strict` Cookie이고 `/api/v1/auth`에서만 수신하며, 보호 업무 API의 인증 수단이 될 수 없다.
- CORS credential을 허용하더라도 Access Token은 JSON 본문으로만 받고 보호 업무 API는 `Authorization: Bearer`만으로 인증한다.
- Refresh Cookie를 수신하거나 사용하는 인증 명령은 CORS 처리와 별도로 정확한 `Origin` 검증을 통과해야 한다.
- `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`, 허용 method·header 어느 곳에도 와일드카드를 사용하지 않는다.
- 허용 Origin 구성이 비어 있으면 교차 출처 요청을 허용하지 않는다. 잘못된 Origin 형식은 서버 시작을 실패시켜 허용 범위를 추측하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 같은 HTTPS site의 환경별 정확한 allowlist, `SameSite=Strict`, credential CORS와 서버 Origin 검증 | 배포 Origin을 코드에 고정하지 않으면서 필요한 교차 Origin만 허용한다. 별도 Token 발급·저장·회전 API 없이 Cookie 기반 인증 명령을 보호하고 `SameSite=Strict`를 유지한다. | Origin 설정 오류는 요청 차단으로 드러난다. | 낮음 | 추천안. #1023·#1024의 환경별 Origin 조건과 현재 Stateless Bearer·제한된 Refresh Cookie 경계에 맞는다. |
| 2 | 단일 Origin을 코드에 고정 | 환경 변수 오설정을 줄이고 계약이 단순하다. | 배포 Origin 변경에 코드 변경이 필요하며 #1023·#1024의 환경별 설정 완료 조건을 충족하지 못한다. | 중간 | 현재 이슈 범위에 맞지 않는다. |
| 3 | 동일 Origin 프록시만 유지 | Cookie와 CSRF 계약이 단순하다. | #1023의 서로 다른 Origin 배포 조건을 충족하지 못하며 배포 선택지를 제한한다. | 낮음 | 현재 요구사항에 맞지 않는다. |

## 결정

`security.cors.allowed-origins`를 교차 출처 브라우저 요청의 유일한 allowlist로 둔다. 환경 변수
`SECURITY_CORS_ALLOWED_ORIGINS`는 API와 같은 HTTPS site의 쉼표로 구분한 절대 Origin 목록이며, 예를 들어
`https://local-stamp.org`처럼 경로와 마지막 `/` 없이 설정한다. 각 값은 API와 scheme 및 registrable domain이 같아야
한다. 실제 값은 환경 변수에서만 관리하고 저장소에는 넣지 않는다. 비어 있으면 CORS 응답 헤더를 추가하지 않고, 이 조건을
벗어난 값은 서버 시작을 실패시킨다.

구현은 요청 `Origin`을 목록과 정확히 비교해 일치할 때만 해당 값을 `Access-Control-Allow-Origin`에 반영하고
`Access-Control-Allow-Credentials: true`, `Vary: Origin`을 보낸다. 허용 method는 `GET`, `POST`, `PUT`, `PATCH`,
`DELETE`, `OPTIONS`이고, 허용 request header는 `Authorization`, `Content-Type`, `Accept`, `Idempotency-Key`다.
허용되지 않은 Origin에는 CORS 헤더를 보내지 않으며, 사전 요청도 보안 인증보다 먼저 위 allowlist와 method·header만으로
판정한다.

Refresh Cookie의 `SameSite`는 `Strict`로 고정한다. 프런트엔드와 API가 같은 HTTPS site인 환경에서만 credential Cookie
전송을 지원한다. `HttpOnly`, `Secure`, `Path=/api/v1/auth`, `Domain` 생략의 호스트 전용 경계와 14일 절대 만료는
유지한다.

Refresh Cookie를 수신하거나 사용하는 `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`,
`POST /api/v1/auth/logout`은 `Origin` header를 반드시 포함해야 한다. 서버는 그 값이 allowlist와 정확히 일치할 때만
처리하고, Origin 누락·불일치는 `403 FORBIDDEN`으로 거부한다. 이는 브라우저가 임의로 설정할 수 없는 `Origin`을
서버에서 검증하는 CSRF 경계다. 별도 CSRF Cookie, Token header, Token bootstrap API는 만들지 않는다.

이 ADR은 정책과 공개 계약만 확정한다. `SecurityConfig`, CORS·Origin 검증 구현, 구성 바인딩과 자동화
테스트는 #1024의 범위 밖이다. 후속 구현·테스트 Task는 이 ADR과 인증·인가 공통 계약을 선행 조건으로 삼아 #1023을
해결해야 한다.

## 결과와 트레이드오프

### 기대 효과

- 프런트엔드와 API의 실제 배포 Origin을 환경별로 바꾸면서도 같은 HTTPS site의 정확한 Origin만 허용한다.
- 허용 Origin의 JSON·Bearer 요청은 필요한 사전 요청과 credential Cookie 계약을 일관되게 사용한다.
- Refresh Cookie를 쓰는 로그인·토큰 갱신·로그아웃 등 인증 명령은 CORS만 믿지 않고 서버에서 `Origin`을 함께 검증한다.
- Refresh Cookie와 Bearer 업무 API의 분리, 무상태 Access Token 검증, 호스트 전용 Cookie 경계는 유지한다.

### 수용한 단점과 위험

- Origin 목록 오설정은 의도적으로 인증 명령을 `403 FORBIDDEN`으로 막는다. 운영 장애를 피하려고 와일드카드로 완화하지 않는다.
- API와 다른 site의 Origin을 설정하면 `SameSite=Strict` Cookie가 인증 명령에 전송되지 않으므로, 시작 단계에서 거부한다.
- 서버가 Origin 없는 브라우저 인증 명령을 허용하면 교차 사이트 form 요청을 구분할 수 없으므로, 호환성보다 fail-closed를 택한다.

## 전환과 롤백

문서 확정 뒤 별도 구현 Task에서 구성 바인딩과 fail-fast 검증, CORS 처리, 인증 명령의 Origin 검증, Refresh Cookie
`SameSite=Strict` 설정과 프런트엔드 `credentials` 처리를 하나의 호환 배포로 추가한다. 구현 전에는 현재
`SameSite=Strict`·CORS 비활성 코드와 문서 계약이 일시적으로 다를 수 있으며, 이 ADR만으로 런타임 동작은 바뀌지 않는다.

배포 결함이나 의심스러운 Origin 확장이 확인되면 `SECURITY_CORS_ALLOWED_ORIGINS`를 빈 값으로 바꿔 교차 출처 CORS를
즉시 차단하고, 승인된 동일 Origin 경로만 사용한다. Origin 검증 결함은 검증을 생략하지 않고 설정과 해당 인증 명령
배포를 함께 롤백한다.

## 검증 방법

- 허용 Origin과 허용 method·header 조합의 `OPTIONS`가 정확한 `Access-Control-Allow-*`와 `Vary: Origin`을 반환하는지 검증한다.
- 미허용 Origin, API와 다른 site·와일드카드·경로를 포함한 설정값, 허용하지 않은 method·header, 비어 있는 allowlist가 CORS credential을 허용하지 않는지 검증한다.
- 허용 Origin의 로그인·재발급·로그아웃 요청만 성공하고, 다른 Origin·Origin 누락은 `403 FORBIDDEN`인지 검증한다.
- 같은 HTTPS site 환경에서 `SameSite=Strict` Refresh Cookie 송수신, 로그인 후 Bearer 보호 API 응답 읽기, 로그아웃 Cookie 만료를 실제 브라우저 E2E로 검증한다.
- 응답·로그에 Refresh Token 원문이 남지 않고, 보호 업무 API가 Refresh Cookie만으로 인증되지 않는지 검증한다.

## 대체 조건

- 허용 Origin을 테넌트·사용자 단위로 동적으로 결정해야 한다.
- BFF, OAuth/OIDC, 외부 IdP 또는 서버 세션이 브라우저 인증 경계를 맡는다.
- CSRF·CORS 사고가 현재 단일 Origin과 Origin 검증의 한계를 보인다.
