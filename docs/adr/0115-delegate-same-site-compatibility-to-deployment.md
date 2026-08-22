# ADR-0115: Refresh Cookie SameSite 호환성은 배포에서 확인한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-22
- 결정일: 2026-08-22
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [인증·인가 공통 계약](../api/common/authentication.md#교차-출처-corscookieorigin-검증-계약)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#1026 교차 출처 CORS와 인증 Origin 검증을 구현한다](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/1026)
- 대체 대상: [ADR-0114](0114-support-cross-origin-browser-authentication-with-configured-allowlist.md)의 허용 Origin과 API의 scheme·registrable domain 일치 기동 검증 및 그에 따른 설정 거부 범위

## 맥락

ADR-0114는 `SameSite=Strict` Refresh Cookie가 API와 같은 HTTPS site에서만 전송된다는 브라우저 제약을 근거로,
허용 Origin이 API와 scheme 및 registrable domain이 같은지 서버 시작 시 검증하도록 정했다. 그러나 교차 출처
allowlist 계약은 `SECURITY_CORS_ALLOWED_ORIGINS` 하나만 제공한다. 이 값만으로 API 공개 Origin을 알 수 없고,
registrable domain 판별을 구현하려면 별도 API Origin 설정과 Public Suffix List 의존성이 필요하다.

닫힌 #1026 구현 시도는 이 검증을 위해 계약에 없던 설정과 도메인 분석 로직을 추가했다. 이는 정확한 Origin allowlist를
사용하는 CORS·인증 명령 Origin 검증의 범위를 벗어나며, 배포 값을 서버가 중복 검증하는 비용만 늘린다.

## 결정 동인과 불변 조건

- `SECURITY_CORS_ALLOWED_ORIGINS`는 CORS와 인증 명령 Origin 검증이 함께 사용하는 유일한 allowlist다.
- 서버는 허용 목록의 각 값이 HTTPS 절대 Origin 형식인지와 와일드카드·경로·쿼리·fragment가 없는지만 검증한다.
- Refresh Token Cookie는 호스트 전용 `HttpOnly`·`Secure`·`SameSite=Strict`와 `Path=/api/v1/auth`를 유지한다.
- 허용 Origin, method, header의 정확 일치와 인증 명령의 `Origin` 검증 경계는 약화하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 허용 Origin 형식·정확 일치만 서버에서 검증하고 SameSite 호환성은 배포 E2E로 확인 | 환경 변수 하나로 CORS·Origin 검증을 일관되게 구성하며 불필요한 도메인 분석을 만들지 않는다. | 다른 site Origin을 잘못 등록하면 `SameSite=Strict` Cookie가 전송되지 않아 Refresh 흐름이 실패한다. | 낮음 | 추천안. 현재 구성 계약과 #1026의 최소 구현 범위에 맞는다. |
| 2 | API 공개 Origin과 registrable domain을 별도 설정으로 받아 서버 시작 시 검증 | 배포 오류를 기동 단계에서 일부 발견할 수 있다. | 설정 원천이 늘고 PSL·포트·호스트 정규화 정책을 구현해야 하며, 현재 계약과 이슈 범위를 확장한다. | 중간 | 현재 단계에 맞지 않는다. |
| 3 | 프런트 Origin을 코드 상수로 고정 | 허용 대상이 눈에 보인다. | 환경별 배포 Origin을 지원하지 못하고 #1023의 요구를 충족하지 못한다. | 중간 | 현재 단계에 맞지 않는다. |

## 결정

서버는 `SECURITY_CORS_ALLOWED_ORIGINS`의 HTTPS 절대 Origin 형식만 fail-closed로 검증한다. 비어 있는 목록은
교차 출처 CORS를 비활성화하는 유효한 설정이며, 비어 있지 않은 목록에 형식 오류가 있으면 서버 시작을 실패시킨다.
서버는 별도 API 공개 Origin·registrable domain 설정을 받지 않고, allowlist 값이 API와 같은 site인지 판별하거나
정규화하지 않는다.

`SameSite=Strict` Cookie를 사용하는 배포는 운영자가 API와 같은 HTTPS site인 프런트 Origin만 allowlist에 등록해야
한다. 이는 Cookie 전송을 위한 배포 전제이지 서버의 CORS 또는 Origin 검증 조건이 아니다. 배포 전 브라우저 E2E에서
`credentials: include` 로그인·재발급·로그아웃의 Cookie 저장·전송을 확인한다.

ADR-0114의 나머지 결정, 즉 정확한 allowlist 기반 credential CORS, `POST /api/v1/auth/login`,
`POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`의 서버 `Origin` 검증, 별도 CSRF Token API 미제공은 유지한다.

## 결과와 트레이드오프

### 기대 효과

- #1026 구현은 하나의 환경 변수, CORS 구성, 인증 명령 Origin 필터로 한정된다.
- CORS 허용 범위와 서버 Origin 검증이 동일한 목록을 사용하므로 정책 원천이 분산되지 않는다.
- SameSite 호환성에 필요한 도메인 분석, 추가 환경 변수, Public Suffix List 의존성을 만들지 않는다.

### 수용한 단점과 위험

- 잘못된 다른 site Origin은 서버 기동이 아니라 브라우저 E2E에서 발견한다. 이 경우 CORS 자체는 통과할 수 있지만
  `SameSite=Strict` Refresh Cookie가 전송되지 않아 인증 명령이 정상 동작하지 않는다.
- 운영자는 허용 Origin을 변경할 때 CORS 테스트뿐 아니라 Cookie 흐름을 함께 검증해야 한다.

## 전환과 롤백

현재 문서의 API와 같은 site 기동 검증 요구를 제거하고, #1026은 이 결정에 맞춰 별도 API Origin·registrable domain
설정 없이 구현한다. 구현·배포 중 오류가 나면 `SECURITY_CORS_ALLOWED_ORIGINS`를 비워 교차 출처 CORS를 차단하고,
이전 허용 목록으로 되돌린다. Cookie 속성과 인증 명령 Origin 검증은 롤백 대상이 아니다.

## 검증 방법

- 비어 있는 allowlist가 CORS 응답 헤더를 만들지 않고, 형식 오류·와일드카드·경로·쿼리·fragment가 포함된 값은 기동을 실패시키는지 검증한다.
- 허용·미허용 Origin, method, header와 인증 명령 `Origin` 누락·불일치의 CORS·403 동작을 검증한다.
- API와 같은 HTTPS site로 구성한 배포에서 `credentials: include` 로그인·재발급·로그아웃의 Refresh Cookie 흐름을 실제 브라우저 E2E로 검증한다.

## 대체 조건

- API와 다른 site의 브라우저 프런트엔드를 지원해야 한다.
- 테넌트·사용자 단위의 동적 Origin 정책 또는 외부 IdP·BFF가 필요하다.
- 배포 E2E만으로 SameSite 호환성 오류를 충분히 통제할 수 없다는 운영 증거가 축적된다.
