# ADR-0043: 동일 사이트 Refresh 쿠키 조건에서 무상태 Bearer 보안 체인을 사용한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-07-30
- 결정일: 2026-07-30
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [공통 인증·인가 계약](../api/common/authentication.md), [응답·오류 공통 계약](../api/common/response-and-error.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#161 Spring Security와 JWT Access Token 인증 기반 구현](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/161)
- 대체 대상: 없음

## 맥락

ADR-0005는 서버 세션이 모든 인증 요청의 필수 의존성이 되는 방식을 선택하지 않았고, ADR-0027은 Access Token은
Bearer 헤더로, Refresh Token은 호스트 전용 `HttpOnly`·`Secure`·`SameSite=Strict` 쿠키로 전달하도록 정했다.
그러나 Spring Security 기본 설정은 세션, CSRF, Basic 로그인과 폼 로그인을 포함할 수 있다.

현재 MVP는 교차 사이트 프런트엔드·외부 브라우저 클라이언트·신뢰할 수 없는 같은 사이트 하위 출처를 지원하지
않는다. 이 경계에서 보호 업무 API는 쿠키가 아니라 Bearer Access Token으로만 인증한다. Refresh Token 쿠키는
로그인·토큰 갱신·로그아웃 경로에서만 별도 처리한다.

## 결정 동인과 불변 조건

- 서버 세션과 `JSESSIONID`를 인증 상태나 Access Token 검증 결과의 저장소로 사용하지 않는다.
- 보호 업무 API는 Refresh Token 쿠키만으로 인증되거나 권한을 얻을 수 없다.
- 인증 제외 경로는 공통 인증·인가 계약의 목록과 정확히 일치해야 한다.
- 미인증은 `401 UNAUTHENTICATED`, 인증됐지만 권한이 없으면 `403 FORBIDDEN`의 `ApiResponse` 계약을 사용한다.
- 모든 요청에서 `requestId`가 Spring Security의 인증·인가 오류보다 먼저 로그 문맥에 설정되어야 한다.
- 교차 사이트 요구 또는 신뢰할 수 없는 같은 사이트 출처가 생기면 현재 CSRF·CORS 경계를 유지하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 무상태 Bearer 체인, CORS 미허용, CSRF 비활성화와 `SameSite=Strict` Refresh 쿠키 | 보호 업무 API의 쿠키 인증을 제거하고 공통 Bearer 계약을 단순하게 적용한다. 기존 단일 동일 사이트 쿠키 범위와 맞는다. | 같은 사이트 신뢰 경계가 바뀌면 CSRF 방어가 부족해질 수 있다. Refresh 쿠키 경로를 실수로 업무 API에 사용하면 안 된다. | 중간 | 추천안. 현재 MVP의 단일 동일 사이트 브라우저 조건에 맞는다. |
| 2 | Refresh·로그아웃에 CSRF 토큰 쿠키·헤더를 추가 | 교차 사이트와 같은 사이트 하위 출처 위험에 더 강하다. | 로그인·갱신·로그아웃 API와 클라이언트에 새 쿠키·헤더 계약과 토큰 발급 흐름이 필요하다. | 중간 | 교차 사이트 요구가 생길 때 채택할 수 있지만 현재 범위에는 과도하다. |
| 3 | Spring Security 기본 세션·CSRF·폼 로그인을 유지 | 기본 구성을 적게 변경한다. | JWT 인증과 세션 상태가 섞이고 공개 `POST` 인증 API가 CSRF·폼 로그인 동작에 묶인다. | 중간 | JWT Access Token과 Refresh 쿠키 계약에 맞지 않는다. |

## 결정

Spring Security는 `SessionCreationPolicy.STATELESS`로 구성한다. Basic 인증, 폼 로그인, 서버 측 로그아웃과
기본 세션 저장을 사용하지 않는다. 보호 업무 API의 인증 필터는 `Authorization: Bearer` 헤더에 있는
`token_type=ACCESS` JWT만 검증하며, `refreshToken` 쿠키는 `SecurityContext`를 만들지 않는다.

현재 MVP에서는 CORS 허용 구성을 두지 않고, 호스트 전용 `SameSite=Strict` Refresh 쿠키와 단일 신뢰 사이트를
CSRF 경계로 삼는다. 이에 따라 Spring Security의 CSRF 검사는 비활성화한다. 이 선택은 모든 업무 API가
Bearer 헤더로만 인증되고 Refresh 쿠키가 `/api/v1/auth` 경로에서만 수신된다는 조건에 한정한다.

인증 제외 경로는 공통 인증·인가 계약에 열거된 회원가입·로그인·토큰 갱신·로그아웃과 공개 지역·콘텐츠·회차
조회 API로만 허용한다. 그 밖의 모든 API는 Access Token 인증을 요구한다. 인증 실패는
`AuthenticationEntryPoint`에서 `UNAUTHENTICATED`, 인가 실패는 `AccessDeniedHandler`에서 `FORBIDDEN`으로
`ApiResponse`를 직렬화한다. `RequestIdFilter`는 보안 필터보다 먼저 실행한다.

교차 사이트 프런트엔드, 신뢰할 수 없는 같은 사이트 하위 출처, 쿠키 기반 보호 업무 API 또는 CORS 허용 요구가
생기면 CSRF 토큰 또는 동등한 방어와 허용 Origin 정책을 새 ADR과 인증 API 명세로 먼저 확정한다.

## 결과와 트레이드오프

### 기대 효과

- JWT 검증 결과가 서버 세션에 남지 않아 인스턴스 간 세션 공유가 필요 없다.
- 보호 업무 API가 Refresh Token 쿠키를 인증 수단으로 오용하지 않는다.
- 공개 경로와 기본 보호 범위, 401·403 JSON 응답을 한 보안 체인에서 검증할 수 있다.
- 현재 `SameSite=Strict` 단일 사이트 계약을 바꾸지 않고 회원가입·로그인 등 공개 `POST` API를 제공할 수 있다.

### 수용한 단점과 위험

- Access Token의 개별 즉시 폐기는 제공하지 않으며 ADR-0041의 15분 자연 만료를 수용한다.
- CORS를 설정하지 않으므로 별도 도메인의 브라우저 클라이언트는 현재 인증 API를 사용할 수 없다.
- 신뢰 경계를 잘못 넓히면 같은 사이트 하위 출처를 통한 CSRF 위험이 생길 수 있다. 이 경우 CSRF를 비활성화한 설정을 유지하면 안 된다.
- 업무 Service가 역할·지역·소유권을 DB에서 최종 확인하지 않으면 단순 인증만으로 권한이 부여되는 문제가 생긴다.

## 전환과 롤백

신규 인증 구현이므로 기존 세션이나 Cookie 기반 업무 API를 이관하지 않는다. `RequestIdFilter` 우선순위를
유지한 상태에서 무상태 보안 체인, Bearer 필터, 인증·인가 오류 직렬화를 같은 배포에 추가한다.

보안 체인 배포 결함은 해당 변경을 되돌려 기존 공개 API 동작을 복구할 수 있다. 다만 이미 발급한 Access Token은
ADR-0041의 유효기간 동안 존재할 수 있으므로, 키 유출이나 잘못된 인증 허용이 확인되면 롤백만 하지 않고
서명 키 제거와 재로그인을 함께 수행한다.

## 검증 방법

- 인증 제외 목록의 각 API가 Access Token 없이 통과하고, 목록 밖 API가 `401 UNAUTHENTICATED`를 반환하는지 검증한다.
- 누락·변조·만료·Refresh Token Bearer 요청이 `SecurityContext`를 만들지 않고 같은 401 JSON 계약을 반환하는지 검증한다.
- 인증 후 역할·지역·소유권이 부족한 API가 `403 FORBIDDEN`을 반환하는지 검증한다.
- 응답과 로그에 `requestId`는 남고 Access Token·Refresh Token 원문은 남지 않는지 검증한다.
- 응답 뒤 세션이나 `JSESSIONID`가 생성되지 않고, CORS 허용 헤더가 추가되지 않는지 보안 통합 테스트로 검증한다.
- Refresh Token 쿠키만으로 보호 업무 API를 호출할 수 없는지 검증한다.

## 대체 조건

- 교차 사이트 프런트엔드, 모바일 웹뷰 또는 별도 Origin의 브라우저 클라이언트를 지원해야 한다.
- 신뢰할 수 없는 같은 사이트 하위 출처를 운영하거나, 쿠키를 이용하는 보호 업무 API가 필요해진다.
- 외부 IdP, BFF 또는 서버 세션 방식으로 인증 경계를 변경한다.
- CSRF·CORS 사고나 검증 실패가 현재 단일 사이트 신뢰 경계가 충분하지 않음을 보여 준다.
