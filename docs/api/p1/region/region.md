# 지역 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-09`, `ADM-02`, `ADM-05` |
| 소유 도메인 | 지역 |
| 기준 문서 | [전체관리자](../../../p1/platform-admin.md), [P1 명세](../../../p1-spec.md), [P0 ERD](../../../erd.md), [P1 ERD](../../../p1-erd.md), [ADR-0064](../../../adr/0064-separate-privileged-account-class-from-ordinary-roles.md), [ADR-0065](../../../adr/0065-use-is-public-for-region-availability-and-history-roles.md), [ADR-0077](../../../adr/0077-normalize-region-code-to-uppercase.md), [ADR-0078](../../../adr/0078-treat-repeated-region-visibility-as-no-op-success.md), [ADR-0082](../../../adr/0082-store-evidence-reference-in-region-visibility-failure-audits.md), [ADR-0085](../../../adr/0085-keep-region-evidence-reference-as-free-string.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 전체관리자가 지역을 생성하고 지역 공개 여부를 변경하며 전체 지역을 조회하는 HTTP API 계약을 정의한다.
요청·응답의 공통 형식, 인증, 오류 구조는 `common/` 문서를 단일 출처로 삼는다.

P1 지역 API는 전체관리자 전용 경로인 `/platform-admin` prefix를 사용한다. P0 공개 지역 목록인 `GET /regions`와
달리 비공개 지역을 포함한 운영 데이터를 반환하고, 실제 지역 생성·공개 여부 변경에는 감사 이력을 남긴다.

P1은 별도 지역 운영 상태를 추가하지 않는다. `region.is_public = false`는 비공개·준비,
`region.is_public = true`는 공개·운영을 뜻하며 공개 `GET /regions`의 노출 여부와 같은 기준을 사용한다.

### P0·P1 ERD 정합성

| 계약 | 확정 모델 | 적용 규칙 |
| --- | --- | --- |
| 지역 생성·조회 | P0 `region`과 `region(region_code)` 유일 제약을 재사용한다. `region_code`는 최대 50자, `name`은 최대 100자다. | 새 지역은 `is_public = false`로 생성한다. `region_code`는 앞뒤 공백 제거와 내부 공백 거부 뒤 [ADR-0077](../../../adr/0077-normalize-region-code-to-uppercase.md)의 대문자 정규형으로 저장·응답한다. |
| 지역 공개 여부 | 별도 상태 컬럼 없이 P0 `region.is_public`을 재사용한다. | `false → true`는 공개·운영 전환이고, `true → false`는 비삭제 콘텐츠가 없는 지역의 운영 전 노출 취소에만 사용한다. 콘텐츠 운영 이력이 있는 지역의 운영 종료·비공개는 P1에서 제공하지 않는다. 현재 값과 같은 목표 상태의 요청은 `200 OK` 무변경 성공으로 처리하고 감사 이벤트를 만들지 않는다. |
| 전체관리자 권한 | `app_user.account_kind = PRIVILEGED`와 별도 `platform_admin_assignment`를 사용한다. | `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot으로 세 API를 1차 인가하고, DB에서는 활성 `PRIVILEGED` 계정과 대상·업무 상태를 확인한다. 일반 `user_role_assignment`와 겸임하지 않는다. |
| 변경 사유·증빙 | `audit_event.reason_code`와 P1의 nullable `evidence_reference`를 사용한다. | 지역 변경 API는 허용 사유 코드와 증빙 참조를 모두 필수로 받는다. `evidenceReference`는 앞뒤 공백 제거 후 1~500자 자유 문자열이며 서버는 출처·형식·민감정보를 판별하지 않는다. 호출자는 개인정보·토큰·비밀값을 포함하지 않아야 한다. 실제 상태 전이에 성공하면 요청 사유와 증빙을 성공 감사에 저장한다. 비삭제 콘텐츠 조건 실패에는 `previous_state = true`, `next_state = NULL`, 서버 실패 코드와 요청 증빙을 실패 감사에 저장하고, 동일 상태 무변경 성공에서는 저장하지 않는다. 공통 감사 테이블의 nullable 제약을 지역 변경 요청의 선택 입력으로 해석하지 않는다. |
| 특권 감사 | P0 `audit_event`와 `audit_event_actor_link`를 확장해 재사용한다. | 실제 지역 변경과 성공 감사는 같은 트랜잭션으로 처리하고 활성 actor만 연결한다. 동일 상태 무변경 성공은 감사 이벤트를 만들지 않는다. 상태 조건 실패 감사 저장이 실패해도 원래 409와 롤백 결과를 유지하고 비개인 구조화 로그·운영 알림으로 관찰한다. |

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `P1-FR-09`, `ADM-02`, `ADM-05` | `POST /platform-admin/regions` | `platform_admin_assignment`, `region`, `audit_event`, `audit_event_actor_link` |
| `P1-FR-09`, `ADM-02` | `GET /platform-admin/regions` | `platform_admin_assignment`, `region`, `user_role_assignment`, `app_user` |
| `P1-FR-09`, `ADM-02`, `ADM-05` | `PATCH /platform-admin/regions/{regionId}/status` | `platform_admin_assignment`, `region`, `content`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 사건 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | `ROLE_SUPER_ADMIN` 또는 `ROLE_PLATFORM_ADMIN` snapshot과 활성 `PRIVILEGED` 계정 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P1 초기 지역 목록에는 페이지네이션을 적용하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 지역 생성 | `POST /platform-admin/regions` | [create-region.md](create-region.md) |
| 지역 공개 여부 변경 | `PATCH /platform-admin/regions/{regionId}/status` | [update-region-status.md](update-region-status.md) |
| 전체 지역 조회 | `GET /platform-admin/regions` | [list-regions.md](list-regions.md) |
