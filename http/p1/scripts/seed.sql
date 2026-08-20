-- P1 HTTP 시나리오 전용 로컬 시드. 운영 DB와 다른 개발 데이터에만 적용한다.
START TRANSACTION;

SET @now = UTC_TIMESTAMP(6);
SET @password_hash = '{bcrypt}$2a$12$ssgGGtMwA9aYPG.BtDlUaOVh6oJC19qJkadq6seaZSh/xLpYuxX.u';

UPDATE content
SET reservation_price = 15000
WHERE content_id = 900001;

DELETE FROM user_role_assignment
WHERE user_id IN (10, 11, 12);

INSERT INTO app_user (
  user_id, login_identifier, password_hash, name, phone, status, account_kind, created_at, updated_at
)
VALUES
  (10, 'p1-super-admin@example.test', @password_hash, 'P1 최고 관리자', '01000000010', 'ACTIVE', 'PRIVILEGED', @now, @now),
  (11, 'p1-platform-admin@example.test', @password_hash, 'P1 플랫폼 관리자', '01000000011', 'ACTIVE', 'PRIVILEGED', @now, @now),
  (12, 'p1-region-admin-candidate@example.test', @password_hash, 'P1 지역 관리자 후보', '01000000012', 'ACTIVE', 'ORDINARY', @now, @now)
ON DUPLICATE KEY UPDATE
  login_identifier = VALUES(login_identifier), password_hash = VALUES(password_hash), name = VALUES(name),
  phone = VALUES(phone), status = VALUES(status), account_kind = VALUES(account_kind), updated_at = VALUES(updated_at);

INSERT INTO user_role_assignment (user_id, role, region_id, status, granted_at, revoked_at, revoke_reason_code)
VALUES (12, 'VISITOR', NULL, 'ACTIVE', @now, NULL, NULL);

INSERT INTO platform_admin_assignment (
  platform_admin_assignment_id, user_id, grade, status, granted_at, inactivated_at, inactive_reason_code
)
VALUES
  (900001, 10, 'SUPER_ADMIN', 'ACTIVE', @now, NULL, NULL),
  (900002, 11, 'PLATFORM_ADMIN', 'ACTIVE', @now, NULL, NULL);

INSERT INTO coupon_policy (
  coupon_policy_id, content_id, region_id, name, description, issuance_type, discount_amount,
  minimum_payment_amount, valid_days, issue_starts_at, issue_ends_at, total_issue_limit, issued_count,
  status, published_at, ended_at, updated_at
)
VALUES
  (900101, 900001, 900001, 'P1 스탬프북 완주 보상', '스탬프북 완주 시 발급하는 로컬 쿠폰', 'STAMPBOOK_COMPLETION', 2000,
   10000, 30, DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 365 DAY), 100, 0,
   'PUBLISHED', @now, NULL, @now),
  (900102, 900001, 900001, 'P1 미션 완주 보상', '미션 보상용 로컬 쿠폰', 'MISSION_REWARD', 2000,
   10000, 30, DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 365 DAY), 100, 0,
   'PUBLISHED', @now, NULL, @now),
  (900103, 900001, 900001, 'P1 방문 결제 할인', '방문 기반 유료 예약 할인 쿠폰', 'VISIT', 3000,
   10000, 30, DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 365 DAY), 100, 1,
   'PUBLISHED', @now, NULL, @now);

INSERT INTO stampbook (
  stampbook_id, title, region_id, reward_coupon_policy_id, status, published_at, ended_at
)
VALUES
  (900201, 'P1 공개 스탬프북', 900001, 900101, 'PUBLISHED', @now, NULL),
  (900202, 'P1 반려 대상 스탬프북', 900001, 900101, 'PENDING_REVIEW', NULL, NULL);

INSERT INTO stampbook_content (stampbook_id, content_id)
VALUES
  (900201, 900001),
  (900202, 900001);

INSERT INTO stampbook_progress (stampbook_progress_id, stampbook_id, user_id, status, completed_at)
VALUES (900301, 900201, 8, 'IN_PROGRESS', NULL);

INSERT INTO stamp_earn (stamp_earn_id, stampbook_progress_id, visit_id, content_id, earned_at)
VALUES (900401, 900301, 950001, 900001, @now);

INSERT INTO mission (
  mission_id, title, region_id, condition_type, required_visit_count, reward_coupon_policy_id,
  status, ends_at, published_at, ended_at
)
VALUES
  (900501, 'P1 참여 생성 대상 미션', 900001, 'VISIT_COUNT', 1, 900102,
   'PUBLISHED', DATE_ADD(@now, INTERVAL 365 DAY), @now, NULL),
  (900502, 'P1 반려 대상 미션', 900001, 'VISIT_COUNT', 1, 900102,
   'PENDING_REVIEW', DATE_ADD(@now, INTERVAL 365 DAY), NULL, NULL),
  (900503, 'P1 보상 수령 대상 미션', 900001, 'VISIT_COUNT', 1, 900102,
   'PUBLISHED', DATE_ADD(@now, INTERVAL 365 DAY), @now, NULL);

INSERT INTO mission_target_content (mission_id, content_id)
VALUES
  (900501, 900001),
  (900502, 900001),
  (900503, 900001);

INSERT INTO mission_participation (
  mission_participation_id, mission_id, user_id, status, joined_at, completed_at
)
VALUES (900601, 900503, 8, 'COMPLETED', DATE_SUB(@now, INTERVAL 1 DAY), @now);

INSERT INTO mission_progress (mission_participation_id, visit_id, content_id, recorded_at)
VALUES (900601, 950001, 900001, @now);

INSERT INTO coupon (coupon_id, coupon_policy_id, user_id, status, issued_at, expires_at)
VALUES (900701, 900103, 8, 'AVAILABLE', @now, DATE_ADD(@now, INTERVAL 30 DAY));

INSERT INTO coupon_issuance (
  coupon_issuance_id, coupon_id, coupon_policy_id, recipient_user_id, visit_id, mission_reward_claim_id,
  stampbook_reward_grant_id, issuance_identity_hash, issued_at
)
VALUES (
  900801, 900701, 900103, 8, 950001, NULL, NULL,
  'p1-local-visit-coupon-900701', @now
);

INSERT INTO coupon_status_history (
  coupon_status_history_id, coupon_id, previous_status, next_status, reason_code, actor_kind, occurred_at
)
VALUES (900901, 900701, NULL, 'AVAILABLE', 'VISIT_ISSUANCE', 'SYSTEM', @now);

INSERT INTO content_session (
  session_id, content_id, region_id, status, starts_at, ends_at, checkin_open_at, checkin_close_at,
  capacity, remaining_capacity, cancelled_at, cancelled_by_user_id, cancellation_reason, completed_at,
  version_no, created_at, updated_at, reviewed_at, reviewed_by_user_id, reject_reason
)
VALUES
  (911001, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 2 DAY), DATE_ADD(@now, INTERVAL 50 HOUR),
   DATE_ADD(@now, INTERVAL 47 HOUR), DATE_ADD(@now, INTERVAL 49 HOUR),
   10, 9, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (911002, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 3 DAY), DATE_ADD(@now, INTERVAL 74 HOUR),
   DATE_ADD(@now, INTERVAL 71 HOUR), DATE_ADD(@now, INTERVAL 73 HOUR),
   10, 9, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL),
  (911003, 900001, 900001, 'SCHEDULED', DATE_ADD(@now, INTERVAL 4 DAY), DATE_ADD(@now, INTERVAL 98 HOUR),
   DATE_ADD(@now, INTERVAL 95 HOUR), DATE_ADD(@now, INTERVAL 97 HOUR),
   10, 9, NULL, NULL, NULL, NULL, 1, @now, @now, NULL, NULL, NULL);

INSERT INTO capacity_hold (
  hold_id, region_id, session_id, user_id, quantity, status, expires_at, terminal_at, invalidation_reason,
  capacity_released_at, created_at
)
VALUES
  (941001, 900001, 911001, 8, 1, 'ACTIVE', DATE_ADD(@now, INTERVAL 30 MINUTE), NULL, NULL, NULL, @now),
  (941002, 900001, 911002, 8, 1, 'CONSUMED', DATE_ADD(@now, INTERVAL 30 MINUTE), @now, NULL, NULL, @now),
  (941003, 900001, 911003, 8, 1, 'CONSUMED', DATE_ADD(@now, INTERVAL 30 MINUTE), @now, NULL, NULL, @now);

INSERT INTO reservation (
  reservation_id, reservation_no, qr_reference, region_id, hold_id, session_id, user_id, status, confirmed_at,
  cancelled_at, cancellation_reason, expired_at, capacity_released_at, updated_at
)
VALUES
  (931001, 'RP1PAID1', '00000000-0000-4000-8000-000000001001', 900001, 941002, 911002, 8, 'CONFIRMED', @now,
   NULL, NULL, NULL, NULL, @now),
  (931002, 'RP1PAID2', '00000000-0000-4000-8000-000000001002', 900001, 941003, 911003, 8, 'CONFIRMED', @now,
   NULL, NULL, NULL, NULL, @now);

INSERT INTO reservation_price_snapshot (
  reservation_price_snapshot_id, hold_id, coupon_id, base_amount, discount_amount, final_amount, currency, created_at
)
VALUES
  (951001, 941002, NULL, 15000, 0, 15000, 'KRW', @now),
  (951002, 941003, NULL, 15000, 0, 15000, 'KRW', @now);

INSERT INTO payment (
  payment_id, hold_id, reservation_price_snapshot_id, reservation_id, order_id, portone_payment_id,
  status, finalized_at, created_at
)
VALUES
  (961001, 941002, 951001, 931001, 'p1-local-order-961001', 'p1-local-portone-961001', 'APPROVED', @now, @now),
  (961002, 941003, 951002, 931002, 'p1-local-order-961002', 'p1-local-portone-961002', 'APPROVED', @now, @now);

INSERT INTO payment_discrepancy (
  payment_discrepancy_id, payment_id, discrepancy_type, status, detected_at
)
VALUES (971001, 961001, 'LOCAL_VERIFICATION_REQUIRED', 'OPEN', @now);

INSERT INTO refund (refund_id, payment_id, amount, status, requested_at, completed_at, resolved_at)
VALUES (981001, 961002, 15000, 'DISCREPANT', DATE_SUB(@now, INTERVAL 1 DAY), @now, NULL);

INSERT INTO refund_attempt (
  refund_attempt_id, refund_id, attempt_no, initiator_kind, portone_cancellation_id, outcome_kind,
  failure_reason_code, external_status, result_hash, attempted_at
)
VALUES (
  991001, 981001, 1, 'SYSTEM', NULL, 'NO_RESPONSE', 'TIMEOUT', NULL, NULL, @now
);

COMMIT;
