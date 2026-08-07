# ADR-0022: 멱등 명령 결과를 idempotency_record에 직접 연결한다

- 상태: 채택됨
- 기록 유형: 신규
- 기록일: 2026-07-28
- 결정일: 2026-07-28
- 관련 요구사항: [P0 명세 FR-06·FR-07](../p0-spec.md#7-기능-요구사항과-소유-문서), [AC-03·AC-05](../p0-spec.md#9-테스트-및-출시-수용-기준)
- 관련 단계: 단계 1. MVP 구현·검증
- 관련 이슈: 없음
- 대체 대상: 없음. [ADR-0003](0003-use-persisted-idempotency-for-reservation-and-checkin.md)의 영속 멱등성 원칙은 유지하며, 그 결과 참조의 물리 모델만 구체화한다.

## 맥락

P0에서 멱등 처리 대상은 무료 예약 확정과 체크인 두 명령뿐이다. 기존 논리 모델은 공통
`idempotency_record` 아래에 `reservation_confirmation_request`, `checkin_request`를 1:1 하위
테이블로 두어 요청별 입력과 성공 결과를 연결한다.

그러나 무료 예약에는 결제 대기·승인 같은 별도 요청 수명주기가 없고, `reservation.status = CONFIRMED`가
확정 완료를 이미 표현한다. 두 하위 테이블은 성공 결과 참조만 위해 존재하면서 ERD 관계와 제약을 늘린다.
특히 앞으로 종결된 미확정 홀드의 보관 방식을 검토할 때 `reservation_confirmation_request.hold_id` FK가
불필요한 결합점이 된다. 이번 결정은 홀드 만료·무효화의 상태·보관 정책을 변경하지 않는다.

ADR-0003은 같은 키의 결과 재반환, 처리 중 중복 실행 방지, 멱등 기록과 도메인 변경의 같은 MySQL
트랜잭션 커밋을 요구하지만, 결과 참조를 하위 테이블에 둘 것을 요구하지는 않는다.

## 결정 동인과 불변 조건

- 같은 actor·operation·멱등 키와 같은 요청은 이전 완료 결과를 반환한다.
- 예약 확정은 같은 홀드를 한 번만 `CONSUMED`로 전환하고 예약을 한 건만 생성한다.
- 체크인은 예약당 방문을 한 건만 생성한다.
- 멱등 키 점유, 도메인 변경, 성공 결과 참조 기록은 하나의 MySQL 트랜잭션에서 함께 커밋한다.
- `RESERVATION_CONFIRM` 결과와 `CHECK_IN` 결과의 FK 무결성을 유지한다.
- P0 밖의 명령·결제 결과·범용 다형 결과 참조를 미리 추가하지 않는다.

## 검토한 선택지

| 순서 | 선택지 | 장점 | 단점·실패 위험 | 되돌림 비용 | 현재 단계 적합성 |
| --- | --- | --- | --- | --- | --- |
| 1 | `idempotency_record`에 작업별 결과 FK를 직접 둔다 | 멱등성 책임이 한 테이블에 모이고, 두 요청 상세 테이블과 그 FK가 사라진다. 결과 FK 무결성을 유지한다. | `operation`·처리 상태별 null 제약을 명시적으로 강제해야 한다. | 중간 | 두 명령만 있는 무료 예약 P0에 가장 적합하다. |
| 2 | 기존 요청 상세 테이블을 유지한다 | 요청별 입력과 결과 관계가 명시적이다. | 무료 예약에 필요 없는 1:1 테이블과 관계·제약이 유지된다. | 낮음 | 과도하다. |
| 3 | `result_type`, `result_id` 범용 다형 참조를 둔다 | 결과 컬럼 수가 적고 명령 추가가 쉬워 보인다. | MySQL FK로 예약·방문 참조를 보장할 수 없고 P0 밖 확장을 앞당긴다. | 중간 | 부적합하다. |

## 결정

`reservation_confirmation_request`, `checkin_request`를 제거한다. `idempotency_record`에 아래 nullable FK를
직접 둔다.

- `result_reservation_id`: `RESERVATION_CONFIRM` 성공 결과의 `reservation` 참조
- `result_visit_id`: `CHECK_IN` 성공 결과의 `visit` 참조

각 멱등 기록은 작업별 결과 FK를 최대 하나만 가진다. `operation = RESERVATION_CONFIRM` 및
`status = SUCCEEDED`이면 `result_reservation_id`만 존재하고 `result_visit_id`는 없다.
`operation = CHECK_IN` 및 `status = SUCCEEDED`이면 `result_visit_id`만 존재하고
`result_reservation_id`는 없다. 서로 다른 체크인 요청 ID의 새 QR 재스캔은 같은 `visit` 결과를
반환할 수 있으므로 결과 FK 자체에는 유일 제약을 두지 않는다.
`PROCESSING`과 `FAILED`에는 두 결과 FK가 모두 없다. 저장하기로 한 결정적 도메인 실패는
`result_code`로 재응답한다.

`request_hash`는 요청 의미를 검증하는 근거로 유지하며, `hold_id`, `reservation_id`, `session_id`를
요청별 하위 테이블에 중복 저장하지 않는다. 성공 결과의 원본 입력 관계는 `reservation.hold_id`,
`reservation.session_id`, `visit.reservation_id`, `visit.session_id`로 조회한다.

이 결정은 `capacity_hold`의 `EXPIRED`·`INVALIDATED` 전이, 관련 컬럼 및 보관 기간을 변경하지 않는다.
그 정책을 바꾸려면 ADR-0001의 해당 범위를 명시적으로 대체하는 별도 ADR이 필요하다.

## 결과와 트레이드오프

### 기대 효과

- 멱등 키, 요청 해시, 처리 상태와 성공 결과 참조를 한 테이블에서 조회한다.
- 무료 예약 확정의 `CONFIRMED` 상태를 별도 확인 상태 없이 사용한다.
- 요청 상세 테이블의 FK가 미확정 홀드의 장래 보관 정책을 불필요하게 제약하지 않는다.
- ADR-0003의 결과 재응답과 도메인 고유 제약을 그대로 유지한다.

### 수용한 단점과 위험

- 두 nullable 결과 FK가 존재하며, `operation`과 `status`에 따른 조건부 제약을 DB와 통합 테스트로
  함께 검증해야 한다.
- 실패·처리 중 요청은 원본 대상 ID를 별도 관계로 보관하지 않는다. 운영 추적은 `request_hash`의 비개인
  의미와 감사·구조화 로그를 사용한다.
- P0 이후 멱등 명령이 늘어나면 결과 FK가 계속 증가할 수 있다.

## 전환과 롤백

문서 단계에서는 코드·migration을 변경하지 않는다. 구현 시에는 다음 순서를 따른다.

1. 두 결과 FK와 operation별 조건부 제약을 추가한다.
2. 기존 `reservation_confirmation_request.reservation_id`와 `checkin_request.visit_id`를 결과 FK로
   backfill하고, 성공 멱등 기록의 operation·status·결과 FK 조합을 검증한다.
3. 새 쓰기는 결과 FK만 기록하고, 읽기는 결과 FK를 우선 사용한다.
4. 기존 멱등 키의 보관 기간이 지나고 하위 테이블 참조가 없음을 확인한 뒤 두 하위 테이블을 제거한다.

하위 테이블을 제거하기 전에는 이전 읽기 경로로 되돌릴 수 있다. 제거 후 문제가 발견되면 결과 FK에서
성공한 예약·방문 관계를 다시 구성하는 호환 테이블을 먼저 만든다. 실패·처리 중 과거 요청의 대상 ID는
의도적으로 보존하지 않으므로, 이 경우의 기본 복구는 이전 모델로의 완전 복원이 아니라 전방 수정이다.

## 검증 방법

- 같은 예약 확정 또는 체크인 요청을 직렬·동시로 반복해 결과 엔터티가 한 건이고 같은 결과 FK를 반환하는지 검증한다.
- 서로 다른 체크인 요청 ID로 유효한 QR을 재스캔해도 새 방문을 만들지 않고 기존 `visit`을 결과로
  참조하는지 검증한다.
- 같은 키에 다른 `request_hash`를 보내면 결과 FK를 변경하지 않고 충돌로 거부되는지 검증한다.
- `SUCCEEDED` 멱등 기록의 operation과 결과 FK 조합, `PROCESSING`·`FAILED`의 결과 FK 부재를 DB 제약과
  통합 테스트로 검증한다.
- 예약 확정·체크인 트랜잭션을 중간에 실패시켜 결과 FK만 또는 도메인 행만 커밋되지 않는지 검증한다.
- `operation`, 처리 상태, 결과 FK 존재 여부, `requestId`를 비개인 구조화 로그와 지표로 관찰하고,
  결과 FK 조합 위반은 즉시 오류로 판정한다.

## 대체 조건

- P0에 세 번째 이상의 멱등 명령이 확정돼 작업별 결과 FK가 반복적으로 늘어난다.
- 하나의 명령이 여러 결과 엔터티 또는 외부 거래 결과를 함께 반환해야 한다.
- 멱등 기록 보관량·응답 재구성 비용이 합의한 저장·응답 SLO를 지속적으로 위반한다.
