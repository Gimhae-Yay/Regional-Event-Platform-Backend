# 지역 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02`, `ADM-05` |
| 소유 도메인 | 지역 |
| 기준 문서 | [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [ERD](../../../erd.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 전체관리자가 지역을 생성하고 지역 운영 상태를 변경하며 전체 지역을 조회하는 HTTP API 계약을 정의한다.
요청·응답의 공통 형식, 인증, 오류 구조는 `common/` 문서를 단일 출처로 삼는다.

P1 지역 API는 전체관리자 전용 경로인 `/platform-admin` prefix를 사용한다. P0 공개 지역 목록인 `GET /regions`와
달리 비공개 지역을 포함한 운영 데이터를 반환하고, 지역 생성·상태 변경에는 감사 이력을 남긴다.

현재 ERD에는 `region.region_code`, `region.name`, `region.is_public`이 확정되어 있다. `ADM-02`의 지역 운영 상태는
P1 구현 전에 상태 종류, 전이 규칙, 공개 콘텐츠·예약·혜택 영향과 저장 컬럼을 ADR·ERD로 확정해야 한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `P1-FR-09`, `ADM-02`, `ADM-05` | `POST /platform-admin/regions` | `region`, `audit_event`, `audit_event_actor_link` |
| `P1-FR-09`, `ADM-02` | `GET /platform-admin/regions` | `region`, `user_role_assignment` |
| `P1-FR-09`, `ADM-02`, `ADM-05` | `PATCH /platform-admin/regions/{regionId}/status` | `region`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 활성 `PLATFORM_ADMIN` 역할과 전체관리자 계정 상태 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P1 초기 지역 목록에는 페이지네이션을 적용하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 지역 생성 | `POST /platform-admin/regions` | [create-region.md](create-region.md) |
| 지역 운영 상태 변경 | `PATCH /platform-admin/regions/{regionId}/status` | [update-region-status.md](update-region-status.md) |
| 전체 지역 조회 | `GET /platform-admin/regions` | [list-regions.md](list-regions.md) |
