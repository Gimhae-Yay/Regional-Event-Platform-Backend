# 지역·콘텐츠 카탈로그 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-02`, `FR-04`, `FR-06`, `AUTH-01`, `CON-01`, `CON-02`, `CON-03`, `CON-04`, `SES-01`, `SES-02`, `RSV-02`, `RSV-06` |
| 소유 도메인 | 지역·콘텐츠 카탈로그 |
| 기준 문서 | [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [인증·프로필](../../../p0/auth-profile.md), [ERD](../../../erd.md), [기술 스택](../../../local-stamp-platform-tech-stack.md), [ADR-0016](../../../adr/0016-use-private-s3-presigned-urls-and-immediate-image-deletion.md), [ADR-0029](../../../adr/0029-use-version-validated-cache-aside-for-public-content.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 지역·콘텐츠 카탈로그 도메인의 공개 지역 탐색, 지역 홈, 담당 지역 승인 대기 콘텐츠 조회와 콘텐츠 회차
조회·취소 요구사항을 HTTP API 계약으로 구체화한다.
요청·응답의 공통 형식, 인증, 페이지네이션과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 해당 API에만 적용되는 값과 규칙만 작성한다.

공개 회차 조회는 `PUBLISHED` 콘텐츠의 `SCHEDULED` 회차만 노출한다. `PENDING` 콘텐츠는 심사 제출이 완료된
상태이므로 회차 생성·수정 API를 별도로 제공하지 않으며, 콘텐츠 생성·승인 요청 유스케이스에서 유효 회차를 함께
완성한다. 소유 운영자의 회차 취소는 `SCHEDULED → CANCELLED` 전이와 활성 홀드·미체크인 확정 예약의 종결, 성공
감사 기록을 하나의 트랜잭션으로 처리한다. 운영자 API는 `OPERATOR` 역할, 담당 지역 일치와 콘텐츠 소유 관계를 함께
검증한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-02` | `GET /regions` | `region` |
| `FR-02` | `GET /regions/{regionId}/home` | `region`, `content`, `content_session`, `content_representative_image`, `image_object` |
| `CON-03` | `GET /regions/{regionId}/home` | `content.status`, `content.publish_at`, `content_log` |
| `CON-04` | `GET /regions/{regionId}/home` | `content.status`, `content_session.status` |
| `SES-01` | `GET /regions/{regionId}/home` | `content_session.status`, `content_session.starts_at`, `content_session.ends_at` |
| `SES-02` | `GET /regions/{regionId}/home` | `content.status`, `content_session.status`, `content_session.remaining_capacity` |
| `FR-04` | `GET /region-admin/contents?status=PENDING` | `content`, `content_log`, `app_user` |
| `AUTH-01` | `GET /region-admin/contents?status=PENDING` | `content.region_id`, `user_role_assignment.region_id` |
| `CON-01` | `GET /region-admin/contents?status=PENDING` | `content.status`, `content.deleted_at` |
| `FR-02`, `SES-01`, `SES-02` | `GET /contents/{contentId}/sessions` | `content`, `content_session` |
| `FR-02`, `RSV-02`, `SES-01`, `SES-02` | `GET /sessions/{sessionId}` | `content`, `content_session` |
| `FR-06`, `AUTH-01`, `RSV-06` | `POST /operator/sessions/{sessionId}/cancel` | `content`, `content_session`, `capacity_hold`, `reservation`, `audit_event`, `audit_event_actor_link` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시각·식별자 표현 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며 시각과 식별자는 공통 규칙을 따른다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 공개 API 여부, 지역 관리자 역할과 담당 지역 조건 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | P0의 지역·콘텐츠·회차 목록에는 페이지네이션을 적용하지 않는다. 지역 홈의 진행·임박 목록은 각각 최대 10건이다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 공개 지역 목록 조회 | `GET /regions` | [list-public-regions.md](list-public-regions.md) |
| 지역 홈·진행/임박 콘텐츠 조회 | `GET /regions/{regionId}/home` | [get-region-home.md](get-region-home.md) |
| 담당 지역 승인 대기 목록 조회 | `GET /region-admin/contents?status=PENDING` | [list-pending-contents.md](list-pending-contents.md) |
| 공개 콘텐츠 회차 목록 조회 | `GET /contents/{contentId}/sessions` | [list-public-content-sessions.md](list-public-content-sessions.md) |
| 가격·실시간 잔여 정원·예약 가능 여부 조회 | `GET /sessions/{sessionId}` | [get-session-reservation-info.md](get-session-reservation-info.md) |
| 소유 운영자의 회차 취소 | `POST /operator/sessions/{sessionId}/cancel` | [session-cancel.md](session-cancel.md) |
