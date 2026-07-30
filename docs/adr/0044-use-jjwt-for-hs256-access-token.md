# ADR-0044: HS256 Access Token 구현에 JJWT를 사용한다

- 상태: 채택됨
- 기록 유형: 고급
- 기록일: 2026-07-30
- 결정일: 2026-07-30
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [공통 인증·인가 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#161 Spring Security와 JWT Access Token 인증 기반 구현](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/161)
- 대체 대상: [ADR-0041](0041-define-jwt-access-token-security-profile.md)의 Access Token 발급·검증 라이브러리 선택만 대체한다. HS256, claim, 15분 수명, `kid` 키 회전 규칙은 대체하지 않는다.

## 맥락

ADR-0041은 Access Token의 보안 프로필을 확정하면서 발급·검증 구현에 Spring Security JOSE/Nimbus를 선택했다. 그러나 P0의 현재 인증 경계는 하나의 백엔드가 대칭 키로 HS256 Access Token을 발급하고 같은 애플리케이션이 검증하는 구조다. 외부 IdP, JWKS, OAuth2 Resource Server, 비대칭 키 검증자는 아직 없다.

이 범위에서는 JWT 발급과 검증 규칙을 직접 읽고 테스트하기 쉬운 API가 필요하다. OAuth2/JWK/JWE까지 포함하는 Spring Security JOSE 의존성을 유지하면 현재 사용하지 않는 범위까지 함께 도입하게 된다. 다만 라이브러리 변경이 토큰 보안 정책 자체를 완화해서는 안 된다.

## 결정 동인과 불변 조건

- 기존 Access Token은 `Authorization: Bearer <accessToken>`으로만 수신한다.
- HS256, Base64로 주입한 256비트 이상 키, 활성 키와 직전 키의 `kid` 검증, `iss`, `aud`, `sub`, `token_type`, `iat`, `exp` claim 규칙을 유지한다.
- Access Token은 정확히 `iat + 15분`에 만료되어야 하며, 별도 만료 유예를 두지 않는다.
- Refresh Token은 Access Token으로 인증될 수 없어야 하며, 토큰 원문과 키는 로그·응답에 남기지 않는다.
- 역할·지역·소유 관계의 최종 인가는 계속 현재 DB 상태로 검증한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌리기 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | JJWT로 HS256 JWT 발급·검증 | `Jwts.builder()`와 서명 검증 파서로 현재 필요한 claim·헤더·키 검증 흐름을 직접 표현할 수 있고, OAuth2 Resource Server 기능을 추가하지 않는다. | `JwtDecoder`·JWKS·OAuth2 authority 매핑 같은 Spring Security 통합 기능은 직접 제공하지 않는다. 검증 순서와 예외 경계를 애플리케이션 코드와 테스트로 유지해야 한다. | 중간 | 추천. 단일 백엔드의 대칭 키 Access Token 범위에 맞는다. |
| 2 | Spring Security JOSE/Nimbus 유지 | JWK/JWKS, 비대칭 키, OAuth2 Resource Server 확장에 유리하다. | 현재 쓰지 않는 OAuth2·JOSE 범위까지 의존하며, 직접 사용하는 Nimbus API가 JWT 정책 코드의 의도를 흐릴 수 있다. | 낮음 | 가능하나 P0의 단일 HS256 발급·검증 범위에는 과하다. |

## 결정

Access Token의 발급·검증 라이브러리를 JJWT 0.13.0으로 변경한다. Gradle에는 `jjwt-api`를 컴파일 의존성으로, `jjwt-impl`과 Jackson 직렬화 모듈인 `jjwt-jackson`을 런타임 의존성으로 추가한다. `spring-security-oauth2-jose`는 제거한다.

`JwtAccessTokenService`는 JJWT만 사용해 HS256으로 서명하고, 파싱 전에 허용 알고리즘과 `kid`를 확인한다. 선택된 검증 키로 서명, issuer, audience, token type, subject, 발급·만료 시각과 정확한 15분 수명을 검증한다. 외부에 노출되는 인증 실패 계약은 기존 `UNAUTHENTICATED`를 유지한다.

이 결정은 ADR-0041 중 라이브러리 선택만 대체한다. Access Token의 형식과 보안 프로필은 유지하므로 HTTP API, 환경 변수 이름, 키 저장 방식, `SecurityContext` 구성 방식은 변경하지 않는다.

## 결과와 트레이드오프

### 기대 효과

- 현재 JWT 정책을 발급·검증 코드와 테스트에서 직접 따라갈 수 있다.
- P0에서 사용하지 않는 OAuth2 Resource Server와 JWKS 관련 의존 범위를 제거한다.
- 표준 HS256 JWS compact serialization과 기존 claim 계약을 유지하므로 API 클라이언트 계약은 바뀌지 않는다.

### 수용하는 단점과 위험

- 향후 외부 IdP, JWKS, 비대칭 키, OAuth2 scope 기반 인가를 도입하면 Spring Security Resource Server 또는 Nimbus 계열로의 전환을 다시 검토해야 한다.
- 라이브러리의 편의 API가 검증 규칙을 대신 결정하지 않도록, 헤더·키 식별자·정확한 만료 수명 검증을 명시적으로 유지해야 한다.
- 동일 키와 보안 프로필을 사용하는 기존 토큰의 호환은 JWT 표준 형식에 의존한다. #161 기능은 아직 배포 전이므로 이 변경에서는 운영 토큰의 이관 절차를 두지 않는다.

## 전환과 롤백

`spring-security-oauth2-jose`를 제거하고 JJWT 의존성과 구현·테스트를 같은 변경으로 전환한다. 배포 전 검증 단계에서 문제가 발견되면 해당 커밋을 되돌려 Nimbus 구현으로 복구할 수 있다. 운영 배포 후에는 활성 키와 직전 키를 동시에 허용하는 기존 회전 규칙을 유지하며, 라이브러리만 되돌리는 배포 시에도 토큰 프로필을 변경하지 않는다.

## 검증 방법

- JJWT로 발급한 정상 토큰이 보호 API의 `SecurityContext`를 구성하는지 검증한다.
- 만료, 위조 서명, 다른 `iss`·`aud`·`token_type`, 잘못된 `sub`, 알 수 없는 `kid`, HS256 이외 알고리즘, 15분이 아닌 수명을 각각 거부하는 단위 테스트를 실행한다.
- 활성 키 발급, 직전 키 검증, 직전 키 제거 후 거부를 `Clock` 기반 테스트로 검증한다.
- `./gradlew test`와 `./gradlew build`를 실행하고 `git diff --check`로 문서·코드 공백 오류를 확인한다.

## 대체 조건

- 외부 IdP, 독립 검증 서비스 또는 JWKS 기반 키 배포가 도입된다.
- 비대칭 키 검증이나 OAuth2 scope·authority 매핑이 실제 요구사항이 된다.
- 현재 직접 검증하는 보안 프로필을 Spring Security Resource Server 표준 구성으로 일관되게 대체할 근거가 생긴다.
