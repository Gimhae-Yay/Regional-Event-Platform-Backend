# 정원 홀드·무료 예약 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-04`, `FR-05`, `FR-06`, `FR-07`, `FR-10`, `FR-11`, `AUTH-01`, `AUTH-03`, `CON-04`, `CON-09`, `SES-01`, `SES-02`, `RSV-01`, `RSV-02`, `RSV-03`, `RSV-04`, `RSV-05`, `RSV-06`, `QR-03`, `QR-05` |
| 소유 도메인 | 예약 |
| 기준 문서 | [정원 홀드·무료 예약](../../../p0/reservation.md), [지역·콘텐츠 카탈로그](../../../p0/content-catalog.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 예약 도메인의 요구사항을 HTTP API 계약으로 구체화한다.
요청·응답의 공통 형식, 인증, 페이지네이션, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼으며,
이 문서에는 해당 API에만 적용되는 값과 규칙만 작성한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-05` | `POST /reservations` | `content_session`, `capacity_hold` |
| `RSV-01` | `POST /reservations` | `capacity_hold.status`, `capacity_hold.expires_at` |
| `RSV-02` | `POST /reservations` | `content_session.remaining_capacity`, `capacity_hold` |
| `FR-06` | `POST /reservation-holds/{holdId}/confirm` | `capacity_hold`, `reservation`, `idempotency_record` |
| `RSV-03` | `POST /reservation-holds/{holdId}/confirm` | `capacity_hold.status`, `reservation`, `idempotency_record` |
| `FR-11` | `POST /reservation-holds/{holdId}/confirm` | `audit_event`, `idempotency_record` |
| `FR-05` | `scheduler` | `capacity_hold.status`, `content_session.remaining_capacity`, `audit_event` |
| `RSV-01` | `scheduler` | `capacity_hold.status`, `capacity_hold.expires_at`, `capacity_hold.capacity_released_at` |
| `FR-06` | `scheduler` | `reservation.status`, `content_session.status`, `audit_event` |
| `RSV-05` | `scheduler` | `reservation.status`, `reservation.expired_at`, `content_session.status` |
| `SES-01` | `scheduler` | `content_session.status`, `content_session.completed_at` |
| `FR-11` | `scheduler` | `audit_event` |
| `FR-06` | `POST /me/reservations/{reservationId}/cancel` | `reservation`, `capacity_hold`, `content_session` |
| `RSV-04` | `POST /me/reservations/{reservationId}/cancel` | `reservation.status`, `reservation.capacity_released_at`, `content_session.remaining_capacity` |
| `FR-06` | `GET /me/reservations/{reservationId}` | `reservation`, `content_session` |
| `FR-07` | `GET /me/reservations/{reservationId}` | `reservation`, `visit`, `content_session` |
| `QR-03` | `GET /me/reservations/{reservationId}` | `reservation.status`, `visit.checked_at` |
| `FR-06` | `GET /me/reservations` | `reservation`, `content_session`, `content` |
| `FR-07` | `GET /me/reservations` | `reservation.status`, `visit.checked_at` |
| `FR-10` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `reservation`, `content_session`, `content`, `audit_event` |
| `AUTH-01` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `AUTH-03` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `app_user.name`, `app_user.phone` |
| `QR-05` | `GET /operator/reservations/search?reservationNo={reservationNo}` | `reservation`, `content_session`, `visit`, `audit_event` |
| `FR-10` | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | `reservation`, `capacity_hold`, `content_session`, `visit` |
| `AUTH-01` | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `AUTH-03` | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | `app_user.name`, `app_user.phone` |
| `FR-04` | `POST /region-admin/contents/{contentId}/end` | `content`, `content_session`, `content_log` |
| `CON-04` | `POST /region-admin/contents/{contentId}/end` | `content.status`, `content_log.status` |
| `CON-09` | `POST /region-admin/contents/{contentId}/end` | `content_log`, `audit_event` |
| `SES-01` | `POST /region-admin/contents/{contentId}/end` | `content_session.status` |
| `SES-02` | `POST /reservations`, `POST /region-admin/contents/{contentId}/end` | `content.status`, `capacity_hold`, `reservation`, `visit`, `review` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1` |
| 인증·인가 | [인증·인가](../../common/authentication.md) | API별 허용 역할, 지역 경계 조건 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 성공 상태, `data` 필드와 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 적용하지 않음 |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 예약 대기 및 정원 홀드 생성 | `POST /reservations` | [create-reservation-hold.md](create-reservation-hold.md) |
| 활성 홀드의 무료 예약 확정 | `POST /reservation-holds/{holdId}/confirm` | [confirm-reservation-hold.md](confirm-reservation-hold.md) |
| 예약·노출 종료 | `POST /region-admin/contents/{contentId}/end` | [end-content-reservations.md](end-content-reservations.md) |
| 홀드 만료·무효화와 정원 1회 복구 | `scheduler` | [expire-or-invalidate-holds.md](expire-or-invalidate-holds.md) |
| 노쇼 전환과 회차 완료 처리 | `scheduler` | [expire-no-shows-and-complete-session.md](expire-no-shows-and-complete-session.md) |
| 예약 취소 | `POST /me/reservations/{reservationId}/cancel` | [cancel-reservation.md](cancel-reservation.md) |
| 예약 상세 조회(예약·회차·체크인 상태) | `GET /me/reservations/{reservationId}` | [get-my-reservation.md](get-my-reservation.md) |
| 내 예약 목록 조회 | `GET /me/reservations` | [list-my-reservations.md](list-my-reservations.md) |
| QR 실패 시 예약번호 보조 조회 | `GET /operator/reservations/search?reservationNo={reservationNo}` | [search-reservation-by-number.md](search-reservation-by-number.md) |
| 회차별 예약자 목록 및 개인정보 마스킹 조회 | `GET /operator/contents/{contentId}/reservations?sessionId={sessionId}` | [list-session-reservations.md](list-session-reservations.md) |
