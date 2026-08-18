# 인증·인가

## 인증 전달 방식

| 항목 | 계약 |
| --- | --- |
| 인증 헤더 | `Authorization: Bearer <accessToken>` |
| Access Token 성공 응답 | 로그인·토큰 갱신 성공 응답은 JSON 본문의 `data.accessToken`에 Access Token을 포함하고 `Authorization` 응답 헤더를 포함하지 않는다. 보호 업무 API 요청은 기존과 같이 `Authorization: Bearer <accessToken>` 헤더를 사용한다. ([ADR-0105](../../adr/0105-deliver-access-token-in-json-response-body.md)) |
| Refresh Token 전달 | 로그인 성공 응답은 `Set-Cookie: refreshToken=<refreshToken>; Max-Age=1209600; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`를 포함한다. 토큰 갱신 성공 응답의 `Max-Age`는 최초 로그인부터 14일인 계열 절대 만료까지 남은 전체 초다. `Domain`은 생략해 호스트 전용 쿠키로 유지하며 Refresh Token은 JSON·`Authorization` 헤더에 넣지 않는다. |
| 갱신·로그아웃 순서 | 브라우저 클라이언트는 갱신과 로그아웃을 하나의 인증 상태 전이로 직렬화한다. 로그아웃 전 진행 중인 갱신이 끝나면 최신 Cookie로 로그아웃을 요청하며, 로그아웃이 끝날 때까지 새 갱신을 시작하지 않는다. |
| 토큰 만료·무효 | `401 Unauthorized`, `UNAUTHENTICATED` |

Refresh Token은 `Path=/api/v1/auth` 범위의 인증 API에서만 수신하며 보호 업무 API의 인증 수단으로 사용할 수 없다.
현재 MVP는 단일 신뢰 사이트 브라우저 클라이언트만 지원하므로 CORS 허용 구성을 두지 않으며, Spring Security의 CSRF
검사는 적용하지 않는다. 이는 보호 업무 API가 Bearer Access Token으로만 인증되고 Refresh Token이 호스트 전용
`SameSite=Strict` 쿠키로 위 경로에만 전송된다는 조건에 한정한다. 교차 사이트 또는 신뢰할 수 없는 같은 사이트 하위
출처를 지원하려면 CSRF 방어·허용 Origin 정책을 ADR과 인증 API 명세에 먼저 확정한다.

## 전역 authority snapshot 계약

[ADR-0108](../../adr/0108-use-global-authority-snapshot-for-first-stage-rbac.md)에 따라 Access Token은 전역 역할만
`authorities` claim으로 가진다. 이 값은 요청자가 제출하거나 수정할 수 없고 로그인·토큰 갱신 시 서버가 현재 DB의
활성 권한 원천에서 만든 snapshot이다.

| 항목 | 계약 |
| --- | --- |
| claim 이름 | `authorities` |
| 형식 | 중복 없는 JSON 문자열 배열. 빈 배열 `[]`은 유효하다. |
| 허용 authority | `ROLE_VISITOR`, `ROLE_OPERATOR`, `ROLE_REGION_ADMIN`, `ROLE_PLATFORM_ADMIN`, `ROLE_SUPER_ADMIN` |
| 발급 원천 | `ORDINARY` 계정은 활성 일반 역할(`VISITOR`·`OPERATOR`·`REGION_ADMIN`)의 합집합, `PRIVILEGED` 계정은 활성 고권한 등급(`SUPER_ADMIN` 또는 `PLATFORM_ADMIN`) 하나다. |
| 상충 원천 | 계정 분류와 맞지 않는 활성 배정 또는 일반·고권한 활성 배정의 동시 존재는 어떤 authority도 합치지 않고 빈 배열로 fail-closed 처리한다. |
| `SUPER_ADMIN` | `ROLE_PLATFORM_ADMIN`을 중복 보유하지 않는다. 공통 전체관리자 API는 권한 행렬에서 두 authority를 함께 허용한다. |
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
| 2 | `POST` | `/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout` | 인증 제외 | 해당 없음 | 아니오 | 각 인증 API의 입력·Refresh Token·회전·폐기 규칙을 적용한다. |
| 3 | `POST` | `/api/v1/webhooks/portone` | 인증 제외 | 해당 없음 | 아니오 | PortOne webhook 서명·결제 상태 검증을 적용한다. |
| 4 | `GET` | `/actuator/health` | 인증 제외 | 해당 없음 | 아니오 | 배포 상태 확인 전용이며 구성 요소·예외·상세 정보를 반환하지 않는다. |
| 5 | `GET` | `/api/v1/regions`, `/api/v1/regions/*/home`, `/api/v1/contents`, `/api/v1/contents/*`, `/api/v1/contents/*/reviews`, `/api/v1/contents/*/sessions`, `/api/v1/sessions/*` | 공개 | 해당 없음 | 아니오 | 공개 여부와 도메인별 노출 상태를 조회 조건으로 적용한다. |
| 6 | `GET` | `/api/v1/regions/*/missions`, `/api/v1/missions/*` | 공개 | 해당 없음 | 예 | 헤더가 없으면 공개 데이터만, 유효한 Access Token이 있으면 본인 참여 요약을 더한다. 잘못된 Token은 익명으로 낮추지 않고 401이다. |
| 7 | `POST` | `/api/v1/platform-admin/admin-accounts`, `/api/v1/platform-admin/admin-accounts/*/deactivate` | 역할 보호 | `ROLE_SUPER_ADMIN` | 아니오 | `app_user.status = ACTIVE`, `PRIVILEGED` 계정, 대상 계정·배정 상태, 자기·마지막 슈퍼관리자 보호와 감사 업무 규칙을 확인한다. 호출자의 `SUPER_ADMIN` 등급과 고권한 배정의 현재 활성 여부는 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 8 | `GET`, `POST`, `PATCH` | `/api/v1/platform-admin/**` | 역할 보호 | `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` | 아니오 | `app_user.status = ACTIVE`, `PRIVILEGED` 계정, 대상·상태·감사 규칙을 확인한다. 호출자의 고권한 등급과 고권한 배정의 현재 활성 여부는 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. 7번 행이 먼저 적용된다. |
| 9 | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `/api/v1/region-admin/**` | 역할 보호 | `ROLE_REGION_ADMIN` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정과 인증 주체의 현재 담당 지역 관계, 대상 지역의 일치를 확인한다. 현재 담당 지역 범위를 산정할 때 활성 지역 관리자 배정을 조회할 수 있으며, 배정 철회·지역 변경으로 범위가 없거나 달라지면 즉시 거부한다. |
| 10 | `GET` | `/region-admin/qr-exceptions`, `/region-admin/qr-exceptions/*` | 역할 보호 | `ROLE_REGION_ADMIN` | 아니오 | 9번과 같은 지역 관리자 DB 최종 검증을 적용한다. 기존 별칭 경로도 같은 권한으로 보호한다. |
| 11 | `POST` | `/api/v1/operator/operator-requests` | 인증 전용 | 해당 없음 | 아니오 | 활성 회원, 이전 `REJECTED` 신청과 현재 역할·신청 상태를 확인한다. 이 경로는 운영자 prefix 예외다. |
| 12 | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `/api/v1/operator/**` | 역할 보호 | `ROLE_OPERATOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정, 현재 담당 지역 관계, 콘텐츠 소유권·업무 상태를 확인한다. 현재 담당 지역 범위를 산정할 때 활성 운영자 배정을 조회할 수 있으며, 배정 철회·지역 변경으로 범위가 없거나 달라지면 즉시 거부한다. 11번 행이 먼저 적용된다. |
| 13 | `GET`, `POST` | `/operator/check-ins`, `/operator/check-ins/manual`, `/operator/contents/*` | 역할 보호 | `ROLE_OPERATOR` | 아니오 | 12번과 같은 운영자 DB 최종 검증을 적용한다. 기존 별칭 경로도 같은 권한으로 보호한다. |
| 14 | `POST`, `PATCH`, `DELETE` | `/api/v1/visits/*/reviews`, `/api/v1/reviews/*` | 역할 보호 | `ROLE_VISITOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정, 본인 방문·후기 소유권과 후기 상태를 확인한다. 호출자의 현재 `VISITOR` 배정은 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 15 | `POST` | `/api/v1/missions/*/participations`, `/api/v1/me/mission-participations/*/rewards/claim` | 역할 보호 | `ROLE_VISITOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정, 본인 참여·미션·보상·쿠폰 상태를 확인한다. 호출자의 현재 `VISITOR` 배정은 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 16 | `GET` | `/api/v1/me/mission-participations`, `/api/v1/me/mission-participations/*` | 역할 보호 | `ROLE_VISITOR` | 아니오 | `app_user.status = ACTIVE`, `ORDINARY` 계정과 본인 참여 소유권을 확인한다. 호출자의 현재 `VISITOR` 배정은 이 행의 claim authority 판정을 다시 수행하는 근거로 사용하지 않는다. |
| 17 | `DELETE` | `/api/v1/auth/delete` | 인증 전용 | 해당 없음 | 아니오 | 활성 회원, 탈퇴 차단 관계와 Refresh Token 계열 폐기를 확인한다. |
| 18 | `POST` | `/api/v1/reservations`, `/api/v1/reservation-holds/*/confirm`, `/api/v1/coupon-policies/*/coupons` | 인증 전용 | 해당 없음 | 아니오 | 활성 회원, 본인 홀드·발급 근거·정원·쿠폰 상태를 확인한다. |
| 19 | `GET`, `POST` | `/api/v1/me/**` | 인증 전용 | 해당 없음 | 아니오 | 각 API 명세에 정한 활성 회원 또는 활성 방문자 상태와 본인 예약·결제·환불·쿠폰·스탬프북·홀드 소유권을 확인한다. 활성 방문자 조건은 `app_user.status = ACTIVE`, `ACTIVE VISITOR`, `account_kind = ORDINARY`를 함께 확인한다. 15·16번 행이 먼저 적용된다. |
| 20 | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `/actuator/**` | 인증 전용 | 해당 없음 | 아니오 | 4번 `health` 예외 외 Actuator 엔드포인트는 Access Token 인증을 요구한다. |
| 21 | 모든 method | 그 밖의 애플리케이션 경로 | 인증 전용 | 해당 없음 | 아니오 | 새 역할 보호 API는 구현 전에 이 행렬에 더 구체적인 method·pattern·authority 행을 추가한다. |

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
