# 인증·프로필 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | [FR-01 인증·역할·지역 권한](../../../p0/auth-profile.md#fr-01-인증역할지역-권한), [FR-09 운영자 승인·정보 마스킹](../../../p0/auth-profile.md#fr-09-운영자-승인정보-마스킹), [PRV-01](../../../p0/auth-profile.md#prv-01), [PRV-02](../../../p0/auth-profile.md#prv-02) |
| 소유 도메인 | 인증·프로필 |
| 기준 문서 | [인증·프로필](../../../p0/auth-profile.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 인증·프로필 도메인의 회원가입, 로그인, CSRF Token bootstrap, 토큰 갱신, 로그아웃, 회원탈퇴, 내 역할 조회와
운영자 신청·심사 HTTP API 계약을 관리한다. 공개 API는 회원가입·로그인·CSRF Token bootstrap·토큰 갱신·로그아웃이고,
회원탈퇴·내 역할 조회·운영자 신청과 지역 관리자 심사 API는 Access Token을 요구한다. 요청·응답 공통 형식과 인증 전달
방식은 `common/` 문서를 단일 출처로 삼으며, 각 API 명세에는 해당 API의 입력·상태 전이·응답·오류만 작성한다.

회원가입은 클라이언트가 `VISITOR` 또는 `OPERATOR` 역할을 선택한다. `VISITOR`는 가입 즉시 역할을 부여하고,
`OPERATOR`는 요청 지역과 사업자 정보를 포함한 `PENDING` 운영자 신청만 생성한다. 승인 전에는 운영자 역할과
담당 지역을 부여하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| FR-01 | `POST /api/v1/auth/signup` | `app_user`, `user_role_assignment`, `operator_application` |
| FR-01 | `POST /api/v1/auth/login` | `app_user`, `user_role_assignment` |
| FR-01 | `GET /api/v1/auth/csrf` | 브라우저 `__Host-csrf` Cookie |
| FR-01 | `POST /api/v1/auth/refresh` | `app_user` |
| FR-01 | `POST /api/v1/auth/logout` | 브라우저 `refreshToken` 쿠키 |
| FR-01, PRV-01, PRV-02 | `DELETE /api/v1/auth/delete` | `app_user`, 역할·소유 관계, `operator_application` |
| FR-01, AUTH-01 | `GET /api/v1/me` | `app_user`, `user_role_assignment`, `region` |
| FR-09, AUTH-02 | `POST /api/v1/operator/operator-requests` | `operator_application`, `region` |
| FR-09, AUTH-02 | `GET /api/v1/region-admin/operator-requests?status=PENDING` | `operator_application`, `user_role_assignment`, `region` |
| FR-09, AUTH-02 | `GET /api/v1/region-admin/operator-requests/{requestId}` | `operator_application`, `user_role_assignment`, `region` |
| FR-09, AUTH-02 | `POST /api/v1/region-admin/operator-requests/{requestId}/approve` | `app_user`, `user_role_assignment`, `operator_application` |
| FR-09, AUTH-02 | `POST /api/v1/region-admin/operator-requests/{requestId}/reject` | `operator_application`, `user_role_assignment` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 회원가입·로그인·토큰 갱신·로그아웃은 인증 제외 API다. 회원탈퇴·내 역할 조회·운영자 신청에는 Access Token과 활성 회원 상태가 필요하며, 지역 관리자 심사 API에는 추가로 `REGION_ADMIN` 역할과 담당 지역 일치가 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드를 명시한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 운영자 신청 대기 목록은 P0에서 페이지네이션을 적용하지 않는다. 나머지 API는 단건 조회 또는 명령이다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 역할 선택 회원가입 | `POST /api/v1/auth/signup` | [signup.md](signup.md) |
| Access·Stateless Refresh Token 발급 로그인 | `POST /api/v1/auth/login` | [login.md](login.md) |
| 교차 출처 인증용 CSRF Token bootstrap | `GET /api/v1/auth/csrf` | [인증·인가 공통 계약](../../common/authentication.md#교차-출처-corscookiecsrf-계약) |
| Access Token 재발급 | `POST /api/v1/auth/refresh` | [refresh.md](refresh.md) |
| Refresh Token 쿠키 만료 로그아웃 | `POST /api/v1/auth/logout` | [logout.md](logout.md) |
| 본인 회원탈퇴 | `DELETE /api/v1/auth/delete` | [withdrawal.md](withdrawal.md) |
| 내 역할·담당 지역 조회 | `GET /api/v1/me` | [me.md](me.md) |
| 운영자 권한 신청 | `POST /api/v1/operator/operator-requests` | [operator-request.md](operator-request.md) |
| 운영자 신청 대기 목록 조회 | `GET /api/v1/region-admin/operator-requests?status=PENDING` | [pending-operator-requests.md](pending-operator-requests.md) |
| 운영자 신청 상세 조회 | `GET /api/v1/region-admin/operator-requests/{requestId}` | [operator-request-detail.md](operator-request-detail.md) |
| 운영자 신청 승인 | `POST /api/v1/region-admin/operator-requests/{requestId}/approve` | [operator-request-approve.md](operator-request-approve.md) |
| 운영자 신청 반려 | `POST /api/v1/region-admin/operator-requests/{requestId}/reject` | [operator-request-reject.md](operator-request-reject.md) |
