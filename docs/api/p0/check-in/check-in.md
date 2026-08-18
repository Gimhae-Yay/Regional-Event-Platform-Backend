# 예약 QR·체크인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-07`, `FR-10`, `FR-11`, `AUTH-01`, `QR-02`, `QR-03`, `QR-05`, `RSV-04` |
| 소유 도메인 | 예약 QR·체크인 |
| 기준 문서 | [예약 QR·체크인](../../../p0/check-in.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ADR-0003](../../../adr/0003-use-persisted-idempotency-for-reservation-and-checkin.md), [ADR-0010](../../../adr/0010-issue-short-lived-qr-on-demand-and-separate-retry-idempotency.md), [ADR-0101](../../../adr/0101-reject-new-qr-rescans-after-check-in.md), [ADR-0102](../../../adr/0102-preserve-qr-check-in-validation-precedence-for-rescan-conflict.md), [ADR-0103](../../../adr/0103-record-rejected-qr-rescans-without-visit-result-or-progress-trigger.md), [ADR-0040](../../../adr/0040-use-single-get-endpoint-for-my-reservation-qr.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 예약 QR·체크인 도메인의 HTTP API 계약을 구체화한다.
체크인 창 안의 단기 QR 발급·조회, 소유 운영자의 QR·예약번호 체크인, 담당 지역 관리자의 QR 예외 조회를 포함한다.
요청·응답의 공통 형식, 인증, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼는다.

`GET /me/reservations/{reservationId}/qr`은 내 예약 화면에서 QR을 조회하고, 성공 시 현재 시각을 기준으로
새 단기 토큰을 발급해 반환하는 유일한 계약이다. QR 토큰과 발급 이력은 저장하지 않으므로 이 조회는
예약·회차·정원·방문 상태를 변경하지 않는다. 브라우저와 중간 캐시는 `Cache-Control: no-store`로 응답을 저장하지 않는다.

체크인 요청 멱등성은 별도 HTTP API가 아니다. `POST /operator/check-ins`와
`POST /operator/check-ins/manual`의 필수 `Idempotency-Key` 헤더와 영속 멱등 기록으로 보장한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-07`, `QR-01` | `GET /me/reservations/{reservationId}/qr` | `reservation`, `content_session` |
| `FR-07`, `QR-02` | `POST /operator/check-ins` | `reservation`, `content_session`, `visit` |
| `FR-07`, `QR-03` | 체크인 명령의 `Idempotency-Key` | `idempotency_record`, `visit` |
| `FR-10`, `AUTH-01` | `POST /operator/check-ins`, `POST /operator/check-ins/manual` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `FR-11` | 체크인 명령과 QR 예외 조회 | `audit_event`, `idempotency_record` |
| `QR-05`, `AUTH-03` | `GET /region-admin/qr-exceptions`, `GET /region-admin/qr-exceptions/{exceptionId}` | `audit_event`, `reservation`, `app_user` |
| `QR-05` | `POST /operator/check-ins/manual` | `reservation`, `content_session`, `visit`, `audit_event` |
| `RSV-04` | 두 체크인 명령 | `reservation.status`, `visit` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1` |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 역할 보호 API의 권한 행렬과 API별 지역·소유권 조건을 따른다. 방문자 QR 조회의 현재 활성 방문자 조건은 개별 API 명세를 따른다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태, `data` 필드와 QR·체크인·멱등 충돌 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | QR 예외 목록에 API별 커서 계약 적용 |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| QR 실패·보조 처리 목록 조회 | `GET /region-admin/qr-exceptions` | [list-qr-exceptions.md](list-qr-exceptions.md) |
| QR 예외·마스킹 예약자 단건 조회 | `GET /region-admin/qr-exceptions/{exceptionId}` | [get-qr-exception.md](get-qr-exception.md) |
| 내 예약 단기 QR 조회·발급 | `GET /me/reservations/{reservationId}/qr` | [get-my-reservation-qr.md](get-my-reservation-qr.md) |
| 예약번호 보조 조회 후 체크인 | `POST /operator/check-ins/manual` | [manual-check-in-by-reservation-number.md](manual-check-in-by-reservation-number.md) |
| QR 검증·체크인과 방문 자동 생성 | `POST /operator/check-ins` | [check-in-by-qr.md](check-in-by-qr.md) |
| 체크인 요청 멱등성 | 별도 HTTP 경로 없음 | [check-in-idempotency.md](check-in-idempotency.md) |
