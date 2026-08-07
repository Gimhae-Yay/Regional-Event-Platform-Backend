# 예약

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | [P1-FR-08](../../../p1-spec.md#6-기능-요구사항과-소유-문서), `PAY-05`, `RSV-04` |
| 소유 도메인 | 예약 |
| 기준 문서 | [유료 결제·환불](../../../p1/payment-refund.md), [환불 API](../refund/refund.md), [정원 홀드·무료 예약](../../../p0/reservation.md), [P1 ERD](../../../p1-erd.md), [ADR-0001](../../../adr/0001-use-mysql-conditional-update-for-capacity-consistency.md), [API 공통 계약](../../common/README.md) |

## 1. 개요

이 문서는 P1 유료 예약 도메인이 소유하는 API의 링크 문서다. 예약·정원 전이는 P0 무료 예약 정책(`RSV-04`)을
그대로 따르며, 결제가 연결된 유료 예약의 취소는 취소 자격 검증과 예약 상태 전이만 이 도메인이 소유한다.
전액 환불의 생성·외부 PortOne 호출·재시도 가능 여부와 같은 환불 상태 자체의 소유권은
[환불 API](../refund/refund.md)에 있으며, 이 도메인은 그 처리 규칙을 참조로 재사용한다.

결제·환불 조회, 결제 불일치·환불 실패의 수동 처리는 각각 결제·환불·전체관리자 도메인 범위이며 이 문서에서
다루지 않는다.

### 요구사항 추적

| 요구사항 | HTTP 계약 | 주요 데이터 |
| --- | --- | --- |
| P1-FR-08, PAY-05, RSV-04 | `POST /api/v1/me/reservations/{reservationId}/cancel` | `reservation`, `reservation_price_snapshot`, `payment`, `refund`, `refund_attempt`, `coupon`, `coupon_redemption`, `coupon_status_history` |

## 2. 공통 계약 참조

인증, 응답 포맷, 오류 코드 목록과 공통 규칙은 [API 공통 계약](../../common/README.md)을 따른다.

## 3. 기능별 API 명세

| API | 문서 |
| --- | --- |
| 유료 예약 취소 | [유료 예약 취소](cancel-reservation.md) |
