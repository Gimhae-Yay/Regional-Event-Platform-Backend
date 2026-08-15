# ADR-0100: 쿠폰 사용 반전의 공식 기록을 사용 이력에 보존

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-15
- 결정일: 2026-08-15
- 관련 요구사항: [`docs/p1/coupon.md`의 `CPN-04`~`CPN-05`](../p1/coupon.md#3-쿠폰-정책), [`docs/p1/payment-refund.md`의 `PAY-05`](../p1/payment-refund.md#3-결제환불-정책), [`docs/api/p1/refund/refund.md`의 쿠폰 복구 계약](../api/p1/refund/refund.md#쿠폰-복구-계약)
- 관련 단계: 단계 3. 확장 의사결정
- 관련 이슈: [#841](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/841), 부모 Bug [#833](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/833)
- 대체 대상: 없음

## 맥락

환불 공통 계약은 쿠폰 사용 이력을 `REVERSED`로 전이할 때 환불 식별자, 반전 사유와 반전 시각을 기록하도록 요구한다. 그러나 기존 `coupon_redemption`은 상태와 `reversed_at`만 보존하므로, 반전된 사용 이력만으로 유료 환불과 환불 행이 없는 최종 금액 0원 예약 취소를 구분하거나 원인 거래를 조회할 수 없다.

기존 `REFUND`, `COUPON` 감사 이벤트는 같은 `requestId`와 사유 코드를 남기지만 `coupon_redemption`과 환불·예약 취소 출처를 직접 연결하지 않는다. 감사 보관 정책과 actor 비식별화도 쿠폰 사용 이력의 수명주기와 다르므로, 감사 이벤트를 공식 반전 기록으로 사용하면 사용 이력 조회가 간접 결합에 의존한다.

## 결정 동인과 불변 조건

- 반전된 `coupon_redemption` 한 행에서 환불 또는 0원 예약 취소 출처, 사유와 시각을 재현할 수 있어야 한다.
- 유료 예약은 실제 `refund`만 참조하고, 환불 행이 없는 0원 예약 취소를 위해 가짜 환불을 만들지 않는다.
- 사용자·운영자 취소, 최초 환불, 환불 재시도, 1분 고정 지연 복구와 수동 성공 확정이 같은 기록 규칙을 사용한다.
- 같은 반전 근거의 재처리는 새 효과를 만들지 않고, 다른 근거로 기존 반전 기록을 덮어쓰지 않는다.
- 쿠폰 사용 이력 반전, 쿠폰 상태 복구와 상태 이력은 같은 MySQL 트랜잭션에서 커밋하거나 롤백한다.
- 공개 API 경로·요청·응답·HTTP 상태와 오류 코드는 변경하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 추천안: nullable `refund_id`와 `reversal_reason_code`를 `coupon_redemption`에 직접 기록하고 0원 취소는 기존 `reservation_id`를 출처로 사용 | 실제 환불은 FK로 보장하고 가짜 환불이나 중복 예약 FK 없이 두 경로를 구분한다. 상태별 열 조합을 CHECK로 강제할 수 있다. | 출처 유형을 독립 열이 아니라 사유 코드와 FK 조합으로 판별한다. | 중간 | 높음 |
| 2 | `reversal_source_type`과 다형적 `reversal_source_id`를 기록 | 출처 표현 형식이 하나다. | 하나의 식별자가 `refund`와 `reservation`을 모두 가리켜 DB FK로 무결성을 보장할 수 없고 기존 `reservation_id`와 중복된다. | 중간 | 낮음 |
| 3 | 공통 감사 이벤트를 공식 기록으로 사용 | `coupon_redemption` 스키마 변경이 적다. | 사용 이력과 감사 사이 직접 FK가 없고 `requestId` 간접 결합과 서로 다른 보관 수명에 의존한다. | 높음 | 낮음 |

## 결정

`coupon_redemption`을 쿠폰 사용 반전의 공식 영속 기록으로 사용한다. 다음 열과 제약을 추가한다.

| 열 | 계약 |
| --- | --- |
| `refund_id BIGINT NULL` | 유료 환불 반전의 실제 `refund.refund_id`. `fk_coupon_redemption_refund`로 참조하고 `ON DELETE RESTRICT`를 적용한다. `uk_coupon_redemption_refund`로 값이 있을 때 유일하게 한다. 0원 예약 취소에서는 `NULL`이다. |
| `reversal_reason_code VARCHAR(50) NULL` | `REFUND_SUCCEEDED`, `RESERVATION_CANCELLED`만 허용한다. `CONFIRMED`에서는 `NULL`이다. |
| `reversed_at TIMESTAMP(6) NULL` | MySQL 현재 시각으로 고정한 반전 시각이다. `CONFIRMED`에서는 `NULL`, `REVERSED`에서는 `NOT NULL`이다. |

별도 `reversal_source_type`이나 `cancellation_reservation_id`는 만들지 않는다. 유료 환불은 `refund_id`, 0원 예약 취소는 사용 이력에 이미 존재하는 `reservation_id`를 공식 출처 식별자로 사용한다.

기존 `ck_coupon_redemption_status`는 유지하고, `ck_coupon_redemption_reversed_at`은 다음 조합을 강제하는 `ck_coupon_redemption_reversal`로 대체한다.

| 상태와 출처 | 필수 열 조합 |
| --- | --- |
| `CONFIRMED` | `refund_id IS NULL`, `reversal_reason_code IS NULL`, `reversed_at IS NULL` |
| 유료 환불 `REVERSED` | `refund_id IS NOT NULL`, `reversal_reason_code = 'REFUND_SUCCEEDED'`, `reversed_at IS NOT NULL` |
| 0원 취소 `REVERSED` | `refund_id IS NULL`, `reversal_reason_code = 'RESERVATION_CANCELLED'`, `reversed_at IS NOT NULL` |

다른 테이블을 조회해야 하는 조건은 FK와 CHECK만으로 강제하지 않는다. 애플리케이션은 잠근 행을 기준으로 다음을 검증한다.

- `REFUND_SUCCEEDED`: `refund.status = SUCCEEDED`이고 환불의 결제·예약·가격 스냅샷이 `coupon_redemption`의 예약·가격 스냅샷과 일치하며, 회차 시작 전 사용자·운영자 취소다.
- `RESERVATION_CANCELLED`: 가격 스냅샷의 `final_amount = 0`이고 연결된 결제·환불 행이 없으며, `coupon_redemption.reservation_id`의 예약이 회차 시작 전에 취소됐다.

같은 공식 출처와 사유로 이미 `REVERSED`인 사용 이력을 재처리하면 저장된 결과를 유지하는 무변경 성공으로 수렴한다. 다른 출처 또는 사유로 이미 반전된 행을 재처리하면 기존 값을 바꾸지 않고 내부 정합성 실패로 전체 상태 반영 트랜잭션을 롤백한다. 이 실패를 위한 새 공개 오류 코드는 만들지 않는다.

공통 감사 이벤트는 기존과 같이 같은 `requestId`로 남기되 보조 감사 기록으로만 사용한다. 공식 출처 조회는 감사 이벤트나 `requestId` 조인을 요구하지 않는다.

## 결과와 트레이드오프

### 기대 효과

- 반전된 사용 이력에서 실제 환불 또는 0원 예약 취소 출처와 사유·시각을 직접 조회할 수 있다.
- 환불 재시도·복구·수동 확정이 어느 경로에서 성공해도 같은 환불 식별자로 수렴한다.
- 감사 이벤트의 보관·비식별화와 무관하게 쿠폰 사용·복구 원인을 보존한다.
- FK·유일 제약·상태 조합 CHECK와 애플리케이션 검증의 책임 경계가 명확해진다.

### 수용한 단점과 위험

- `coupon_redemption`이 쿠폰뿐 아니라 환불 스키마에도 의존한다.
- 예약 금액, 결제 존재 여부와 회차 시작 전 취소 조건은 여러 테이블을 가로지르므로 DB CHECK만으로 강제할 수 없다.
- 이미 출처 없이 `REVERSED`인 운영 데이터가 있다면 감사 이벤트를 근거로 자동 추정해 이관할 수 없다.

## 전환과 롤백

후속 Fix Task는 기존 V27 migration을 수정하지 않고 새 migration으로 두 열, FK·유일 제약과 확장 CHECK를 추가한다. 제약 추가 전에 출처 없이 `REVERSED`인 기존 행의 존재 여부를 확인한다. 해당 행이 있으면 `requestId`나 감사 이벤트로 출처를 추정하지 않고 migration을 중단해 검증 가능한 원본 거래에 근거한 별도 데이터 보정 계획을 확정한다. `CONFIRMED` 기존 행은 새 열을 모두 `NULL`로 유지한다.

애플리케이션이 새 반전 기록을 쓰기 전에는 새 migration을 롤백할 수 있다. 새 형식의 `REVERSED` 데이터가 생성된 뒤 열을 제거하면 공식 출처가 유실되므로, 그 이후에는 새 ADR과 데이터 보존·이관 계획 없이 이전 스키마로 되돌리지 않는다.

## 검증 방법

- MySQL migration 테스트에서 FK, nullable 유일 제약과 세 상태 조합 CHECK를 검증한다.
- 엔티티 단위 테스트에서 유료 환불과 0원 취소의 필수 값, 허용하지 않은 조합과 두 번째 반전을 검증한다.
- 사용자·운영자 취소, 최초 환불, 환불 재시도, 1분 복구와 수동 성공 확정이 각각 올바른 출처·사유·시각을 보존하는지 검증한다.
- 같은 출처 재처리는 무변경으로 수렴하고 다른 출처 재처리는 기존 기록을 유지한 채 전체 트랜잭션을 롤백하는지 검증한다.
- 쿠폰 반전 기록, 쿠폰 현재 상태, `coupon_status_history`와 공통 감사 중 하나라도 실패하면 모두 롤백되는지 검증한다.
- 기존 공개 API 경로·응답·오류 계약이 바뀌지 않았는지 API 회귀 테스트로 확인한다.

## 대체 조건

- 부분 환불, 예약당 복수 쿠폰 또는 하나의 환불이 여러 쿠폰 사용 이력을 반전하는 정책이 확정되면 `refund_id` 유일 제약과 관계를 재검토한다.
- 환불·취소 원본을 통합한 별도 불변 거래 이벤트 저장소가 공식 기록으로 채택되면 소유 테이블을 재검토한다.
