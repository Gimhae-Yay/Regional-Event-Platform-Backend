# ADR-0090: 결제 생성 멱등 결과를 결제 또는 0원 예약으로 저장한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-08-09
- 결정일: 2026-08-09
- 관련 요구사항: [P1-FR-07](../p1-spec.md#6-기능-요구사항과-소유-문서), [PAY-01](../p1/payment-refund.md#3-결제·환불-정책), [유료 예약 결제 생성 API](../api/p1/payment/create-payment.md#3-유료-예약-결제-생성)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: [#522](https://github.com/Gimhae-Yay/Regional-Event-Platform-Backend/issues/522)
- 대체 대상: [ADR-0069](0069-use-p0-capacity-hold-and-reservation-price-snapshot-for-paid-checkout.md)의 `payment_idempotency` 성공 결과 연결 범위

## 맥락

ADR-0069는 유료 체크아웃의 결과를 전용 `payment_idempotency`에 보관하도록 정했다. 그러나 같은 API는 쿠폰 할인으로 최종 금액이 0원이면 `payment` 행을 만들지 않고 `reservation(CONFIRMED)`만 만든다. 기존 `payment_idempotency`의 성공 제약은 `payment_id`를 반드시 요구하므로, 0원 성공을 완료 상태로 저장하거나 같은 키 재시도에 최초 예약 결과를 반환할 수 없다.

## 결정 동인과 불변 조건

- 같은 회원·같은 키·같은 요청 의미의 재시도는 최초 `201 Created` 결과와 같은 결제 또는 예약 결과를 반환한다.
- 0원 확정은 `payment`를 만들거나 PortOne을 호출하지 않는다.
- 성공 멱등 기록은 결제 결과와 예약 결과를 동시에 가리키지 않으며, 실패·처리 중 기록은 결과를 가리키지 않는다.
- 결과 연결, 예약 확정과 쿠폰 사용은 하나의 MySQL 트랜잭션으로 커밋한다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | 추천안: `payment_idempotency.reservation_id`를 추가하고 성공 결과를 둘 중 정확히 하나로 제한 | 0원 결과를 명시적으로 보존해 재시도 응답을 안정적으로 재구성한다. 양수·0원 경로의 결과 유형을 DB 제약으로 구분한다. | FK·유일 제약·CHECK 변경과 기존 빈 P1 테이블의 migration이 필요하다. | 중간 | 높음 |
| 2 | `payment_id` 없이 홀드로 확정 예약을 역조회 | 열을 추가하지 않는다. | 성공 기록이 결과를 직접 가리키지 않아 홀드·예약 조회 규칙에 결합되고, 결과가 없는 성공 상태를 별도 허용해야 한다. | 낮음 | 중간 |

## 결정

`payment_idempotency`에 nullable `reservation_id` FK와 `UNIQUE (reservation_id)`를 추가한다.

- 양수 결제 생성 성공은 `payment_id`만 연결한다.
- 0원 예약 확정 성공은 `reservation_id`만 연결한다.
- `SUCCEEDED`는 `payment_id`, `reservation_id` 중 정확히 하나와 `completed_at`을 가져야 한다.
- `PROCESSING`과 `FAILED`는 두 결과 FK가 모두 `NULL`이어야 한다. `FAILED`만 `completed_at`을 가진다.

## 결과와 트레이드오프

### 기대 효과

- 0원 확정의 멱등 재시도가 별도 추론 없이 최초 예약 번호와 확정 시각을 반환한다.
- 양수 결제와 0원 확정이 같은 HTTP API를 사용하면서도 서로 다른 완료 결과를 혼동하지 않는다.

### 수용한 단점과 위험

- `payment_idempotency`가 두 종류의 결과 FK를 가지므로 CHECK 제약과 JPA 상태 전이 테스트를 함께 유지해야 한다.
- 이미 적용된 V26은 수정하지 않고 후속 Flyway migration으로 확장해야 한다.

## 전환과 롤백

P1 결제 생성 API는 아직 운영 데이터가 없으므로 V26 뒤에 `reservation_id`와 제약을 추가하는 순방향 migration을 적용한다. 배포 전 migration·통합 테스트가 실패하면 결제 생성 기능을 활성화하지 않는다. 적용 뒤 이 결정을 바꾸려면 새 ADR과 결과 데이터 이관 계획을 작성한다.

## 검증 방법

- 양수 금액 요청은 `payment_id`만 가진 성공 멱등 기록과 `PENDING` 결제 하나를 만드는지 확인한다.
- 0원 요청은 `reservation_id`만 가진 성공 멱등 기록, `CONFIRMED` 예약, 쿠폰 사용 이력을 같은 트랜잭션으로 만드는지 확인한다.
- 같은 키 재시도·다른 의미의 키 재사용·동시 요청에서 결과 하나로 수렴하거나 충돌하는지 MySQL 통합 테스트로 확인한다.
- 두 결과 FK가 모두 있거나 모두 없는 성공 기록을 DB CHECK가 거부하는지 확인한다.

## 대체 조건

- 결제 생성 결과가 세 번째 영속 결과 유형을 가져 단일 테이블의 다형 결과 제약이 지나치게 복잡해지거나, 결제 결과 보관 기간이 예약 결과와 달라지면 결과 저장소를 분리하는 후속 ADR을 검토한다.
