# 전체관리자 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01`, `ADM-03` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0086](../../../adr/0086-apply-separated-privileged-account-model-to-platform-admin-api.md), [ADR-0087](../../../adr/0087-bootstrap-and-inactivate-privileged-admin-accounts.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 활성 고권한 배정을 가진 전체관리자의 HTTP API 계약을 관리한다. 사용자 목록은 활성 일반 계정과 일반 역할만
반환한다. 고권한 계정 생성·비활성화는 활성 `SUPER_ADMIN`만 수행할 수 있다. 지역 관리자(`REGION_ADMIN`) 역할과
담당 지역의 임명·회수는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 수행한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01 | `GET /api/v1/platform-admin/users` | `app_user`, `user_role_assignment`, `region` |
| P1-FR-09, ADM-01 | `GET /api/v1/platform-admin/admin-accounts` | `app_user`, `platform_admin_assignment` |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts` | `app_user`, `platform_admin_assignment`, `audit_event` |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate` | `platform_admin_assignment`, `audit_event` |
| P1-FR-09, ADM-03, ADM-05 | `PATCH /api/v1/platform-admin/users/{userId}/role` | `user_role_assignment`, `app_user`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 사건 시각 UTC ISO 8601 표기를 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 목록 조회는 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`, 고권한 계정 변경은 활성 `SUPER_ADMIN`, 지역관리자 역할 변경은 활성 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN`이 필요하다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드를 명시한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 사용자 목록과 전체관리자 계정 목록 조회는 단순 목록이므로 적용하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 전체관리자의 사용자 목록 조회 | `GET /api/v1/platform-admin/users` | [list-users.md](list-users.md) |
| 전체관리자 계정 목록 조회 | `GET /api/v1/platform-admin/admin-accounts` | [list-admin-accounts.md](list-admin-accounts.md) |
| 전체관리자 계정 생성 | `POST /api/v1/platform-admin/admin-accounts` | [create-admin-account.md](create-admin-account.md) |
| 전체관리자 계정 비활성화 | `POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate` | [deactivate-admin-account.md](deactivate-admin-account.md) |
| 지역관리자 역할 부여·회수 | `PATCH /api/v1/platform-admin/users/{userId}/role` | [update-user-role.md](update-user-role.md) |
