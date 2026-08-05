# 전체관리자 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-09](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `ADM-01` |
| 소유 도메인 | 전체관리자 |
| 기준 문서 | [전체관리자](../../../p1/platform-admin.md), [ERD](../../../erd.md), [ADR-0063](../../../adr/0063-use-global-admin-role-in-existing-user-role-assignment.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 전체관리자 도메인의 HTTP API 계약을 관리한다. 현재는 전역 권한을 가진 활성 사용자가 역할 관리 대상을
확인하기 위한 사용자 목록 조회만 제공한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-09, ADM-01 | `GET /api/v1/platform-admin/users` | `app_user`, `user_role_assignment`, `region` |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts` | `app_user`, `user_role_assignment`, `audit_event` |
| P1-FR-09, ADM-01, ADM-05 | `POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate` | `app_user`, Redis Refresh Token 계열, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL `/api/v1`과 사건 시각 UTC ISO 8601 표기를 사용한다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `PLATFORM_ADMIN` 역할이 필요하며 지역·소유권 조건은 적용하지 않는다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드를 명시한다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 사용자 목록 조회는 단순 목록이므로 적용하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 전체관리자의 사용자 목록 조회 | `GET /api/v1/platform-admin/users` | [list-users.md](list-users.md) |
| 전체관리자 계정 생성 | `POST /api/v1/platform-admin/admin-accounts` | [create-admin-account.md](create-admin-account.md) |
| 전체관리자 계정 비활성화 | `POST /api/v1/platform-admin/admin-accounts/{userId}/deactivate` | [deactivate-admin-account.md](deactivate-admin-account.md) |
