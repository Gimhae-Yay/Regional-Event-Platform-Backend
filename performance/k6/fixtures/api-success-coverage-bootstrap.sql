SET @perf_platform_admin_id = 900013;
SET @perf_applicant_id = 900014;
SET @perf_withdrawal_visitor_id = 900015;
SET @perf_now = UTC_TIMESTAMP(6);
SET @perf_password_hash = '{bcrypt}$2a$12$/SenwR03QWMkim.0.mDq7uE3vB75E5egW2.A5FQPVmlBU9VEUlmm2';

-- 각 정상 응답 전이는 다른 행을 사용한다. 실행 중 상태가 바뀌어도 다음 케이스의
-- 선행 상태가 오염되지 않도록 900020~900099 범위를 이 fixture 전용으로 예약한다.
DELETE FROM payment_discrepancy_action
WHERE payment_discrepancy_id BETWEEN 900020 AND 900099;
DELETE FROM audit_event
WHERE audit_event_id = 900020;
DELETE FROM payment_discrepancy
WHERE payment_discrepancy_id BETWEEN 900020 AND 900099;
DELETE FROM refund_attempt
WHERE refund_id BETWEEN 900020 AND 900099;
DELETE FROM refund
WHERE refund_id BETWEEN 900020 AND 900099;
DELETE FROM payment
WHERE payment_id BETWEEN 900020 AND 900099;
DELETE FROM reservation
WHERE reservation_id BETWEEN 900020 AND 900099;
DELETE FROM reservation_price_snapshot
WHERE reservation_price_snapshot_id BETWEEN 900020 AND 900099;
DELETE FROM capacity_hold
WHERE hold_id BETWEEN 900020 AND 900099;
DELETE FROM content_withdrawal_request
WHERE content_withdrawal_request_id BETWEEN 900020 AND 900099;
DELETE FROM operator_application
WHERE operator_application_id BETWEEN 900020 AND 900099;
DELETE FROM session_revision
WHERE session_revision_id BETWEEN 900020 AND 900099;
DELETE FROM content_revision
WHERE content_revision_id BETWEEN 900020 AND 900099;
DELETE FROM stampbook_reward_grant
WHERE stampbook_progress_id BETWEEN 900020 AND 900099;
DELETE FROM stampbook_progress
WHERE stampbook_id BETWEEN 900020 AND 900099;
DELETE FROM stampbook_content
WHERE stampbook_id BETWEEN 900020 AND 900099;
DELETE FROM stampbook
WHERE stampbook_id BETWEEN 900020 AND 900099;
DELETE FROM mission_reward_claim
WHERE mission_participation_id BETWEEN 900020 AND 900099;
DELETE FROM mission_progress
WHERE mission_participation_id BETWEEN 900020 AND 900099;
DELETE FROM mission_participation
WHERE mission_id BETWEEN 900020 AND 900099;
DELETE FROM mission_target_content
WHERE mission_id BETWEEN 900020 AND 900099;
DELETE FROM mission
WHERE mission_id BETWEEN 900020 AND 900099;
DELETE FROM coupon_policy
WHERE coupon_policy_id = 900020;
DELETE FROM content_session
WHERE session_id BETWEEN 900020 AND 900099;
DELETE FROM content
WHERE content_id BETWEEN 900020 AND 900099;

INSERT INTO app_user (
    user_id,
    login_identifier,
    password_hash,
    name,
    phone,
    status,
    account_kind,
    created_at,
    updated_at
) VALUES (
    @perf_platform_admin_id,
    'k6-api-success-platform-admin@example.com',
    @perf_password_hash,
    'K6 Platform Admin',
    '01090000013',
    'ACTIVE',
    'PRIVILEGED',
    @perf_now,
    @perf_now
) ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    name = VALUES(name),
    phone = VALUES(phone),
    status = VALUES(status),
    account_kind = VALUES(account_kind),
    updated_at = VALUES(updated_at);

INSERT INTO platform_admin_assignment (
    user_id,
    grade,
    status,
    granted_at,
    inactivated_at,
    inactive_reason_code
) VALUES (
    @perf_platform_admin_id,
    'SUPER_ADMIN',
    'ACTIVE',
    @perf_now,
    NULL,
    NULL
) ON DUPLICATE KEY UPDATE
    grade = VALUES(grade),
    status = VALUES(status),
    granted_at = VALUES(granted_at),
    inactivated_at = VALUES(inactivated_at),
    inactive_reason_code = VALUES(inactive_reason_code);

INSERT INTO app_user (
    user_id,
    login_identifier,
    password_hash,
    name,
    phone,
    status,
    created_at,
    updated_at
) VALUES
    (
        @perf_applicant_id,
        'k6-applicant@example.com',
        @perf_password_hash,
        'K6 Applicant',
        '01090000014',
        'ACTIVE',
        @perf_now,
        @perf_now
    ),
    (
        @perf_withdrawal_visitor_id,
        'k6-withdraw@example.com',
        @perf_password_hash,
        'K6 Withdrawal',
        '01090000015',
        'ACTIVE',
        @perf_now,
        @perf_now
    ) ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    name = VALUES(name),
    phone = VALUES(phone),
    status = VALUES(status),
    updated_at = VALUES(updated_at);

INSERT INTO user_role_assignment (
    user_id,
    role,
    region_id,
    status,
    granted_at
) VALUES
    (@perf_applicant_id, 'VISITOR', NULL, 'ACTIVE', @perf_now),
    (@perf_withdrawal_visitor_id, 'VISITOR', NULL, 'ACTIVE', @perf_now)
ON DUPLICATE KEY UPDATE
    region_id = VALUES(region_id),
    status = VALUES(status),
    granted_at = VALUES(granted_at);

INSERT INTO content (
    content_id, region_id, operator_id, content_type, status, version_no,
    title, description, location_text, operating_hours_text, contact_text,
    precautions, age_requirement, materials, cancellation_policy_text,
    publish_at, deleted_at, created_at, updated_at,
    representative_image_object_id, representative_image_assigned_at, reservation_price
) VALUES
    (900020, 900001, 900001, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'K6 수정 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now - INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900021, 900001, 900001, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'K6 수정본 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now - INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900022, 900001, 900001, 'EVENT_EXPERIENCE', 'REJECTED', 1, 'K6 재제출 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now + INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900023, 900001, 900001, 'EVENT_EXPERIENCE', 'PENDING', 1, 'K6 심사 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now + INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900024, 900001, 900001, 'EVENT_EXPERIENCE', 'PENDING', 1, 'K6 반려 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now + INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900025, 900001, 900001, 'EVENT_EXPERIENCE', 'PENDING', 1, 'K6 삭제 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now + INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900026, 900001, 900001, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'K6 중단 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now - INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900027, 900001, 900001, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'K6 종료 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now - INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000),
    (900028, 900001, 900001, 'EVENT_EXPERIENCE', 'PUBLISHED', 1, 'K6 철회 대상', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', @perf_now - INTERVAL 1 DAY, NULL, @perf_now, @perf_now, 900001, @perf_now, 10000);

INSERT INTO content_session (
    session_id, content_id, region_id, status, starts_at, ends_at,
    checkin_open_at, checkin_close_at, capacity, remaining_capacity,
    cancelled_at, cancelled_by_user_id, cancellation_reason, completed_at,
    version_no, created_at, updated_at, reviewed_at, reviewed_by_user_id, reject_reason
) VALUES
    (900020, 900020, 900001, 'SCHEDULED', @perf_now + INTERVAL 10 DAY, @perf_now + INTERVAL 10 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 10 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 10 DAY + INTERVAL 90 MINUTE, 20, 20, NULL, NULL, NULL, NULL, 1, @perf_now, @perf_now, @perf_now, 900005, NULL),
    (900021, 900021, 900001, 'SCHEDULED', @perf_now + INTERVAL 11 DAY, @perf_now + INTERVAL 11 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 11 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 11 DAY + INTERVAL 90 MINUTE, 20, 20, NULL, NULL, NULL, NULL, 1, @perf_now, @perf_now, @perf_now, 900005, NULL),
    (900022, 900023, 900001, 'PENDING', @perf_now + INTERVAL 12 DAY, @perf_now + INTERVAL 12 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 12 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 12 DAY + INTERVAL 90 MINUTE, 20, 20, NULL, NULL, NULL, NULL, 1, @perf_now, @perf_now, NULL, NULL, NULL),
    (900023, 900020, 900001, 'SCHEDULED', @perf_now + INTERVAL 13 DAY, @perf_now + INTERVAL 13 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 13 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 13 DAY + INTERVAL 90 MINUTE, 20, 20, NULL, NULL, NULL, NULL, 1, @perf_now, @perf_now, @perf_now, 900005, NULL),
    (900024, 900021, 900001, 'SCHEDULED', @perf_now + INTERVAL 14 DAY, @perf_now + INTERVAL 14 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 14 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 14 DAY + INTERVAL 90 MINUTE, 20, 20, NULL, NULL, NULL, NULL, 1, @perf_now, @perf_now, @perf_now, 900005, NULL),
    (900025, 900026, 900001, 'SCHEDULED', @perf_now + INTERVAL 18 DAY, @perf_now + INTERVAL 18 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 18 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 18 DAY + INTERVAL 90 MINUTE, 20, 20, NULL, NULL, NULL, NULL, 1, @perf_now, @perf_now, @perf_now, 900005, NULL);

INSERT INTO content_revision (
    content_revision_id, content_id, revision_no, base_content_version, editor_user_id, status,
    title, description, location_text, operating_hours_text, contact_text, precautions,
    age_requirement, materials, cancellation_policy_text, reservation_price, publish_at,
    submitted_at, created_at
) VALUES
    (900020, 900020, 1, 1, 900001, 'EDIT_REQUESTED', 'K6 철회 수정본', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', 10000, @perf_now + INTERVAL 1 DAY, @perf_now, @perf_now),
    (900021, 900023, 1, 1, 900001, 'EDIT_REQUESTED', 'K6 심사 수정본', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', 10000, @perf_now + INTERVAL 1 DAY, @perf_now, @perf_now),
    (900022, 900026, 1, 1, 900001, 'EDIT_REQUESTED', 'K6 승인 수정본', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', 10000, @perf_now + INTERVAL 1 DAY, @perf_now, @perf_now),
    (900023, 900027, 1, 1, 900001, 'EDIT_REQUESTED', 'K6 반려 수정본', 'K6 fixture', 'K6 장소', '10:00-18:00', '01090000001', 'K6', '전체', '없음', 'K6', 10000, @perf_now + INTERVAL 1 DAY, @perf_now, @perf_now);

INSERT INTO session_revision (
    session_revision_id, content_id, region_id, target_session_id, base_session_version,
    starts_at, ends_at, checkin_open_at, checkin_close_at, capacity, status,
    requested_by_user_id, submitted_at, reviewed_at, reviewed_by_user_id, reject_reason, created_at
) VALUES
    (900020, 900020, 900001, 900023, 1, @perf_now + INTERVAL 15 DAY, @perf_now + INTERVAL 15 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 15 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 15 DAY + INTERVAL 90 MINUTE, 20, 'PENDING', 900001, @perf_now, NULL, NULL, NULL, @perf_now),
    (900021, 900021, 900001, 900024, 1, @perf_now + INTERVAL 16 DAY, @perf_now + INTERVAL 16 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 16 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 16 DAY + INTERVAL 90 MINUTE, 20, 'PENDING', 900001, @perf_now, NULL, NULL, NULL, @perf_now),
    (900022, 900023, 900001, 900022, 1, @perf_now + INTERVAL 17 DAY, @perf_now + INTERVAL 17 DAY + INTERVAL 2 HOUR, @perf_now + INTERVAL 17 DAY - INTERVAL 30 MINUTE, @perf_now + INTERVAL 17 DAY + INTERVAL 90 MINUTE, 20, 'PENDING', 900001, @perf_now, NULL, NULL, NULL, @perf_now);

INSERT INTO coupon_policy (
    coupon_policy_id, content_id, region_id, name, description, issuance_type,
    discount_amount, minimum_payment_amount, valid_days, issue_starts_at, issue_ends_at,
    total_issue_limit, issued_count, status, published_at, ended_at, updated_at
) VALUES (
    900020, 900020, 900001, 'K6 Stampbook Reward', 'K6 스탬프북 완료 보상', 'STAMPBOOK_COMPLETION',
    1000, 10000, 30, @perf_now - INTERVAL 1 DAY, @perf_now + INTERVAL 30 DAY,
    100, 0, 'PUBLISHED', @perf_now - INTERVAL 1 DAY, NULL, @perf_now
);

INSERT INTO mission (
    mission_id, region_id, condition_type, required_visit_count, reward_coupon_policy_id,
    status, ends_at, published_at, ended_at
) VALUES
    (900020, 900001, 'VISIT_COUNT', 1, 900011, 'PUBLISHED', @perf_now + INTERVAL 30 DAY, @perf_now - INTERVAL 1 DAY, NULL),
    (900021, 900001, 'VISIT_COUNT', 1, 900011, 'PENDING_REVIEW', @perf_now + INTERVAL 30 DAY, NULL, NULL),
    (900022, 900001, 'VISIT_COUNT', 1, 900012, 'PUBLISHED', @perf_now + INTERVAL 30 DAY, @perf_now - INTERVAL 1 DAY, NULL);

INSERT INTO stampbook (
    stampbook_id, region_id, reward_coupon_policy_id, status, published_at, ended_at
) VALUES
    (900020, 900001, 900020, 'PUBLISHED', @perf_now - INTERVAL 1 DAY, NULL),
    (900021, 900001, 900020, 'PENDING_REVIEW', NULL, NULL),
    (900022, 900001, 900020, 'PENDING_REVIEW', NULL, NULL),
    (900023, 900001, 900020, 'PUBLISHED', @perf_now - INTERVAL 1 DAY, NULL);

INSERT INTO stampbook_content (stampbook_id, content_id) VALUES
    (900020, 900020),
    (900021, 900021),
    (900022, 900022),
    (900023, 900023);

INSERT INTO stampbook_progress (
    stampbook_progress_id, stampbook_id, user_id, status, completed_at
) VALUES (
    900020, 900020, 900002, 'IN_PROGRESS', NULL
);

INSERT INTO operator_application (
    operator_application_id, applicant_user_id, requested_region_id, business_information,
    status, inspected_user_id, rejected_reason, created_at, updated_at
) VALUES (
    900020, @perf_applicant_id, 900001, 'K6 반려 신청', 'PENDING', NULL, NULL, @perf_now, @perf_now
);

INSERT INTO content_withdrawal_request (
    content_withdrawal_request_id, content_id, requested_by_user_id, idempotency_key_hash,
    status, request_reason, requested_at, reviewed_at, reviewed_by_user_id, rejection_reason,
    invalidated_at, invalidated_by_user_id, invalidation_reason
) VALUES (
    900020, 900020, 900001,
    '9000200000000000000000000000000000000000000000000000000000000000',
    'PENDING', 'K6 반려 철회 요청', @perf_now, NULL, NULL, NULL, NULL, NULL, NULL
);

INSERT INTO capacity_hold (
    hold_id, region_id, session_id, user_id, quantity, status, expires_at,
    terminal_at, invalidation_reason, capacity_released_at, created_at
) VALUES
    (900020, 900001, 900020, 900002, 1, 'ACTIVE', @perf_now + INTERVAL 30 MINUTE, NULL, NULL, NULL, @perf_now),
    (900021, 900001, 900021, 900002, 1, 'CONSUMED', @perf_now + INTERVAL 30 MINUTE, @perf_now, NULL, NULL, @perf_now),
    (900022, 900001, 900020, 900002, 1, 'CONSUMED', @perf_now + INTERVAL 30 MINUTE, @perf_now, NULL, NULL, @perf_now),
    (900023, 900001, 900021, 900002, 1, 'CONSUMED', @perf_now + INTERVAL 30 MINUTE, @perf_now, NULL, NULL, @perf_now),
    (900024, 900001, 900023, 900002, 1, 'CONSUMED', @perf_now + INTERVAL 30 MINUTE, @perf_now, NULL, NULL, @perf_now),
    (900025, 900001, 900024, 900002, 1, 'CONSUMED', @perf_now + INTERVAL 30 MINUTE, @perf_now, NULL, NULL, @perf_now);

INSERT INTO reservation (
    reservation_id, reservation_no, qr_reference, region_id, hold_id, session_id, user_id,
    status, confirmed_at, cancelled_at, cancellation_reason, expired_at, capacity_released_at, updated_at
) VALUES
    (900020, 'K6CN20260817000020', '90000000-0000-4000-8000-000000000020', 900001, 900021, 900021, 900002, 'CONFIRMED', @perf_now, NULL, NULL, NULL, NULL, @perf_now),
    (900021, 'K6PD20260817000021', '90000000-0000-4000-8000-000000000021', 900001, 900022, 900020, 900002, 'CONFIRMED', @perf_now, NULL, NULL, NULL, NULL, @perf_now),
    (900022, 'K6PD20260817000022', '90000000-0000-4000-8000-000000000022', 900001, 900023, 900021, 900002, 'CONFIRMED', @perf_now, NULL, NULL, NULL, NULL, @perf_now),
    (900023, 'K6RF20260817000023', '90000000-0000-4000-8000-000000000023', 900001, 900024, 900023, 900002, 'CONFIRMED', @perf_now, NULL, NULL, NULL, NULL, @perf_now),
    (900024, 'K6RF20260817000024', '90000000-0000-4000-8000-000000000024', 900001, 900025, 900024, 900002, 'CONFIRMED', @perf_now, NULL, NULL, NULL, NULL, @perf_now);

INSERT INTO reservation_price_snapshot (
    reservation_price_snapshot_id, hold_id, coupon_id, base_amount, discount_amount,
    final_amount, currency, created_at
) VALUES
    (900020, 900022, NULL, 10000, 0, 10000, 'KRW', @perf_now),
    (900021, 900023, NULL, 10000, 0, 10000, 'KRW', @perf_now),
    (900022, 900024, NULL, 10000, 0, 10000, 'KRW', @perf_now),
    (900023, 900025, NULL, 10000, 0, 10000, 'KRW', @perf_now),
    (900024, 900021, NULL, 10000, 0, 10000, 'KRW', @perf_now);

INSERT INTO payment (
    payment_id, hold_id, reservation_price_snapshot_id, reservation_id, order_id,
    portone_payment_id, status, finalized_at, created_at
) VALUES
    (900020, 900022, 900020, 900021, 'k6-discrepancy-900020', 'k6-portone-900020', 'DISCREPANT', @perf_now, @perf_now),
    (900021, 900023, 900021, 900022, 'k6-discrepancy-900021', 'k6-portone-900021', 'DISCREPANT', @perf_now, @perf_now),
    (900022, 900024, 900022, 900023, 'k6-refund-900020', 'k6-portone-900022', 'APPROVED', @perf_now, @perf_now),
    (900023, 900025, 900023, 900024, 'k6-refund-900021', 'k6-portone-900023', 'APPROVED', @perf_now, @perf_now),
    (900024, 900021, 900024, 900020, 'k6-cancel-900024', 'k6-portone-900024', 'APPROVED', @perf_now, @perf_now);

INSERT INTO payment_discrepancy (
    payment_discrepancy_id, payment_id, discrepancy_type, status, detected_at
) VALUES
    (900020, 900020, 'K6_FIXTURE', 'OPEN', @perf_now),
    (900021, 900021, 'K6_FIXTURE', 'OPEN', @perf_now);

INSERT INTO refund (
    refund_id, payment_id, amount, status, requested_at, completed_at, resolved_at
) VALUES
    (900020, 900022, 10000, 'FAILED', @perf_now, @perf_now, NULL),
    (900021, 900023, 10000, 'DISCREPANT', @perf_now, @perf_now, NULL),
    (900022, 900024, 10000, 'FAILED', @perf_now, @perf_now, NULL);

INSERT INTO refund_attempt (
    refund_attempt_id, refund_id, attempt_no, initiator_kind, portone_cancellation_id,
    outcome_kind, failure_reason_code, external_status, result_hash, attempted_at
) VALUES
    (900020, 900020, 1, 'SYSTEM', NULL, 'NO_RESPONSE', 'TIMEOUT', NULL, NULL, @perf_now),
    (900021, 900021, 1, 'SYSTEM', NULL, 'NO_RESPONSE', 'TIMEOUT', NULL, NULL, @perf_now),
    (900022, 900022, 1, 'SYSTEM', NULL, 'NO_RESPONSE', 'TIMEOUT', NULL, NULL, @perf_now);

INSERT INTO audit_event (
    audit_event_id, request_id, region_id, target_type, target_id,
    previous_state, next_state, result, reason_code, actor_kind, actor_role,
    occurred_at, evidence_reference, reason
) VALUES (
    900020, '90000000-0000-4000-8000-000000000020', 900001, 'RESERVATION', 900020,
    'CONFIRMED', NULL, 'FAILURE', 'QR_CHECK_IN_SIGNATURE_INVALID', 'SYSTEM', NULL,
    @perf_now, NULL, NULL
);
