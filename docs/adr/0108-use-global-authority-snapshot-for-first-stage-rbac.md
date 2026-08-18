# ADR-0108: Access Token 전역 authority snapshot으로 1차 RBAC를 적용한다

- 상태: 채택됨
- 기록 유형: 대체
- 기록일: 2026-08-18
- 결정일: 2026-08-18
- 관련 요구사항: [FR-01 인증·역할·지역 권한](../p0/auth-profile.md#fr-01-인증역할지역-권한), [전체관리자 정책](../p1/platform-admin.md#3-전체관리자-정책), [공통 인증·인가 계약](../api/common/authentication.md)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#929 Access Token 전역 역할 snapshot RBAC 정책과 권한 행렬을 확정](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/929)
- 관련 ADR: [ADR-0045](0045-use-stateless-bearer-security-with-same-site-refresh-cookie.md), [ADR-0064](0064-separate-privileged-account-class-from-ordinary-roles.md)
- 대체 대상: [ADR-0043](0043-define-jwt-access-token-security-profile.md)의 Access Token에서 역할 claim을 제외하고 `SecurityContext`를 사용자 ID와 빈 authorities로만 구성하며, 전역 역할 판단까지 요청 시점 DB에만 맡긴 결정 범위. HS256, `kid`, `iss`, `aud`, `token_type`, `sub`, 정확한 15분 수명, 지역·소유권·활성 상태·업무 상태의 DB 최종 검증은 대체하지 않는다.

## 맥락

ADR-0043은 역할·담당 지역·리소스 소유권이 변할 수 있으므로 JWT claim을 최종 권한 근거로 삼지 않도록 했다. 이 결정은
지역 배정 해제, 소유권 변경, 탈퇴 같은 최신 상태를 DB에서 즉시 반영하는 데 적합하다. 그러나 현재 보안 체인은 공개 경로
외 요청을 인증 여부만으로 통과시키므로, 새 UseCase가 역할 인가 호출을 누락하면 URL 수준에서 이를 막지 못한다.

전역 역할만 Access Token의 짧은 수명 안에서 snapshot으로 사용하면 Spring Security가 명백히 잘못된 역할의 요청을 먼저
차단할 수 있다. 반대로 담당 지역, 리소스 소유권, 계정 활성 상태, 예약·콘텐츠·환불 등 업무 상태까지 claim에 넣으면 변경
직후의 값이 최대 15분간 오래되어 최종 인가 근거로 사용될 위험이 커진다.

일반 역할과 고권한 계정의 원천도 다르다. ADR-0064에 따라 `ORDINARY` 계정은 `user_role_assignment`의 일반 역할만,
`PRIVILEGED` 계정은 `platform_admin_assignment`의 고권한 등급만 가질 수 있다. 두 테이블과 `account_kind`의 교차
일관성은 DB 제약만으로 완전히 강제되지 않으므로, token 발급 시 상충 데이터를 합쳐 권한을 넓히지 않는 규칙이 필요하다.

## 결정 동인과 불변 조건

- Access Token에는 전역 authority snapshot만 넣고, 발급 시 서버가 DB의 현재 권한 원천에서 계산한다.
- 역할 snapshot은 정확한 Access Token 수명인 최대 15분만 유효하다. 역할 변경 뒤 그 기간의 1차 RBAC 지연을 수용한다.
- 지역 ID·담당 지역 목록·리소스 소유권·개인정보·계정 활성 상태·업무 상태는 Access Token에 넣지 않는다.
- Spring Security는 claim의 전역 authority로 URL 단위 1차 RBAC를 수행하고, DB는 지역·소유권·활성 상태·업무 상태를 최종 검증한다.
- 잘못된 형식·중복·미허용 authority를 가진 Access Token은 인증 실패인 `401 UNAUTHENTICATED`로 거부한다.
- 유효하지만 해당 URL의 authority가 부족한 요청은 인가 실패인 `403 FORBIDDEN`으로 거부한다.
- OAuth/OIDC, 외부 IdP, 권한 캐시, 지역·소유권 claim, Access Token 블랙리스트, 서버 세션 전환은 이번 결정 범위에 넣지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 전역 authority snapshot으로 Spring Security 1차 RBAC를 수행하고 DB 최종 인가를 유지 | 역할 없는 요청을 URL 경계에서 빠르게 차단하면서 지역·소유권·상태의 최신성을 보존한다. 현재 HMAC Access Token과 Bearer 체인을 유지한다. | 역할 철회 뒤 최대 15분 동안 1차 관문은 기존 authority를 사용할 수 있다. claim 발급·전환·검증 테스트가 필요하다. | 중간 | 추천안이며 채택한다. |
| 2 | 역할 claim 없이 모든 역할 검증을 DB에서만 수행 | 역할 변경을 즉시 반영하고 기존 구현을 유지한다. | 새 API에서 DB 역할 검증을 누락해도 SecurityConfig가 발견하지 못한다. | 낮음 | 역할별 URL 차단 요구를 충족하지 못한다. |
| 3 | 역할·담당 지역·소유권·활성 상태를 모두 JWT claim으로 발급 | 일부 DB 조회를 줄이고 URL·리소스 권한을 넓게 토큰에서 판단할 수 있다. | 지역 배정, 소유권, 탈퇴와 업무 상태가 오래된 claim에 묶인다. 토큰 크기·전환·권한 철회 위험이 커진다. | 높음 | 현재 범위에 부적합하다. |

## 결정

### Access Token authority claim

Access Token에는 항상 `authorities` claim을 JSON 문자열 배열로 넣는다. 배열은 집합으로 해석하며 빈 배열은 허용하지만,
중복 원소는 허용하지 않는다.

| 항목 | 계약 |
| --- | --- |
| claim 이름 | `authorities` |
| JSON 형식 | 중복 없는 문자열 배열. 빈 배열 `[]`은 유효하다. |
| 허용값 | `ROLE_VISITOR`, `ROLE_OPERATOR`, `ROLE_REGION_ADMIN`, `ROLE_PLATFORM_ADMIN`, `ROLE_SUPER_ADMIN` |
| 검증 | claim이 배열이 아니거나, `null`·문자열이 아닌 원소·중복 원소·허용 목록 밖 원소를 포함하면 Access Token 전체를 `401 UNAUTHENTICATED`로 거부한다. |
| 제외값 | 지역 ID·담당 지역 목록·리소스 소유권·계정 활성 상태·업무 상태·개인정보·Refresh Token 계열 식별자를 넣지 않는다. |

`ROLE_` 접두사는 Spring Security의 authority 관례와 맞춘다. 필터는 검증한 문자열을 그대로
`SimpleGrantedAuthority`로 바꾸고, 권한 행렬은 숨은 접두사 변환이 있는 `hasRole`이 아니라 정확한 문자열을 비교하는
`hasAuthority` 또는 `hasAnyAuthority`를 사용한다.

### authority 원천과 fail-closed 규칙

Access Token은 활성 회원 확인 뒤에만 발급한다. 활성 회원의 authority는 아래 표의 `ACTIVE` 배정만 원천으로 삼는다.
`REVOKED` 일반 역할과 `INACTIVE` 고권한 배정은 원천이 아니다.

| `account_kind` | 활성 일반 역할 배정 | 활성 고권한 배정 | 발급 `authorities` |
| --- | --- | --- | --- |
| `ORDINARY` | 0개 이상 | 없음 | 각 `VISITOR`·`OPERATOR`·`REGION_ADMIN`의 `ROLE_VISITOR`·`ROLE_OPERATOR`·`ROLE_REGION_ADMIN` 합집합 |
| `PRIVILEGED` | 없음 | `SUPER_ADMIN` | `ROLE_SUPER_ADMIN` 하나 |
| `PRIVILEGED` | 없음 | `PLATFORM_ADMIN` | `ROLE_PLATFORM_ADMIN` 하나 |
| `PRIVILEGED` | 없음 | 없음 또는 `INACTIVE` | 빈 배열 `[]` |
| 모든 계정 분류 | 계정 분류와 맞지 않는 활성 원천이 하나라도 있음, 또는 일반·고권한 활성 원천이 함께 있음 | 무관 | 빈 배열 `[]` |

일반 역할은 서로 다른 역할끼리 동시에 활성일 수 있으므로 `ORDINARY` 계정은 활성 일반 역할 전체를 합친다. 반대로
`SUPER_ADMIN`에게 `ROLE_PLATFORM_ADMIN`을 함께 발급하지 않는다. 두 authority가 모두 허용되는 전체관리자 API는
권한 행렬에서 두 값을 명시적으로 허용하고, 고권한 계정 생성·비활성화처럼 슈퍼관리자 전용 API는
`ROLE_SUPER_ADMIN`만 허용한다. 별도 `RoleHierarchy`는 도입하지 않는다.

운영자 신청이 `PENDING`인 활성 일반 회원과 비활성 고권한 배정의 활성 계정은 정상적으로 권한이 없을 수 있으므로 빈 배열은
유효한 claim이다. 다만 계정 분류와 반대되는 활성 배정, 또는 두 종류 활성 배정의 동시 존재는 ADR-0064에 어긋나는 데이터다.
이 경우 어느 역할도 우선하거나 합치지 않고 빈 배열을 발급해 역할 보호 API를 fail-closed 처리하며, 운영자가 데이터
정합성을 조사할 수 있도록 보안 이벤트로 관측한다.

로그인과 Access Token 재발급은 동일한 권한 resolver를 사용한다. 재발급은 기존 Refresh Token 회전 순서와 사용자 행 잠금
경계를 바꾸지 않는다. 권한 resolver는 회전 진행 표지를 확보한 뒤 `RefreshTokenService.rotate`의 prepare callback 안에서
사용자 행 잠금으로 `ACTIVE` 상태를 재확인한 후 snapshot을 계산한다. 이 확인 또는 resolver가 실패하면 진행 표지를 취소하고
Access Token이나 새 Refresh Token을 발급하지 않는다.

### 1차 RBAC와 DB 최종 인가의 경계

검증에 성공한 Access Token은 기존처럼 `Long userId`를 principal로 유지하고, authority 배열만
`Authentication`의 authorities에 추가한다. 기존 Controller의 `@AuthenticationPrincipal Long userId` 사용은 바꾸지
않는다.

Spring Security의 권한 행렬은 전역 역할이 명백히 부족한 요청을 먼저 `403 FORBIDDEN`으로 차단한다. 이 통과는 업무 수행
권한의 최종 성공을 뜻하지 않는다. UseCase와 authorization service는 아래 항목을 요청 시점 DB에서 계속 확인한다.

- `app_user.status = ACTIVE`와 요청자에게 요구되는 `ORDINARY`/`PRIVILEGED` 계정 분류
- 인증 전용 API가 명세상 현재 활성 방문자를 요구하는 경우의 `ACTIVE VISITOR` 배정. 이 조회는 `ORDINARY` 계정 분류를 함께 확인한다.
- 지역 관리자·운영자의 담당 지역과 대상 지역의 일치
- 콘텐츠·회차·예약·후기·쿠폰 등 리소스의 소유자와 인증 주체의 일치
- 공개 여부, 예약·결제·환불·콘텐츠·회차의 업무 상태 및 상태 전이 조건
- 일반 역할 배정을 담당 지역 관계로 사용할 때의 현재 관계와 계정 분류의 일치

역할 보호 HTTP 경로에서는 SecurityConfig가 이미 판정한 일반 역할 또는 고권한 등급의 현재 `ACTIVE` 여부를 UseCase에서
다시 전역 역할 조건으로 조회해 거부하지 않는다. 따라서 역할·등급 철회 전 발급된 Token은 만료 전 기존 authority로
1차 RBAC를 통과할 수 있다. 반면 계정 비활성화·탈퇴, 담당 지역 관계 해제, 소유권 변경·업무 상태 변경은 DB 최종 검증에서
즉시 거부된다. 지역 관계를 얻기 위해 일반 역할 배정을 조회할 수 있으며, 이 조회는 `ORDINARY` 조건을 포함한다.

SecurityConfig authority 관문을 거치지 않는 내부·비동기 호출은 Token snapshot에 기대지 않는다. 기존 DB 전역 역할
검증을 유지하거나 동등한 호출 경계를 명시한다.

### claim 누락 전환

기존 Access Token에는 `authorities` claim이 없다. 별도 호환 claim이나 수명 연장은 만들지 않고, 기존 15분 자연 만료를
이용해 다음 두 단계로 전환한다.

1. 호환 배포에서 새 발급기는 모든 새 Access Token에 `authorities`를 넣고, 검증기는 누락 claim을 빈 배열로 해석한다.
   이 기간의 구 Token은 인증 전용 URL은 통과할 수 있지만 역할 보호 URL에서는 `403 FORBIDDEN`을 받는다.
2. 마지막 구 발급 인스턴스가 종료된 시각을 `T_complete`로 기록하고, `T_complete + 15분` 뒤 엄격 배포로 전환한다.
   엄격 배포부터 누락 claim은 `401 UNAUTHENTICATED`다.

`T_complete`는 배포 도구에서 모든 구 버전 인스턴스가 트래픽을 받지 않는 것이 확인된 시각이다. 전환 전에는 claim 없는
정상 서명 Token의 인증 전용 요청과 역할 보호 요청을 각각 검증하고, 전환 뒤에는 같은 유형의 Token이 401인지 확인한다.
새 버전 Token은 이전 코드가 authority를 무시하므로 엄격 배포를 호환 배포로 되돌리는 롤백은 가능하다.

이 호환·엄격 전환은 Token 검증기의 `authorities` claim 누락 처리 범위만 정한다. #932의 SecurityConfig matcher 또는
#933의 DB 최종 인가 정리 배포·롤백만으로 `T_complete`, 호환 해석, 엄격 거부 상태를 바꾸지 않는다. 엄격 전환 뒤
claim 없는 Token은 1차 RBAC나 DB 최종 인가에 도달하기 전에 항상 `401 UNAUTHENTICATED`다.

운영상 발급기까지 claim 없는 구 버전으로 되돌리면 이는 단순 #932·#933 롤백이 아니라 이 절의 호환 배포 복귀다.
검증기도 호환 해석으로 함께 되돌리고, 모든 구 발급 인스턴스가 다시 종료된 새 시각을 `T_complete`로 기록한 뒤 그
시각부터 15분을 다시 기다린다.

### 범위 제외

OAuth/OIDC의 외부 issuer·JWKS·scope·외부 subject 연결, 서버 세션 전환, Access Token 전역 블랙리스트, 지역·소유권
claim, 권한 캐시는 이 ADR에서 채택하지 않는다. 동일 사이트 Refresh Cookie, stateless Bearer 체인과 CORS·CSRF 경계는
ADR-0045를 유지한다.

## 결과와 트레이드오프

### 기대 효과

- 역할 보호 URL은 도메인 UseCase에 도달하기 전에 최소 전역 authority를 확인한다.
- 일반 역할과 고권한 등급의 원천을 한 authority 계약으로 정리하면서 ADR-0064의 계정 분리 원칙을 유지한다.
- 지역·소유권·활성 상태·업무 상태를 claim에 복제하지 않아 DB 최신 상태를 최종 근거로 유지한다.
- 기존 principal 타입과 Bearer 인증 경계를 유지해 Controller 및 공개 HTTP 계약의 변경을 최소화한다.

### 수용한 단점과 위험

- 역할 철회 뒤 최대 15분 동안 기존 Token은 SecurityConfig의 전역 역할 관문을 통과할 수 있다.
- 역할 보호 API는 1차 RBAC 뒤에도 DB 최종 인가를 수행하므로 계정·지역 관계·소유권·업무 상태 조회가 완전히 사라지지 않는다.
- 두 단계 전환의 `T_complete` 기록을 놓치면 누락 claim Token의 허용·거부 시점이 불명확해진다.
- 빈 authority가 상충 데이터와 정상적인 무권한 상태를 같은 공개 결과로 보이게 하므로, 운영 관측에서 두 원인을 구분해야 한다.

## 전환과 롤백

후속 구현은 #929 권한 행렬을 먼저 적용 기준으로 삼고, #931에서 호환 배포용 공통 authority resolver와 claim
발급·검증을, #932에서 SecurityConfig의 1차 RBAC를, 누락 claim 엄격 전환 Task에서 `T_complete + 15분` 이후 401
전환을, #933에서 DB 최종 인가 경계를 정리한다. HTTP 경로·요청·응답·오류 코드와 DB schema는 이 ADR만으로 바꾸지
않는다.

#933은 #932의 역할 보호 matcher가 배포된 상태에서만 전역 역할의 DB 중복 검사를 제거한다. #933 배포 뒤 #932를
되돌릴 때는 #933도 함께 되돌리거나 DB 전역 역할 fallback을 먼저 복구한다. 두 Task를 독립적으로 롤백해
authenticated-only 체인만 남기는 배포는 허용하지 않는다.

호환 배포 중 결함이 발견되면 #933 전에는 authority claim을 읽지 않는 이전 인증 체인으로 되돌릴 수 있다. 이미 발급된
Token은 ADR-0043의 15분 수명 안에서만 존재한다. 권한을 과도하게 허용한 결함이나 키 유출은 단순 롤백에 의존하지 않고
기존 키 제거·재로그인 절차를 따른다.

## 검증 방법

- 로그인과 Access Token 재발급이 동일 resolver로 활성 역할·등급만 `authorities`에 넣는지 검증한다.
- `ORDINARY`의 복수 활성 일반 역할, `PRIVILEGED`의 각 등급, 정상 빈 배열, 상충 활성 원천의 빈 배열을 단위·통합 테스트로 검증한다.
- `SUPER_ADMIN`이 `ROLE_PLATFORM_ADMIN` 없이도 공통 전체관리자 URL을 통과하고, 슈퍼관리자 전용 URL은 `ROLE_SUPER_ADMIN`만 통과하는지 검증한다.
- 배열이 아닌 claim, `null`·비문자·중복·미허용 authority, 엄격 전환 뒤 누락 claim이 모두 `401 UNAUTHENTICATED`인지 검증한다.
- 호환 기간의 누락 claim Token은 인증 전용 URL에서 인증되고 역할 보호 URL에서 `403 FORBIDDEN`인지 검증한다.
- 권한 없는 유효 Token은 SecurityConfig에서 403인지, 지역 배정 해제·소유권 변경·비활성 계정은 기존 authority가 있어도 DB 단계에서 즉시 거부되는지 검증한다.
- 계정 분류와 맞지 않는 활성 일반 역할 배정은 빈 authority Token을 발급하고, 인증 전용 활성 방문자 API에서 `ORDINARY` 조건으로 403인지 검증한다.
- #932와 #933을 함께 되돌리거나 DB fallback을 먼저 복구하는 롤백 절차를 검증한다.
- 배포 기록의 `T_complete`, 호환 기간의 누락 claim 계수, 엄격 전환 뒤 누락 claim 401을 운영 로그·배포 기록으로 확인한다.

## 대체 조건

- 역할 철회를 Access Token 수명보다 빠르게 전역 차단해야 하는 제품·보안 요구가 확정된다.
- 외부 IdP, OAuth/OIDC 또는 다중 검증 서비스가 도입되어 authority 원천과 JWT 서명 경계가 달라진다.
- 지역·소유권을 토큰에서 처리해야 할 명확한 요구가 생기고, DB 최신성 손실·권한 철회·이관 비용을 감수할 근거가 마련된다.
