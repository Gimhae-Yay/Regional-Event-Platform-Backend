# 결제 API 명세서

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-07](../../../p1-spec.md#6-기능-요구사항과-소유-문서), [P1-FR-10](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-01`~`PAY-04`, `ADM-04` |
| 소유 도메인 | 결제 |
| 기준 문서 | [유료 결제·환불](../../../p1/payment-refund.md), [전체관리자](../../../p1/platform-admin.md), [P1 ERD](../../../p1-erd.md), [ADR-0069](../../../adr/0069-use-p0-capacity-hold-and-reservation-price-snapshot-for-paid-checkout.md), [ADR-0070](../../../adr/0070-use-full-refund-with-bounded-manual-retry-and-discrepancy-closure.md), [ADR-0071](../../../adr/0071-deidentify-p1-benefit-data-on-withdrawal-and-extend-common-audit.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 유료 예약 결제의 생성·상태 조회, PortOne 웹훅을 통한 서버 결제 승인 확인, 담당 예약 결제·환불
상태 조회와 전체관리자의 결제 불일치 조회·수동 조치 HTTP 계약을 정의한다.

**환불 처리는 이 문서의 범위가 아니다.** 취소·환불 요청, 방문자의 내 환불 상태 조회, 환불 재시도와 결제
불일치의 전액환불 요청 처리는 별도 환불 도메인 문서가 소유한다. 이 문서의 "결제 불일치 문제없음 종결"은
전체관리자가 조사 결과 조치가 필요 없다고 판단한 경우에만 사용하며, 전액환불이 필요하면 환불 도메인의
API로 이관한다. 다만 담당 예약 결제·환불 상태 조회는 운영자 문의 대응을 위해 환불 상태를 읽기 전용으로만
함께 보여준다.

결제는 P0 `capacity_hold`와 홀드당 하나의 불변 `reservation_price_snapshot`에 연결되며, 예약은 서버가
PortOne V2에서 외부 거래를 재조회·검증한 뒤에만 생성한다(`ADR-0069`). 클라이언트가 보낸 성공 정보만으로
결제 성공이나 예약 확정을 처리하지 않는다. 결제 승인·확정은 [PortOne 결제 웹훅 수신](receive-portone-webhook.md)
이 유일한 진입점이며, 별도의 클라이언트 트리거형 승인 확인 API는 두지 않는다.

쿠폰은 [결제 생성](create-payment.md)의 `couponId`로만 선택한다. 결제 생성이 가격 스냅샷과 쿠폰 선점을 소유하며,
최종 금액이 0원이면 같은 요청에서 쿠폰 사용과 예약을 즉시 확정한다. 양수 결제는 서버 검증에 성공한 웹훅만
쿠폰 사용과 예약을 확정한다. 별도 쿠폰 사용 확정 HTTP API는 제공하지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-07, PAY-01 | `POST /api/v1/me/reservation-holds/{holdId}/payments` | `payment`, `payment_idempotency`, `reservation_price_snapshot`, `coupon`, `coupon_status_history`, `coupon_redemption` |
| P1-FR-07, PAY-02, PAY-03, PAY-04 | `POST /api/v1/webhooks/portone` | `payment_webhook`, `payment`, `payment_verification`, `capacity_hold`, `reservation`, `coupon`, `coupon_status_history`, `coupon_redemption`, `payment_discrepancy` |
| P1-FR-07, PAY-06 | `GET /api/v1/me/payments/{paymentId}` | `payment` |
| P1-FR-07, P1-FR-08, PAY-06 | `GET /api/v1/operator/reservations/{reservationId}/payment` | `payment`, `refund`, `reservation`, `payment_discrepancy` |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/payment-discrepancies` | `payment_discrepancy` |
| P1-FR-10, ADM-04 | `GET /api/v1/platform-admin/payment-discrepancies/{discrepancyId}` | `payment_discrepancy`, `payment`, `payment_verification`, `payment_discrepancy_action` |
| P1-FR-10, ADM-04 | `POST /api/v1/platform-admin/payment-discrepancies/{discrepancyId}/manual-actions` | `payment_discrepancy`, `payment_discrepancy_action`, `audit_event` |

## 2. 공통 계약 참조

| 대상 | 기준 문서 | 이 도메인에서 명시할 내용 |
| --- | --- | --- |
| Base URL·미디어 타입·시간 형식 | [API 공통 규칙](../../common/api-conventions.md) | Base URL은 `/api/v1`이며, 결제·불일치의 사건 시각은 UTC ISO 8601 형식이다. |
| 인증·인가 | [인증·인가](../../common/authentication.md) | 방문자 API(`/me/...`)는 활성 회원 본인 소유 조건, 운영자 API(`/operator/...`)는 담당 콘텐츠·예약 소유권, 전체관리자 API(`/platform-admin/...`)는 `SUPER_ADMIN` 또는 `PLATFORM_ADMIN` 활성 배정을 검증한다. PortOne 웹훅은 `Authorization` Bearer 인증 대신 공급자 서명 검증을 사용하며 [인증 제외 API](../../common/authentication.md) 표에 추가한다. |
| 성공·오류 응답 | [응답·오류](../../common/response-and-error.md) | API별 `data` 필드와 오류 코드를 확인한다. `PAYMENT_HOLD_CONFLICT`, `PAYMENT_DISCREPANCY_STATE_CONFLICT`, `WEBHOOK_SIGNATURE_INVALID`는 P1 구현 시 전역 `ErrorCode`에 추가한다. |
| 멱등성 | [ADR-0069](../../../adr/0069-use-p0-capacity-hold-and-reservation-price-snapshot-for-paid-checkout.md) | 결제 생성은 `Idempotency-Key`와 전용 `payment_idempotency`로 결제 종결 또는 0원 예약 확정 완료부터 24시간 보관하는 영속 멱등 처리 대상이다. 웹훅 수신은 `payment.status` 및 `payment_webhook.provider_event_id` 유일성으로 자연 멱등을 보장하며 별도 `Idempotency-Key`를 받지 않는다. |
| 감사 이력 | [P1 ERD](../../../p1-erd.md), [ADR-0071](../../../adr/0071-deidentify-p1-benefit-data-on-withdrawal-and-extend-common-audit.md) | 결제·불일치 상태 전이는 대상·처리자·이전·이후 상태·사유·증빙 참조·시각과 서버가 부여한 `requestId`를 함께 감사한다. `requestId`는 응답에 노출하지 않는다. 결제 비밀값, 웹훅 원문, 토큰과 전체 결제수단 정보는 저장하지 않는다. |
| 페이지네이션 | [페이지네이션](../../common/pagination.md) | 이 도메인의 모든 목록 API는 단순 목록이며 P0 관례에 따라 페이지네이션, 커서와 사용자 지정 정렬을 제공하지 않는다. |

## 기능별 API 명세

| 기능 | API 경로 | 명세 |
| --- | --- | --- |
| 유료 예약 결제 생성 | `POST /me/reservation-holds/{holdId}/payments` | [create-payment.md](create-payment.md) |
| 내 결제 상태 조회 | `GET /me/payments/{paymentId}` | [get-my-payment.md](get-my-payment.md) |
| 담당 예약 결제·환불 상태 조회 | `GET /operator/reservations/{reservationId}/payment` | [get-operator-reservation-payment.md](get-operator-reservation-payment.md) |
| PortOne 결제 웹훅 수신 (서버 결제 승인 확인 포함) | `POST /webhooks/portone` | [receive-portone-webhook.md](receive-portone-webhook.md) |
| 결제 불일치 목록 조회 | `GET /platform-admin/payment-discrepancies` | [list-payment-discrepancies.md](list-payment-discrepancies.md) |
| 결제 불일치 상세 조회 | `GET /platform-admin/payment-discrepancies/{discrepancyId}` | [get-payment-discrepancy.md](get-payment-discrepancy.md) |
| 결제 불일치 문제없음 종결 | `POST /platform-admin/payment-discrepancies/{discrepancyId}/manual-actions` | [resolve-payment-discrepancy.md](resolve-payment-discrepancy.md) |
