# ADR-0114: 환경별 허용 Origin으로 교차 출처 브라우저 인증을 지원한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-22
- 결정일: 2026-08-22
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [인증·인가 공통 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#1023 교차 출처 배포에서 인증 API가 CORS 사전 요청으로 차단된다](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/1023), [#1024 CORS 교차 출처 정책과 인증 계약을 확정한다](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/1024)
- 대체 대상: [ADR-0027](0027-deliver-refresh-token-in-http-only-cookie.md)의 Refresh Cookie `SameSite=Strict` 고정 범위, [ADR-0045](0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md)의 CORS 미허용·CSRF 비활성화·단일 동일 사이트 범위, [ADR-0105](0105-deliver-access-token-in-json-response-body.md)의 Refresh Cookie `SameSite=Strict` 고정 범위, [ADR-0111](0111-use-stateless-refresh-token.md)의 Refresh Cookie `SameSite=Strict` 고정 범위

## 맥락

#1023의 배포 조건은 프런트엔드와 API가 스킴·호스트·포트 중 하나 이상이 다른 Origin으로 공개되는 것이다. 현재 보안
체인은 CORS를 명시적으로 비활성화했고, Refresh Cookie는 호스트 전용 `SameSite=Strict`로 고정했으며 CSRF 검사를
적용하지 않는다. 따라서 JSON `POST`, `Authorization` 헤더 또는 Refresh Cookie를 쓰는 브라우저 요청은 사전 요청,
credential Cookie 또는 CSRF 경계에서 계약을 잃는다.

배포별 프런트엔드와 API Origin 값은 소스 코드나 문서 상수로 관리하지 않는다. 다만 허용 Origin을 넓게 해석하거나
와일드카드로 대체하면 credential Cookie가 있는 브라우저 인증 경계를 무너뜨린다. Stateless Bearer 업무 API와
Refresh Cookie의 경계를 유지한 채, 구성으로 받은 정확한 Origin만 허용하고 Cookie 기반 인증 경로를 CSRF로 보호해야 한다.

## 결정 동인과 불변 조건

- 허용 Origin은 환경별 구성에서 받은 정확한 `scheme + host + port` 값만 허용하며, 와일드카드·정규식·경로·쿼리·fragment를 허용하지 않는다.
- Refresh Token은 계속 호스트 전용 `HttpOnly`·`Secure` Cookie이고 `/api/v1/auth`에서만 수신하며, 보호 업무 API의 인증 수단이 될 수 없다.
- CORS credential을 허용하더라도 Access Token은 JSON 본문으로만 받고 보호 업무 API는 `Authorization: Bearer`만으로 인증한다.
- Cookie가 영향을 주는 비안전 인증 요청은 CORS 검증과 별도로 정확한 `Origin` 검증 및 CSRF 토큰 검증을 모두 통과해야 한다.
- `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`, 허용 method·header 어느 곳에도 와일드카드를 사용하지 않는다.
- 허용 Origin 구성이 비어 있으면 교차 출처 요청을 허용하지 않는다. 잘못된 Origin 형식은 서버 시작을 실패시켜 허용 범위를 추측하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 환경별 정확한 allowlist, credential CORS와 이중 제출 CSRF 토큰 | 배포 Origin을 코드에 고정하지 않으면서 credential Cookie와 교차 출처 요청을 필요한 범위로 제한한다. CSRF 토큰과 `Origin` 검증을 함께 적용해 `SameSite=None` 환경도 보호한다. | 프런트엔드는 Cookie 기반 인증 명령 전에 CSRF 토큰을 받고 header를 추가해야 한다. Origin 설정 오류는 기능 차단으로 드러난다. | 중간 | 추천안. #1023의 교차 출처 조건과 Refresh Cookie 보안 경계를 함께 만족한다. |
| 2 | credential CORS와 `SameSite=None`만 허용 | 프런트엔드 변경이 작고 Cookie를 교차 사이트에도 전송할 수 있다. | 허용된 브라우저 요청의 Cookie 기반 인증 경로가 CSRF에 노출되고, Origin 오설정의 피해 범위가 크다. | 낮음 | 보안 불변 조건을 충족하지 못한다. |
| 3 | 동일 Origin 프록시만 유지 | Cookie와 CSRF 계약이 단순하다. | #1023의 서로 다른 Origin 배포 조건을 충족하지 못하며 배포 선택지를 제한한다. | 낮음 | 현재 요구사항에 맞지 않는다. |

## 결정

`security.cors.allowed-origins`를 교차 출처 브라우저 요청의 유일한 allowlist로 둔다. 환경 변수
`SECURITY_CORS_ALLOWED_ORIGINS`는 쉼표로 구분한 절대 Origin 목록이며, 예를 들어 `https://web.example.com,https://admin.example.com`처럼
경로와 마지막 `/` 없이 설정한다. 실제 값은 환경 변수에서만 관리하고 저장소에는 넣지 않는다. 비어 있으면 CORS 응답 헤더를
추가하지 않는다.

구현은 요청 `Origin`을 목록과 정확히 비교해 일치할 때만 해당 값을 `Access-Control-Allow-Origin`에 반영하고
`Access-Control-Allow-Credentials: true`, `Vary: Origin`을 보낸다. 허용 method는 `GET`, `POST`, `PUT`, `PATCH`,
`DELETE`, `OPTIONS`이고, 허용 request header는 `Authorization`, `Content-Type`, `Accept`, `X-CSRF-Token`이다.
`X-CSRF-Token`만 노출 response header로 둔다. 허용되지 않은 Origin에는 CORS 헤더를 보내지 않으며, 사전 요청도
보안 인증·CSRF 검증보다 먼저 위 allowlist와 method·header만으로 판정한다.

Refresh Cookie의 `SameSite`는 `security.cors.refresh-cookie-same-site`와 환경 변수
`SECURITY_CORS_REFRESH_COOKIE_SAME_SITE`로 정한다. 값은 `Strict` 또는 `None`만 허용한다. 스킴과 registrable domain이
같은 교차 Origin은 `Strict`를 사용하고, 서로 다른 site에서 Refresh Cookie를 보내야 할 때만 `None`을 사용한다.
`None`은 `Secure=true`, HTTPS Origin과 함께만 유효하다. 두 경우 모두 `HttpOnly`, `Secure`, `Path=/api/v1/auth`,
`Domain` 생략의 호스트 전용 경계와 14일 절대 만료는 유지한다.

CSRF 토큰은 API 호스트 전용 `__Host-csrf` Cookie와 `X-CSRF-Token` request header를 비교하는 이중 제출 방식으로
검증한다. CSRF Cookie는 `Secure`, `Path=/`, 구성된 `SameSite`, `Domain` 생략을 사용하고 프런트엔드가 Cookie를 직접
읽을 필요가 없도록 `GET /api/v1/auth/csrf`가 같은 값을 노출 header `X-CSRF-Token`으로 반환한다. 프런트엔드는
`credentials: include`로 이 요청을 먼저 호출해 토큰을 메모리에만 보관하고, Refresh Cookie를 수신하거나 사용하는
`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` 요청에 `X-CSRF-Token`과
`credentials: include`를 함께 보낸다. 서버는 이 요청의 `Origin`도 allowlist와 정확히 일치하는지 확인하고, Origin
누락·불일치 또는 CSRF Cookie·header 불일치는 `403 FORBIDDEN`으로 거부한다. `GET /api/v1/auth/csrf`는 생성·조회
전용이며 토큰이 이미 있으면 교체하지 않는다.

이 ADR은 정책과 공개 계약만 확정한다. `SecurityConfig`, CORS/CSRF 구현, 구성 바인딩, 프런트엔드 헤더 처리와 자동화
테스트는 #1024의 범위 밖이다. 후속 구현·테스트 Task는 이 ADR과 인증·인가 공통 계약을 선행 조건으로 삼아 #1023을
해결해야 한다.

## 결과와 트레이드오프

### 기대 효과

- 프런트엔드와 API의 실제 배포 Origin을 환경별로 바꾸면서도 코드 수정 없이 정확한 Origin만 허용한다.
- 허용 Origin의 JSON·Bearer 요청은 필요한 사전 요청과 credential Cookie 계약을 일관되게 사용한다.
- Refresh Cookie를 쓰는 로그인·토큰 갱신·로그아웃 등 인증 명령은 CORS만 믿지 않고 `Origin`과 CSRF 토큰을 함께 검증한다.
- Refresh Cookie와 Bearer 업무 API의 분리, 무상태 Access Token 검증, 호스트 전용 Cookie 경계는 유지한다.

### 수용한 단점과 위험

- `SameSite=None` 환경은 브라우저의 서드파티 Cookie 차단 정책에 영향을 받을 수 있다. 이 경우 동일 site 배포 또는 BFF 전환을 별도 ADR로 검토해야 한다.
- 프런트엔드가 CSRF bootstrap 또는 header 전송을 누락하면 인증 명령이 `403 FORBIDDEN`으로 실패한다.
- Origin 목록 오설정은 의도적으로 교차 출처 요청을 막는다. 운영 장애를 피하려고 와일드카드로 완화하지 않는다.
- CSRF Cookie는 이중 제출 비교를 위해 `HttpOnly`가 아니지만, `__Host-` 제약·정확한 Origin CORS·노출 header 제한으로 다른 Origin의 읽기와 주입을 막는다.

## 전환과 롤백

문서 확정 뒤 별도 구현 Task에서 구성 바인딩과 fail-fast 검증, CORS 처리, CSRF bootstrap·검증, Refresh Cookie
`SameSite` 설정, 프런트엔드 `credentials`·CSRF header 처리를 하나의 호환 배포로 추가한다. 구현 전에는 현재
`SameSite=Strict`·CORS 비활성 코드와 문서 계약이 일시적으로 다를 수 있으며, 이 ADR만으로 런타임 동작은 바뀌지 않는다.

배포 결함이나 의심스러운 Origin 확장이 확인되면 `SECURITY_CORS_ALLOWED_ORIGINS`를 빈 값으로 바꿔 교차 출처 CORS를
즉시 차단하고, 승인된 동일 Origin 경로만 사용한다. `SameSite=None`에서 Cookie 전송이 실패하면 `Strict`와 동일 site
배포로 되돌린다. CSRF 결함은 검증을 비활성화하지 않고 해당 인증 명령과 프런트엔드 배포를 함께 롤백한다.

## 검증 방법

- 허용 Origin과 허용 method·header 조합의 `OPTIONS`가 정확한 `Access-Control-Allow-*`와 `Vary: Origin`을 반환하는지 검증한다.
- 미허용 Origin, 와일드카드·경로를 포함한 설정값, 허용하지 않은 method·header, 비어 있는 allowlist가 CORS credential을 허용하지 않는지 검증한다.
- 허용 Origin의 로그인·재발급·로그아웃 요청이 CSRF bootstrap header와 Cookie를 함께 제출할 때만 성공하고, 누락·변조·다른 Origin·Origin 누락은 `403 FORBIDDEN`인지 검증한다.
- `Strict` 같은 site와 `None` 교차 site 환경에서 각각 Refresh Cookie 송수신, 로그인 후 Bearer 보호 API 응답 읽기, 로그아웃 Cookie 만료를 실제 브라우저 E2E로 검증한다.
- 응답·로그에 Refresh Token과 CSRF 토큰 원문이 남지 않고, 보호 업무 API가 Refresh Cookie만으로 인증되지 않는지 검증한다.

## 대체 조건

- 서드파티 Cookie 차단으로 교차 site Refresh Cookie가 제품 요구를 충족하지 못한다.
- BFF, OAuth/OIDC, 외부 IdP 또는 서버 세션이 브라우저 인증 경계를 맡는다.
- 허용 Origin을 테넌트·사용자 단위로 동적으로 결정해야 하거나 CSRF·CORS 사고가 현재 exact allowlist와 이중 제출 방어의 한계를 보인다.
