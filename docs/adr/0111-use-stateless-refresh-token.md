# ADR-0111: Stateless Refresh Token을 사용한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-20
- 결정일: 2026-08-20
- 관련 요구사항: [인증·프로필](../p0/auth-profile.md#fr-01-인증역할지역-권한), [인증·인가 공통 계약](../api/common/authentication.md), [토큰 갱신 API](../api/p0/auth-profile/refresh.md#3-access-token-재발급)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#968](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/968), [#969](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/969)
- 대체 대상: [ADR-0005](0005-use-jwt-access-and-rotating-refresh-tokens.md)의 회전형 Refresh Token·재사용 탐지·계열 폐기 범위, [ADR-0023](0023-manage-refresh-token-revocation-in-redis.md)의 Refresh Token Redis 상태 전부, [ADR-0052](0052-define-refresh-token-security-profile-and-fail-closed-redis-state.md)의 `jti`·`family_id`·Redis 활성 계열·회전 규칙, [ADR-0053](0053-serialize-logout-and-refresh-by-active-jti.md)의 로그아웃·갱신 Redis 직렬화 범위, [ADR-0108](0108-use-global-authority-snapshot-for-first-stage-rbac.md)의 재발급 시 `RefreshTokenService.rotate`·회전 진행 표지·새 Refresh Token 발급 범위, [ADR-0110](0110-separate-production-redis-from-api-compose.md)의 Redis가 Refresh Token 회전·폐기 상태를 제공한다는 애플리케이션 런타임 의존성 서술, [ADR-0027](0027-deliver-refresh-token-in-http-only-cookie.md)과 [ADR-0105](0105-deliver-access-token-in-json-response-body.md)의 재발급 성공 시 Refresh Cookie 교체 범위

## 맥락

현재 Refresh Token은 회전, 재사용 탐지, 계열 폐기와 로그아웃·탈퇴 처리를 위해 Redis 상태를 필수로 사용한다. 이 상태는
인증 경로를 Redis 가용성·상태 보존·원자 스크립트에 결합하고, 정상적인 다중 탭 갱신도 회전 경합으로 처리해야 한다.

현재 MVP에서는 기기별 세션 목록, 즉시 Refresh Token 폐기, 재사용 탐지보다 짧은 Access Token과 활성 사용자 확인을
우선한다. Redis는 공개 카탈로그 캐시에 계속 사용하지만 Refresh Token의 유효성 판단과 수명주기는 Redis에 두지 않는다.

## 결정 동인과 불변 조건

- Refresh Token은 보호 업무 API의 Bearer 인증 수단이 아니며, Access Token만 `Authorization` 헤더로 수신한다.
- Refresh Token 원문·서명 키는 JSON, 일반 응답 헤더, 로그, 감사 이벤트, DB와 Redis에 저장하거나 노출하지 않는다.
- 로그인에서만 14일 절대 만료 Refresh Token을 `HttpOnly`·`Secure`·`SameSite=Strict`·호스트 전용 Cookie로 발급한다.
- 재발급은 유효한 Refresh Token과 `app_user.status = ACTIVE`만 확인해 Access Token만 발급하며 Cookie를 교체하지 않는다.
- 로그아웃은 서버 상태를 바꾸지 않고 브라우저 Cookie만 만료한다. 탈퇴·비활성화 사용자는 Refresh Token이 남아 있어도 재발급할 수 없다.
- Redis 캐시·의존성·배포 구성은 유지하되 Refresh Token 전용 키, Redis 장애 페일 클로즈와 인증 Redis 분리는 제공하지 않는다.
- Refresh Token 회전·계열 상태·원자 스크립트·경합 예외를 함께 유지하지 않아도 되도록 인증 수명주기의 변경 지점과 장애 진단 범위를 줄인다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 14일 절대 만료 Stateless Refresh Token | Redis 상태·회전 경합 없이 재발급 경로가 단순하고 같은 Token을 여러 탭에서 사용할 수 있다. 회전·계열·원자 스크립트·장애 예외를 함께 변경·검증할 필요가 없어 유지보수 범위가 작다. | 복사된 Refresh Token은 로그아웃 뒤에도 만료 또는 계정 비활성화까지 재사용될 수 있고 재사용 탐지를 하지 못한다. | 중간. 인증 구현·테스트와 API 계약을 함께 전환한다. | 현재 MVP의 단일 사이트 Cookie 경계와 짧은 Access Token에 적합하다. |
| 2 | Redis 회전·계열 폐기 유지 | 재사용 탐지와 즉시 계열 폐기가 가능하다. | Redis 상태 소실·장애·원자 스크립트와 갱신 경합을 계속 운영해야 한다. | 낮음. 현행 유지다. | 현재 단순화 목표에 맞지 않는다. |
| 3 | 서버 세션으로 전환 | 로그아웃과 강제 폐기를 서버에서 즉시 반영할 수 있다. | 모든 보호 요청이 세션 저장소에 의존하고 Cookie 인증·CSRF 경계를 다시 설계해야 한다. | 높음. 인증 모델과 클라이언트를 함께 바꿔야 한다. | 현재 범위에 과도하다. |

## 결정

검토한 선택지 1인 **14일 절대 만료 Stateless Refresh Token**을 채택한다. Redis 회전·계열 폐기를 유지하는 경우의
상태 키, 원자 스크립트, Redis 장애와 갱신 경합 예외를 인증 변경마다 함께 유지·검증하지 않도록 해 유지보수와 장애 진단
범위를 줄이는 것이 선택 이유다. 즉시 서버 측 폐기와 재사용 탐지는 제공하지 않는 대신, 짧은 Access Token과 활성 회원
조회로 현재 MVP의 인증 경계를 유지한다.

Refresh Token은 Access Token과 별도의 HS256 JWS로 발급·검증한다. 활성 Refresh 서명 키 하나만 `kid`와 함께 외부
설정 또는 비밀 저장소에서 주입하고, 키 교체 시 이전 키 Refresh Token은 재로그인을 요구한다. 다음 claim만 발급·검증한다.

| Claim | 값 | 용도 |
| --- | --- | --- |
| `iss` | `regional-event-platform` | 발급자 검증 |
| `aud` | `regional-event-refresh` | Access Token과 대상 분리 |
| `sub` | 양의 사용자 ID의 10진 문자열 | 재발급 대상 식별 |
| `token_type` | `REFRESH` | Access Token과 사용 경계 분리 |
| `iat` | 로그인 발급 시각 | 수명 검증 |
| `exp` | `iat + 14일` | 절대 만료 검증 |

`jti`, `family_id`, 계열·소비·폐기 상태는 발급·검증하지 않는다. Refresh Token은 로그인에서만 새로 발급하며
`Max-Age=1209600`초 Cookie로 전달한다. 재발급 성공은 같은 Cookie를 유지하고 새 Access Token만
`data.accessToken`으로 반환한다. 같은 유효 Token의 반복·동시 재발급은 모두 허용하며 각 요청은 현재 활성 사용자와
권한 원천으로 Access Token을 다시 발급한다.

로그아웃은 Refresh Token의 유효성을 확인하거나 서버 상태를 폐기하지 않고 동일 Cookie를 만료한다. 따라서 브라우저에서
제거되기 전에 복사된 Refresh Token은 14일 만료 전까지 재발급에 사용할 수 있다. 이 한계는 Access Token의 15분
수명과 `ACTIVE` 사용자 확인으로만 제한한다. 탈퇴 또는 계정 비활성화가 먼저 완료되면 재발급은 `401 UNAUTHENTICATED`로
거부하고 제출 Cookie를 만료한다.

## 결과와 트레이드오프

### 기대 효과

- Refresh Token 갱신·로그아웃·탈퇴가 Redis 상태와 회전 경합에 의존하지 않는다.
- 다중 탭의 같은 Refresh Token 재발급이 `409` 충돌이나 계열 폐기를 만들지 않는다.
- Redis는 공개 카탈로그 캐시의 런타임 의존성으로만 유지되어 인증 실패가 Redis 가용성에 좌우되지 않는다.

### 수용한 단점과 위험

- Refresh Token 탈취·재사용을 탐지하거나 로그아웃으로 서버 측 즉시 무효화할 수 없다.
- 로그아웃 뒤 같은 Token을 보관한 클라이언트는 만료 전까지 재발급할 수 있다.
- Refresh 서명 키 교체는 모든 기존 Refresh Token을 무효화해 재로그인을 유발한다.

## 전환과 롤백

Redis Refresh Token 키와 MySQL Refresh Token 테이블은 이 정책에서 사용하지 않으므로 데이터 이관은 없다.
후속 구현은 새 문서 계약을 기준으로 Redis 상태·회전 코드와 테스트를 제거하고 로그인·재발급·로그아웃·탈퇴를 함께
전환한다. 기존 회전형 Token과 Redis 상태를 호환하지 않으며, 전환 배포 시 기존 Refresh Cookie는 만료 또는 새 로그인으로
교체한다.

문제 발생 시 회전형 Redis 상태 구현으로 부분 롤백하지 않는다. Stateless Token 발급·검증의 마지막 정상 버전을
유지하거나 Refresh Token 발급·재발급을 중단해 재로그인을 요구한다. 재사용 탐지나 즉시 폐기가 다시 필요해지면
저장소·장애·이관 정책을 포함한 새 ADR로 이 결정을 대체한다.

## 검증 방법

- 로그인은 14일 Cookie를 발급하고, 재발급은 유효한 Token으로 Access Token만 반환하며 `Set-Cookie`를 반환하지 않는지 검증한다.
- 같은 Refresh Token의 반복·동시 재발급과 로그아웃 뒤 수동 재제시가 만료 전에는 성공하는지 검증한다.
- 누락·변조·만료 Token, 탈퇴·비활성 사용자가 제출한 유효 Token은 `401 UNAUTHENTICATED`와 만료 Cookie를 반환하는지 검증한다.
- Refresh Token 운영 코드와 테스트에서 `jti`, `family_id`, 회전·계열 폐기·Redis Refresh 상태·`REFRESH_TOKEN_CONFLICT`·`AUTH_SERVICE_UNAVAILABLE` 참조가 없는지 확인한다.
- Redis 공개 카탈로그 캐시와 `spring-data-redis` 의존성·설정이 유지되는지 확인한다.

## 대체 조건

- 로그아웃·탈퇴·권한 변경에서 Refresh Token의 즉시 서버 측 무효화가 제품 또는 보안 요구가 된다.
- 탈취·재사용 사고 또는 감사 요구로 Refresh Token 재사용 탐지·기기별 세션 관리가 필요해진다.
- 외부 IdP, OAuth/OIDC 또는 서버 세션으로 인증 수명주기 책임을 이전한다.
