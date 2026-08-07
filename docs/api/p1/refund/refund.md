# 환불 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-05`~`PAY-07`, `ADM-04` |
| 소유 도메인 | 환불 |
| 기준 문서 | [유료 결제·환불](../../../p1/payment-refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 방문자의 환불 상태 조회, 전체관리자의 수동 환불(전액 환불 요청)·환불 재시도와 환불 실패
(`FAILED`·`DISCREPANT`) 조회·수동 조치 HTTP 계약을 정의한다.

**결제는 이 문서의 범위가 아니다.** 결제 생성·서버 승인 확인·상태 조회, PortOne 웹훅 수신과 결제 불일치
조회·문제없음 종결은 결제 도메인 [결제 API](../payment/payment.md)가 소유한다. 담당 콘텐츠 운영자의 담당
예약 결제·환불 상태 조회도 이미 결제 도메인 문서([get-operator-reservation-payment.md](../payment/get-operator-reservation-payment.md))에 있으므로 이 문서에서 다시 정의하지 않는다.

환불은 결제당 최대 한 건이며 전액 환불만 지원한다(부분 환불 없음, `ADR-0070`). 방문자의 취소·환불 요청은
예약 도메인의 취소 API와 연계되며, 이 문서의 수동 환불은 전체관리자가 그 요청을 근거로 트리거하는
전액 환불 실행 계약이다. 방문자는 본인 환불 상태를 읽기 전용으로만 조회한다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-06 | `GET /api/v1/me/refunds` | `refund` |
| P1-FR-08, PAY-06 | `GET /api/v1/me/refunds/{refundId}` | `refund` |
| P1-FR-08, P1-FR-10, PAY-05, ADM-04 | `POST /api/v1/platform-admin/payments/{paymentId}/refund` | `payment`, `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history`, `payment_discrepancy`, `payment_discrepancy_action` |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/refund-failures` | `refund`, `payment` |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/refund-failures/{refundId}` | `refund`, `refund_attempt`, `payment` |
| P1-FR-10, ADM-04 | `POST /api/v1/platform-admin/refund-failures/{refundId}/manual-actions` | `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history` |
| P1-FR-08, PAY-07, ADM-04 | `POST /api/v1/platform-admin/refunds/{refundId}/retry` | `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며, 환불 관련 사건 시각은 UTC ISO 8601 형식이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 방문자 API(`/me/...`)는 활성 회원 본인 소유 조건, 전체관리자 API(`/platform-admin/...`)는 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 활성 배정을 검증한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 `data` 필드와 오류 코드를 확인한다. `REFUND_PAYMENT_CONFLICT`, `REFUND_STATE_CONFLICT`는 P1 구현 시 전역 `ErrorCode`에 추가한다. |
| 멱등성 | [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md) | 수동 환불은 `refund.payment_id` 유일성(결제당 최대 한 건)으로 자연 멱등하며 별도 `Idempotency-Key`를 받지 않는다. 이미 환불이 있으면 새로 만들지 않고 기존 상태를 반환한다. |
| 감사 이력 | [P1 ERD](../../../p1-erd.md), [ADR-0071](../../../adr/0071-deidentify-p1-benefit-data-on-withdrawal-and-extend-common-audit.md) | 환불 상태 전이는 대상·처리자·이전·이후 상태·사유·증빙 참조·시각과 서버가 부여한 `requestId`를 함께 감사한다. `requestId`는 응답에 노출하지 않는다. PortOne 원문, 결제 비밀값과 전체 결제수단 정보는 저장하지 않는다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 이 도메인의 모든 목록 API는 단순 목록이며 P0 관례에 따라 페이지네이션, 커서와 사용자 지정 정렬을 제공하지 않는다. |

### 쿠폰 복구 계약

수동 환불, 환불 재시도, 환불 실패 수동 조치와 1분 고정 지연 복구 작업 중 어느 경로에서든 `refund.status`가
최초로 `SUCCEEDED`가 되면 다음 계약을 같은 상태 반영 트랜잭션에 적용한다.

1. 환불에 연결된 예약이 `CANCELLED`이고 `reservation.cancelled_at < content_session.starts_at`인지 검증한다. 회차 시작 전 사용자·운영자 취소가 아니거나 확정 예약이 없는 결제 불일치 환불은 쿠폰을 복구하지 않는다.
2. 가격 스냅샷에 적용 쿠폰이 있으면 예약의 `hold_id`와 가격 스냅샷의 `hold_id`, 사용 이력의 `reservation_id`·`price_snapshot_id`·`coupon_id`가 각각 환불 대상 예약·스냅샷·적용 쿠폰과 일치하는지 검증한다. 연결된 `coupon_redemption`이 `CONFIRMED`, 쿠폰이 `USED`인 경우에만 복구하며, 적용 쿠폰이 없으면 쿠폰 처리 없이 환불만 확정한다.
3. `coupon_redemption`을 `REVERSED`로 전이하고 환불 식별자, 반전 사유와 반전 시각을 기록한다. 쿠폰은 원래 `expires_at`이 MySQL 기준 복구 시각보다 미래면 `AVAILABLE`, 그렇지 않으면 `EXPIRED`로 전이하고 `coupon_status_history`를 남긴다.
4. 같은 환불·취소 근거로 이미 `REVERSED`인 사용 이력은 새 반전 이력이나 쿠폰 상태 전이를 만들지 않는다. 환불 재요청·재시도·수동 확정·복구 작업의 중복 실행도 최초 복구 결과로 수렴한다.
5. 환불 `SUCCEEDED` 전이, 사용 이력 반전, 쿠폰 상태 전이·상태 이력과 `REFUND`, `COUPON` 감사 이벤트는 같은 `requestId`로 하나의 MySQL 트랜잭션에서 커밋한다.

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 내 환불 목록 조회 | `GET /me/refunds` | [list-my-refunds.md](list-my-refunds.md) |
| 내 환불 상세 조회 | `GET /me/refunds/{refundId}` | [get-my-refund.md](get-my-refund.md) |
| 수동 환불 | `POST /platform-admin/payments/{paymentId}/refund` | [create-refund.md](create-refund.md) |
| 환불 실패 목록 조회 | `GET /platform-admin/refund-failures` | [list-refund-failures.md](list-refund-failures.md) |
| 환불 실패 상세 조회 | `GET /platform-admin/refund-failures/{refundId}` | [get-refund-failure.md](get-refund-failure.md) |
| 환불 실패 수동 조치 | `POST /platform-admin/refund-failures/{refundId}/manual-actions` | [resolve-refund-failure.md](resolve-refund-failure.md) |
| 환불 재시도 | `POST /platform-admin/refunds/{refundId}/retry` | [retry-refund.md](retry-refund.md) |
