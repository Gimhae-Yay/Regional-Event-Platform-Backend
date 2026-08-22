# 인증·인가

## 인증 전달 방식

| 항목 | 계약 |
| --- | --- |
| 인증 헤더 | `Authorization: Bearer <accessToken>` |
| Access Token 성공 응답 | 로그인·토큰 갱신 성공 응답은 JSON 본문의 `data.accessToken`에 Access Token을 포함하고 `Authorization` 응답 헤더를 포함하지 않는다. 보호 업무 API 요청은 기존과 같이 `Authorization: Bearer <accessToken>` 헤더를 사용한다. ([ADR-0105](../../adr/0105-deliver-access-token-in-json-response-body.md)) |
| Refresh Token 전달 | 로그인 성공 응답만 `Set-Cookie: refreshToken=<refreshToken>; Max-Age=1209600; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`를 포함한다. 토큰 갱신 성공 응답은 Cookie를 교체하지 않고 `data.accessToken`만 반환한다. `Domain`은 생략해 호스트 전용 쿠키로 유지하며 Refresh Token은 JSON·`Authorization` 헤더에 넣지 않는다. ([ADR-0111](../../adr/0111-use-stateless-refresh-token.md)) |
| 갱신·로그아웃 한계 | Refresh Token은 상태를 저장하거나 회전하지 않는다. 같은 유효 Token의 반복·동시 갱신은 허용되며, 로그아웃은 브라우저 Cookie만 만료한다. 복사된 Token은 만료 또는 계정 비활성화 전까지 재발급에 사용할 수 있다. ([ADR-0111](../../adr/0111-use-stateless-refresh-token.md)) |
| 토큰 만료·무효 | `401 Unauthorized`, `UNAUTHENTICATED` |

Refresh Token은 `Path=/api/v1/auth` 범위의 인증 API에서만 수신하며 보호 업무 API의 인증 수단으로 사용할 수 없다.

## 교차 출처 CORS·Cookie·Origin 검증 계약

교차 출처 브라우저 지원은 [ADR-0114](../../adr/0114-support-cross-origin-browser-authentication-with-configured-allowlist.md)를
따른다. 실제 배포 Origin은 문서나 코드 상수가 아니라 다음 환경별 설정으로만 관리한다.

| 구성 키 | 환경 변수 | 값과 검증 규칙 |
| --- | --- | --- |
| `security.cors.allowed-origins` | `SECURITY_CORS_ALLOWED_ORIGINS` | 쉼표로 구분한 HTTPS Origin allowlist다. 비어 있으면 교차 출처를 허용하지 않으며, 아래 두 site 설정을 요구하지 않는다. 비어 있지 않으면 각 값은 API 공개 Origin과 같은 scheme 및 명시한 site 기준 도메인에 속해야 한다. |
| `security.cors.api-public-origin` | `SECURITY_CORS_API_PUBLIC_ORIGIN` | allowlist가 비어 있지 않을 때 필수인 API의 공개 HTTPS Origin이다. 허용 Origin과 같은 site 기준에 속해야 한다. |
| `security.cors.site-registrable-domain` | `SECURITY_CORS_SITE_REGISTRABLE_DOMAIN` | allowlist가 비어 있지 않을 때 필수인 신뢰된 site 기준 도메인이다. API 공개 Origin과 모든 허용 Origin의 host는 이 값과 같거나 그 하위 도메인이어야 한다. 현재 의존성에는 Public Suffix List 처리기가 없으므로 마지막 두 라벨 비교를 사용하지 않는다. |

API 공개 Origin과 허용 Origin은 `scheme + host + port`만 가지는 HTTPS Origin이어야 한다. IP host·경로·쿼리·fragment·마지막 `/`·사용자 정보·와일드카드·정규식을 허용하지 않으며, 조건을 벗어난 값은 서버 시작을 실패시킨다. HTTPS 기본 포트 `:443`은 생략하고 host는 소문자로 정규화해 브라우저 `Origin` 직렬화와 일치시킨다. 실제 배포 값은 저장소에 넣지 않고 위 환경 변수로만 주입한다.

서버는 요청 `Origin`이 allowlist와 정확히 일치할 때만 `Access-Control-Allow-Origin`에 그 Origin을 넣고
`Access-Control-Allow-Credentials: true`, `Vary: Origin`을 반환한다. 허용 method는 `GET`, `POST`, `PUT`,
`PATCH`, `DELETE`, `OPTIONS`이며 허용 request header는 `Authorization`, `Content-Type`, `Accept`, `Idempotency-Key`다.
Origin, credential, method, header에 와일드카드를 쓰지 않는다. 사전 요청은 위 allowlist와 method·header만으로 인증 전에
처리하며, 미허용 Origin에는 CORS 응답 헤더를 추가하지 않는다.

`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` 요청은 브라우저가 설정한
`Origin` header를 반드시 포함해야 한다. 서버는 이 값이 allowlist와 정확히 일치할 때만 처리하며, Origin 누락·불일치는
`403 FORBIDDEN`이다. 이 경로는 교차 출처 Refresh Cookie를 수신하거나 사용하므로 CORS 처리와 별도로 Origin을
검증한다. 별도 CSRF Cookie, 전용 요청 header, Token bootstrap API는 제공하지 않는다.

로그인·토큰 갱신·로그아웃 요청은 `credentials: include`를 사용해야 교차 출처에서 Refresh Cookie를 저장하거나
전송할 수 있다. 보호 업무 API는 계속 Bearer Access Token으로만 인증하므로 Refresh Cookie를 업무 API 인증·인가에
사용하지 않는다.

## 전역 authority snapshot 계약

[ADR-0108](../../adr/0108-use-global-authority-snapshot-for-first-stage-rbac.md)에 따라 Access Token은 전역 역할만
`authorities` claim으로 가진다. 이 값은 요청자가 제출하거나 수정할 수 없고 로그인·토큰 갱신 시 서버가 현재 DB의
활성 권한 원천에서 만든 snapshot이다.

| 항목 | 계약 |
| --- | --- |
| claim 이름 | `authorities` |
| 형식 | 중복 없는 JSON 문자열 배열. 빈 배열 `[]`은 유효하다. |
| 허용 authority | `ROLE_VISITOR`, `ROLE_OPERATOR`, `ROLE_REGION_ADMIN`, `ROLE_PLATFORM_ADMIN`, `ROLE_SUPER_ADMIN` |
| 발급 원천 | `ORDINARY` 계정은 활성 일반 역할(`VISITOR`·`OPERATOR`·`REGION_ADMIN`)의 합집합이고, 활성 일반 역할이 없으면 빈 배열 `[]`이다. `PRIVILEGED` 계정은 활성 고권한 등급(`SUPER_ADMIN` 또는 `PLATFORM_ADMIN`) 하나이며 활성 고권한 배정이 없으면 빈 배열 `[]`이다. |
| 정상 빈 배열 | 회원가입에서 `OPERATOR`를 선택해 `PENDING` 운영자 신청만 가진 활성 `ORDINARY` 회원처럼 활성 일반 역할이 없는 정상 회원은 `authorities=[]` Token을 발급받는다. 인증 전용 API에는 사용할 수 있지만 역할 보호 URL은 `403 FORBIDDEN`이다. |
| 상충 원천 | 계정 분류와 맞지 않는 활성 배정 또는 일반·고권한 활성 배정의 동시 존재는 정상 빈 배열이 아니다. authority를 합치거나 빈 배열로 대체하지 않고 Access Token과 Refresh Token 발급을 거부한다. |
| `SUPER_ADMIN` | `ROLE_PLATFORM_ADMIN`을 중복 보유하지 않는다. 전체관리자 본인 조회·사용자 목록·지역·역할 관리 API는 두 authority를 함께 허용하고, 고권한 계정 목록 조회·생성·비활성화는 `ROLE_SUPER_ADMIN`만 허용한다. |
| 형식 오류 | 배열이 아니거나 `null`·비문자·중복·미허용 값이 있으면 유효하지 않은 Access Token으로 `401 UNAUTHENTICATED`다. |
| claim 누락 전환 | 호환 배포에서는 빈 배열로 해석한다. 마지막 구 발급 인스턴스 종료 시각 `T_complete` 뒤 15분이 지나면 누락 claim은 `401 UNAUTHENTICATED`다. |

권한 행렬은 `hasAuthority`·`hasAnyAuthority`로 정확한 authority 문자열을 비교하는 1차 RBAC다. 지역 ID·담당 지역 목록,
리소스 소유권, 계정 활성 상태, 공개 여부와 업무 상태는 claim에 넣지 않으며 UseCase의 DB 최종 검증을 유지한다. 역할 보호
HTTP 경로에서 전역 일반 역할·고권한 등급과 그 배정의 현재 활성 상태를 DB로 다시 판정하지 않는다. 단, 지역 관리자·운영자는
claim에 없는 현재 담당 지역 범위를 얻기 위해 활성 배정 관계를 조회하고 대상 지역과 비교한다. 따라서 배정 철회·지역 변경은
기존 Token이 있어도 DB 단계에서 즉시 거부될 수 있다. 인증 전용 API가 현재 활성 방문자를 요구하면 그 DB 조회는
`app_user.status = ACTIVE`, `ACTIVE VISITOR`, `account_kind = ORDINARY`를 함께 확인한다.

## 권한 행렬

아래 경로는 Spring Security `requestMatcher` 패턴이다. `*`는 한 경로 세그먼트, `**`는 하위 경로 전체를 뜻한다.
위 행이 먼저 평가되는 것이 계약이므로, 예외 경로는 넓은 prefix 패턴보다 앞에 둔다. `인증 전용`은 유효한 Access Token만
요구하며 전역 authority는 요구하지 않는다. 이 경우에도 표의 DB 최종 검증 조건은 생략되지 않는다.

| 우선순위 | HTTP method | 정확한 path/pattern | 접근 | 최소 authority | 선택 인증 | DB 최종 검증 |
| --- | --- | --- | --- | --- | --- |
| 1 | `POST` | `/internal/performance/fixtures/reset` | 성능 전용 내부 API | 해당 없음 | 아니오 | Access Token 대신 성능 전용 설정과 `X-Performance-Fixture-Token`을 별도로 검증한다. 운영 환경에는 Controller를 등록하지 않는다. |
| 2 | `POST` | `/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout` | 인증 제외 | 해당 없음 | 아니오 | 각 인증 API의 입력, Stateless Refresh Token 서명·만료 검증, 활성 사용자 및 Cookie 규칙을 적용한다. |
| 3 | `POST` | `/api/v1/webhooks/portone` | 인증 제외 | 해당 없음 | 아니오 | PortOne webhook 서명·결제 상태 검증을 적용한다. |
| 4 | `GET` | `/actuator/health` | 인증 제외 | 해당 없음 | 아니오 | 배포 상태 확인 전용이며 구성 요소·예외·상세 정보를 반환하지 않는다. |
| 5 | `GET` | `/api/v1/regions`, `/api/v1/regions/*/home`, `/api/v1/contents`, `/api/v1/contents/*`, `/api/v1/contents/*/reviews`, `/api/v1/contents/*/sessions`, `/api/v1/sessions/*` | 공개 | 해당 없음 | 아니오 | 공개 여부와 도메인별 노출 상태를 조회 조건으로 적용한다. |
| 6 | `GET` | `/api/v1/regions/*/missions`, `/api/v1/missions/*` | 공개 | 해당 없음 | 예 | 헤더가 없으면 공개 데이터만, 유효한 Access Token이 있으면 본인 참여 요약을 더한다. 잘못된 Token은 익명으로 낮추지 않고 401이다. |
| 7 | `GET` | `/api/v1/platform-admin/me` | 역할 보호 | `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` | 아니오 | `app_user.status = ACTIVE`와 `account_kind = PRIVILEGED`를 확인한다. 응답 등급은 Access Token authority snapshot에서 결정하며, 현재 고권한 등급이나 고권한 배정의 활성 여부를 DB에서 authority 판정 근거로 다시 사용하지 않는다. |
| 8 | `GET` | `/api/v1/platform-admin/admin-accounts` | 역할 보호 | `ROLE_SUPER_ADMIN` | 아니오 | `app_user.status = ACTIVE`와 `account_kind = PRIVILEGED`를 확인한다. 호출자의 `SUPER_ADMIN` 등급과 고권한 배정의 현재 활성 여부는 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 9 | `POST` | `/api/v1/platform-admin/admin-accounts`, `/api/v1/platform-admin/admin-accounts/*/deactivate` | 역할 보호 | `ROLE_SUPER_ADMIN` | 아니오 | `app_user.status = ACTIVE`, `PRIVILEGED` 계정, 대상 계정·배정 상태, 자기·마지막 슈퍼관리자 보호와 감사 업무 규칙을 확인한다. 호출자의 `SUPER_ADMIN` 등급과 고권한 배정의 현재 활성 여부는 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 10 | `GET`, `POST`, `PATCH` | `/api/v1/platform-admin/**` | 역할 보호 | `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` | 아니오 | `app_user.status = ACTIVE`, `PRIVILEGED` 계정, 대상·상태·감사 규칙을 확인한다. 호출자의 고권한 등급과 고권한 배정의 현재 활성 여부는 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. 7·8·9번 행이 먼저 적용된다. |
| 11 | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `/api/v1/region-admin/**` | 역할 보호 | `ROLE_REGION_ADMIN` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정과 인증 주체의 현재 담당 지역 관계, 대상 지역의 일치를 확인한다. 현재 담당 지역 범위를 산정할 때 활성 지역 관리자 배정을 조회할 수 있으며, 배정 철회·지역 변경으로 범위가 없거나 달라지면 즉시 거부한다. |
| 12 | `GET` | `/region-admin/qr-exceptions`, `/region-admin/qr-exceptions/*` | 역할 보호 | `ROLE_REGION_ADMIN` | 아니오 | 11번과 같은 지역 관리자 DB 최종 검증을 적용한다. 기존 별칭 경로도 같은 권한으로 보호한다. |
| 13 | `POST` | `/api/v1/operator/operator-requests` | 인증 전용 | 해당 없음 | 아니오 | 활성 회원, 이전 `REJECTED` 신청과 현재 역할·신청 상태를 확인한다. 이 경로는 운영자 prefix 예외다. |
| 14 | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `/api/v1/operator/**` | 역할 보호 | `ROLE_OPERATOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정, 현재 담당 지역 관계, 콘텐츠 소유권·업무 상태를 확인한다. 현재 담당 지역 범위를 산정할 때 활성 운영자 배정을 조회할 수 있으며, 배정 철회·지역 변경으로 범위가 없거나 달라지면 즉시 거부한다. 13번 행이 먼저 적용된다. |
| 15 | `GET`, `POST` | `/operator/check-ins`, `/operator/check-ins/manual`, `/operator/contents/*` | 역할 보호 | `ROLE_OPERATOR` | 아니오 | 14번과 같은 운영자 DB 최종 검증을 적용한다. 기존 별칭 경로도 같은 권한으로 보호한다. |
| 16 | `POST`, `PATCH`, `DELETE` | `/api/v1/visits/*/reviews`, `/api/v1/reviews/*` | 역할 보호 | `ROLE_VISITOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정, 본인 방문·후기 소유권과 후기 상태를 확인한다. 호출자의 현재 `VISITOR` 배정은 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 17 | `POST` | `/api/v1/missions/*/participations`, `/api/v1/me/mission-participations/*/rewards/claim` | 역할 보호 | `ROLE_VISITOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정, 본인 참여·미션·보상·쿠폰 상태를 확인한다. 호출자의 현재 `VISITOR` 배정은 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 18 | `GET` | `/api/v1/me/mission-participations`, `/api/v1/me/mission-participations/*` | 역할 보호 | `ROLE_VISITOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정과 본인 참여 소유권을 확인한다. 호출자의 현재 `VISITOR` 배정은 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 19 | `DELETE` | `/api/v1/auth/delete` | 인증 전용 | 해당 없음 | 아니오 | 활성 회원과 탈퇴 차단 관계를 확인하고 성공 시 Refresh Cookie를 만료한다. |
| 20 | `POST` | `/api/v1/reservations`, `/api/v1/reservation-holds/*/confirm`, `/api/v1/coupon-policies/*/coupons` | 인증 전용 | 해당 없음 | 아니오 | 활성 회원, 본인 홀드·발급 근거·정원·쿠폰 상태를 확인한다. |
| 21 | `GET`, `POST` | `/api/v1/me/**` | 인증 전용 | 해당 없음 | 아니오 | 각 API 명세에 정한 활성 회원 또는 활성 방문자 상태와 본인 예약·결제·환불·쿠폰·스탬프북·홀드 소유권을 확인한다. 활성 방문자 조건은 `app_user.status = ACTIVE`, `ACTIVE VISITOR`, `account_kind = ORDINARY`를 함께 확인한다. 17·18번 행이 먼저 적용된다. |
| 22 | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `/actuator/**` | 인증 전용 | 해당 없음 | 아니오 | 4번 `health` 예외 외 Actuator 엔드포인트는 Access Token 인증을 요구한다. |
| 23 | 모든 method | 그 밖의 애플리케이션 경로 | 인증 전용 | 해당 없음 | 아니오 | 새 역할 보호 API는 구현 전에 이 행렬에 더 구체적인 method·pattern·authority 행을 추가한다. |

인증되지 않았거나 Access Token이 무효하면 역할 보호·인증 전용 경로는 `401 UNAUTHENTICATED`다. 유효한 Token의
authority가 해당 행에 없으면 Spring Security가 `403 FORBIDDEN`을 반환한다. 이후 DB 최종 검증에서 권한·상태·소유권이
부족해도 같은 `403 FORBIDDEN`을 반환할 수 있다.

## 인가 표기 규칙

각 도메인 API는 이 문서의 권한 행렬을 복사하지 않고, 다음 DB 최종 검증을 해당 API 계약에 구체적으로 명시한다.

| 항목 | 작성 기준 |
| --- | --- |
| 전역 authority | 이 권한 행렬의 최소 authority 또는 인증 전용·공개 여부를 참조한다. |
| 지역 경계 | 요청 대상과 인증 주체의 현재 담당 지역을 DB에서 비교하는 조건을 적는다. |
| 소유권 | 운영자와 콘텐츠·회차·예약, 회원과 예약·후기·쿠폰의 현재 연결 검증 조건을 적는다. |
| 활성·업무 상태 | 회원 계정 활성 상태와 리소스 상태 전이 조건을 DB 최종 검증으로 적는다. 지역 경계를 산정하는 경우에만 현재 담당 지역 배정 관계를 함께 적는다. 권한 행렬의 전역 authority·고권한 등급을 같은 배정 상태로 다시 판정하지 않는다. |
| 실패 결과 | 무효 Token은 401, authority 또는 DB 최종 인가 부족은 403이며 대상 부재와 상태 충돌은 도메인 오류 코드로 구분한다. |

새 공개·선택 인증·역할 보호 API를 추가하거나 제거할 때는 이 권한 행렬과 해당 도메인 API 명세를 같은 Docs 변경에서
갱신한다. OAuth/OIDC와 외부 권한 원천은 현재 범위에 포함하지 않는다.
