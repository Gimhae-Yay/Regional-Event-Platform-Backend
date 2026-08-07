# 쿠폰 만료 처리 스케줄러 구현 명세

| 항목 | 내용 |
| --- | --- |
| 대상 릴리스 | P1 |
| 관련 요구사항 | `P1-FR-06`, `CPN-05` |
| 소유 도메인 | 쿠폰 |
| 기준 문서 | [쿠폰 API](coupon.md), [쿠폰 도메인 공통 값](coupon-common.md), [쿠폰](../../../p1/coupon.md), [P1 ERD](../../../p1-erd.md), [ADR-0068](../../../adr/0068-use-immutable-coupon-lifecycle-and-evidence-sources.md), [ADR-0007](../../../adr/0007-use-transactional-outbox-with-spring-scheduler.md), [홀드 만료·무효화 작업](../../p0/reservation/expire-or-invalidate-holds.md) |

## 1. 목적과 범위

이 문서는 만료 시각이 지난 미선점(`AVAILABLE`) 쿠폰을 `EXPIRED`로 전이하는 내부 배치 작업의 구현 계약이다.
HTTP API나 외부 이벤트를 제공하지 않는다.

가격 스냅샷에 선점된 `RESERVED` 쿠폰의 만료 전이는 이 문서의 범위가 아니다. 그 경로는 결제·홀드 종결
처리와 함께 [홀드 만료·무효화 작업](../../p0/reservation/expire-or-invalidate-holds.md#6-홀드-만료무효화와-정원-1회-복구)
처리 규칙 15번이 담당하며, 이 스케줄러가 대체하지 않는다.

## 2. 실행 계약

| 항목 | 계약 |
| --- | --- |
| 실행 주체 | Spring Scheduler |
| 실행 주기 | 운영 설정으로 관리한다. P1 기본값은 5분 고정 지연(`0 */5 * * * *`)이다. |
| 시간 기준 | 대상 판정은 애플리케이션 서버 시각이 아닌 MySQL 현재 시각을 사용한다. |
| 처리 단위 | 최대 100건의 배치를 하나의 트랜잭션으로 처리하고, 대상이 남아 있으면 다음 배치를 실행한다. |
| 종료 조건 | 조회한 대상이 없거나 마지막 배치가 100건보다 적으면 종료한다. |
| 외부 의존성 | MySQL만 사용한다. Redis 분산 락, 외부 큐와 Outbox는 사용하지 않는다. |

운영 설정을 바꿔도 대상 조건·전이 범위·조건부 갱신 계약은 바꾸지 않는다.

## 3. 대상과 상태 전이

### 대상 조건

다음 조건을 모두 만족하는 쿠폰만 선택한다.

- `status = AVAILABLE`
- `expires_at <= MySQL 현재 시각`

`RESERVED`, `USED`, `EXPIRED`, `INVALIDATED` 쿠폰과 아직 만료 시각이 지나지 않은 `AVAILABLE` 쿠폰은 대상이
아니다.

### 조건부 갱신

각 대상은 동일한 조건을 `WHERE` 절에 다시 포함한 단일 갱신으로 처리한다.

```text
UPDATE coupon
SET status = 'EXPIRED'
WHERE coupon_id = :couponId
  AND status = 'AVAILABLE'
  AND expires_at <= MySQL 현재 시각
```

갱신에 성공하면 같은 트랜잭션에서 `coupon_status_history(previous_status = 'AVAILABLE', next_status =
'EXPIRED', reason_code = 'EXPIRATION_SCHEDULE', actor_kind = 'SYSTEM', occurred_at = MySQL 현재 시각)`를
추가하고, `target_type = COUPON`인 `audit_event`를 함께 남긴다. 갱신 건수 `0`은 다른 실행이 먼저 처리했거나
대상 조건이 더는 성립하지 않음(예: 그사이 결제 생성이 먼저 선점)을 뜻하며 오류로 취급하지 않는다.
`coupon_policy_id`, `user_id`, `issued_at`은 이 작업에서 변경하지 않는다.

## 4. 정합성·실패 처리

- 각 배치는 독립 트랜잭션이다. 배치 중 오류가 나면 해당 배치의 갱신을 모두 롤백하고 이후 배치를 실행하지
  않는다.
- 다음 실행은 미처리 대상을 같은 조건으로 다시 조회하므로 별도 재시도 큐·실패 상태를 저장하지 않는다.
- 여러 인스턴스가 같은 시간에 실행돼도 `status = 'AVAILABLE'` 조건부 갱신으로 한 실행만 상태를 바꾼다.
  중복 조회·0건 갱신은 허용한다.
- 결제 생성이 같은 쿠폰을 `AVAILABLE → RESERVED`로 선점하는 처리와 경합할 수 있다. 조건부 갱신에 먼저
  성공한 처리만 반영된다. 스케줄러가 먼저 성공하면 결제 생성의 쿠폰 선점은 대상을 찾지 못해 실패하고,
  결제 생성이 먼저 성공하면 스케줄러는 해당 쿠폰을 0건 갱신으로 건너뛴다.
- 스케줄러가 중단되거나 DB를 사용할 수 없으면 만료 처리하지 못한 쿠폰은 `AVAILABLE`로 유지된다. 복구 뒤
  다음 실행에서 같은 조건으로 다시 처리한다.
- 이미 `EXPIRED`인 쿠폰을 대상으로 반복 실행해도 조건부 갱신이 0건이라 상태·이력이 중복 생성되지 않는다.

## 5. 관측과 보안

실행 시작·종료와 배치별 조회·전이·0건 갱신·실패 건수, 소요 시간만 구조화 로그와 운영 지표로 남긴다. 쿠폰
소유 회원, 정책, 결제·예약 식별자는 로그와 예외 메시지에 남기지 않는다.

이 작업은 시스템이 수행하는 정기 만료 전이이므로 `audit_event`에 처리자를 연결하지 않되, [홀드 만료·무효화
작업](../../p0/reservation/expire-or-invalidate-holds.md)과 동일하게 `target_type = COUPON` 감사 이벤트는
남긴다.

## 6. 구현 경계

- 스케줄러는 실행 시각과 배치 반복만 책임지고, 대상 선택과 상태 전이는 쿠폰 도메인 Service·Repository에
  위임한다.
- 전이 Service의 공개 메서드는 배치 단위 쓰기 트랜잭션을 연다.
- Repository는 MySQL 현재 시각을 이용해 대상 후보를 제한 조회하고 조건부 갱신을 수행한다.
- JPA 엔티티를 스케줄러 로그·응답 모델로 직접 노출하지 않는다.

## 7. 완료 기준

- `expires_at` 전의 `AVAILABLE` 쿠폰과 `RESERVED`·`USED`·`EXPIRED`·`INVALIDATED` 쿠폰은 어떤 배치에서도
  상태가 바뀌지 않는다.
- 정확히 만료 시각이 지난 `AVAILABLE` 쿠폰은 다음 실행에서 `EXPIRED`로 전이되고 `coupon_status_history`에
  한 건이 추가된다.
- 결제 생성의 쿠폰 선점과 스케줄러가 경합해도 한쪽만 성공하고 쿠폰은 하나의 최종 상태로 수렴한다.
- 두 스케줄러 실행이 경합해도 결과는 한 번 전이한 것과 같다.
- 배치 오류는 해당 배치의 변경을 롤백하며 다음 실행에서 재시도할 수 있다.
- `RESERVED` 쿠폰은 이 스케줄러가 아니라 [홀드 만료·무효화 작업](../../p0/reservation/expire-or-invalidate-holds.md)에서만 전이된다.
- 운영 로그·지표와 예외에 쿠폰 소유 회원, 정책, 결제·예약 식별자가 포함되지 않는다.
