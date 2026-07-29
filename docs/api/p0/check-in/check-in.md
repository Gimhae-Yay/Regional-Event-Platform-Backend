# 예약 QR·체크인 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P0 |
| 관련 요구사항 | `FR-07`, `FR-10`, `FR-11`, `AUTH-01`, `QR-03`, `QR-05`, `RSV-04` |
| 소유 도메인 | 예약 QR·체크인 |
| 기준 문서 | [예약 QR·체크인](../../../p0/check-in.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [ADR-0003](../../../adr/0003-use-persisted-idempotency-for-reservation-and-checkin.md), [ADR-0010](../../../adr/0010-issue-short-lived-qr-on-demand-and-separate-retry-idempotency.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 예약 QR·체크인 도메인의 HTTP API 계약을 구체화한다.
현재 작성 범위는 QR 실패 시 예약번호 보조 조회 결과를 이용해 소유 운영자가 실제 체크인을 완료하는 명령이다.
요청·응답의 공통 형식, 인증, 멱등성과 오류 구조는 `common/` 문서를 단일 출처로 삼는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| `FR-07` | `POST /operator/check-ins/manual` | `reservation`, `visit`, `content_session` |
| `FR-10` | `POST /operator/check-ins/manual` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `FR-11` | `POST /operator/check-ins/manual` | `audit_event`, `idempotency_record` |
| `AUTH-01` | `POST /operator/check-ins/manual` | `content.operator_id`, `content.region_id`, `reservation.region_id` |
| `QR-03` | `POST /operator/check-ins/manual` | `idempotency_record`, `visit` |
| `QR-05` | `POST /operator/check-ins/manual` | `reservation`, `content_session`, `visit`, `audit_event` |
| `RSV-04` | `POST /operator/check-ins/manual` | `reservation.status`, `visit` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1` |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 소유 운영자 역할, 콘텐츠 소유권과 담당 지역 조건 |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | 성공 상태, `data` 필드와 체크인·멱등 충돌 오류 코드 |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 적용하지 않음 |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 예약번호 보조 조회 후 체크인 | `POST /operator/check-ins/manual` | [manual-check-in-by-reservation-number.md](manual-check-in-by-reservation-number.md) |
