SET @region_id = 900001;
SET @operator_user_id = 900001;
SET @visitor_user_id = 900002;
SET @qr_visitor_user_id = 900003;
SET @manual_visitor_user_id = 900004;
SET @admin_user_id = 900005;
SET @concurrency_visitor_04_user_id = 900006;
SET @concurrency_visitor_05_user_id = 900007;
SET @concurrency_visitor_06_user_id = 900008;
SET @concurrency_visitor_07_user_id = 900009;
SET @concurrency_visitor_08_user_id = 900010;
SET @concurrency_visitor_09_user_id = 900011;
SET @concurrency_visitor_10_user_id = 900012;
SET @image_object_id = 900001;
SET @content_id = 900001;
SET @reservation_session_id = 900001;
SET @checkin_session_id = 900002;
SET @reservation_concurrency_session_id = 900003;
SET @qr_hold_id = 900001;
SET @manual_hold_id = 900002;
SET @qr_reservation_id = 900001;
SET @manual_reservation_id = 900002;
SET @payment_create_hold_id = 900010;
SET @paid_hold_id = 900011;
SET @paid_reservation_id = 900010;
SET @paid_snapshot_id = 900010;
SET @paid_payment_id = 900010;
SET @coupon_source_hold_id = 900012;
SET @coupon_source_reservation_id = 900012;
SET @coupon_source_visit_id = 900010;
SET @coupon_policy_id = 900010;
SET @mission_reward_policy_id = 900011;
SET @mission_progress_policy_id = 900012;
SET @mission_id = 900010;
SET @mission_participation_id = 900010;
SET @mission_progress_id = 900011;
SET @mission_progress_participation_id = 900011;
SET @password_hash = '{bcrypt}$2a$12$/SenwR03QWMkim.0.mDq7uE3vB75E5egW2.A5FQPVmlBU9VEUlmm2';
SET @now = UTC_TIMESTAMP(6);

DELETE FROM payment_webhook
WHERE payment_id = @paid_payment_id OR provider_event_id LIKE 'k6-%';
DELETE FROM refund_attempt
WHERE refund_id IN (
    SELECT refund_id
    FROM refund
    WHERE payment_id = @paid_payment_id
);
DELETE FROM refund WHERE payment_id = @paid_payment_id;
DELETE FROM payment_idempotency WHERE actor_user_id = @visitor_user_id;
DELETE FROM payment WHERE payment_id = @paid_payment_id OR hold_id IN (@payment_create_hold_id, @paid_hold_id);
DELETE FROM reservation_price_snapshot
WHERE reservation_price_snapshot_id = @paid_snapshot_id
    OR hold_id IN (@payment_create_hold_id, @paid_hold_id);

DELETE FROM coupon_issuance
WHERE coupon_policy_id IN (@coupon_policy_id, @mission_reward_policy_id, @mission_progress_policy_id);
DELETE FROM coupon_status_history
WHERE coupon_id IN (
    SELECT coupon_id FROM coupon
    WHERE coupon_policy_id IN (@coupon_policy_id, @mission_reward_policy_id, @mission_progress_policy_id)
);
DELETE FROM coupon
WHERE coupon_policy_id IN (@coupon_policy_id, @mission_reward_policy_id, @mission_progress_policy_id);
DELETE FROM mission_reward_claim
WHERE mission_participation_id IN (@mission_participation_id, @mission_progress_participation_id);
DELETE FROM mission_progress
WHERE mission_participation_id IN (@mission_participation_id, @mission_progress_participation_id);
DELETE FROM mission_participation
WHERE mission_participation_id IN (@mission_participation_id, @mission_progress_participation_id);
DELETE FROM mission WHERE mission_id IN (@mission_id, @mission_progress_id);
DELETE FROM coupon_policy
WHERE coupon_policy_id IN (@coupon_policy_id, @mission_reward_policy_id, @mission_progress_policy_id);

DELETE idempotency_record
FROM idempotency_record
LEFT JOIN reservation
    ON reservation.reservation_id = idempotency_record.result_reservation_id
LEFT JOIN visit
    ON visit.visit_id = idempotency_record.result_visit_id
WHERE idempotency_record.actor_user_id IN (
        @operator_user_id,
        @visitor_user_id,
        @qr_visitor_user_id,
        @manual_visitor_user_id,
        @concurrency_visitor_04_user_id,
        @concurrency_visitor_05_user_id,
        @concurrency_visitor_06_user_id,
        @concurrency_visitor_07_user_id,
        @concurrency_visitor_08_user_id,
        @concurrency_visitor_09_user_id,
        @concurrency_visitor_10_user_id
    )
    OR reservation.session_id IN (
        @reservation_session_id,
        @checkin_session_id,
        @reservation_concurrency_session_id
    )
    OR visit.session_id IN (
        @reservation_session_id,
        @checkin_session_id,
        @reservation_concurrency_session_id
    );

DELETE review
FROM review
JOIN visit ON visit.visit_id = review.visit_id
WHERE visit.session_id IN (
    @reservation_session_id,
    @checkin_session_id,
    @reservation_concurrency_session_id
);

DELETE FROM visit
WHERE session_id IN (
    @reservation_session_id,
    @checkin_session_id,
    @reservation_concurrency_session_id
);

DELETE FROM reservation
WHERE session_id IN (
    @reservation_session_id,
    @checkin_session_id,
    @reservation_concurrency_session_id
);

DELETE FROM capacity_hold
WHERE session_id IN (
    @reservation_session_id,
    @checkin_session_id,
    @reservation_concurrency_session_id
);

INSERT INTO region (
    region_id,
    region_code,
    name,
    is_public,
    created_at,
    updated_at
) VALUES (
    @region_id,
    'K6-LOCAL-REGION',
    'K6 Local Region',
    TRUE,
    @now,
    @now
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    is_public = VALUES(is_public),
    updated_at = VALUES(updated_at);

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
    (@operator_user_id, 'k6-operator@example.com', @password_hash, 'K6 Operator', '01090000001', 'ACTIVE', @now, @now),
    (@visitor_user_id, 'k6-visitor@example.com', @password_hash, 'K6 Visitor', '01090000002', 'ACTIVE', @now, @now),
    (@qr_visitor_user_id, 'k6-qr-visitor@example.com', @password_hash, 'K6 QR Visitor', '01090000003', 'ACTIVE', @now, @now),
    (@manual_visitor_user_id, 'k6-manual-visitor@example.com', @password_hash, 'K6 Manual Visitor', '01090000004', 'ACTIVE', @now, @now),
    (@admin_user_id, 'k6-admin@example.com', @password_hash, 'K6 Admin', '01090000005', 'ACTIVE', @now, @now),
    (@concurrency_visitor_04_user_id, 'k6-concurrency-visitor-04@example.com', @password_hash, 'K6 Concurrency Visitor 04', '01090000006', 'ACTIVE', @now, @now),
    (@concurrency_visitor_05_user_id, 'k6-concurrency-visitor-05@example.com', @password_hash, 'K6 Concurrency Visitor 05', '01090000007', 'ACTIVE', @now, @now),
    (@concurrency_visitor_06_user_id, 'k6-concurrency-visitor-06@example.com', @password_hash, 'K6 Concurrency Visitor 06', '01090000008', 'ACTIVE', @now, @now),
    (@concurrency_visitor_07_user_id, 'k6-concurrency-visitor-07@example.com', @password_hash, 'K6 Concurrency Visitor 07', '01090000009', 'ACTIVE', @now, @now),
    (@concurrency_visitor_08_user_id, 'k6-concurrency-visitor-08@example.com', @password_hash, 'K6 Concurrency Visitor 08', '01090000010', 'ACTIVE', @now, @now),
    (@concurrency_visitor_09_user_id, 'k6-concurrency-visitor-09@example.com', @password_hash, 'K6 Concurrency Visitor 09', '01090000011', 'ACTIVE', @now, @now),
    (@concurrency_visitor_10_user_id, 'k6-concurrency-visitor-10@example.com', @password_hash, 'K6 Concurrency Visitor 10', '01090000012', 'ACTIVE', @now, @now)
ON DUPLICATE KEY UPDATE
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
    (@operator_user_id, 'OPERATOR', @region_id, 'ACTIVE', @now),
    (@visitor_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@qr_visitor_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@manual_visitor_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@admin_user_id, 'REGION_ADMIN', @region_id, 'ACTIVE', @now),
    (@concurrency_visitor_04_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@concurrency_visitor_05_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@concurrency_visitor_06_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@concurrency_visitor_07_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@concurrency_visitor_08_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@concurrency_visitor_09_user_id, 'VISITOR', NULL, 'ACTIVE', @now),
    (@concurrency_visitor_10_user_id, 'VISITOR', NULL, 'ACTIVE', @now)
ON DUPLICATE KEY UPDATE
    region_id = VALUES(region_id),
    status = VALUES(status),
    granted_at = VALUES(granted_at);

INSERT INTO image_object (
    image_object_id,
    object_key,
    media_type,
    byte_size,
    checksum,
    lifecycle_status,
    delete_attempt_count,
    last_delete_attempted_at,
    created_at,
    created_by_user_id,
    region_id,
    upload_expires_at,
    linked_at
) VALUES (
    @image_object_id,
    'k6/local/representative-image.jpg',
    'image/jpeg',
    12345,
    'k6-local-representative-image-checksum',
    'ACTIVE',
    0,
    NULL,
    @now,
    @operator_user_id,
    @region_id,
    @now + INTERVAL 1 DAY,
    @now
) ON DUPLICATE KEY UPDATE
    object_key = VALUES(object_key),
    media_type = VALUES(media_type),
    byte_size = VALUES(byte_size),
    checksum = VALUES(checksum),
    lifecycle_status = VALUES(lifecycle_status),
    delete_attempt_count = VALUES(delete_attempt_count),
    last_delete_attempted_at = VALUES(last_delete_attempted_at),
    created_by_user_id = VALUES(created_by_user_id),
    region_id = VALUES(region_id),
    upload_expires_at = VALUES(upload_expires_at),
    linked_at = VALUES(linked_at);

INSERT INTO content (
    content_id,
    region_id,
    operator_id,
    content_type,
    status,
    version_no,
    title,
    description,
    location_text,
    operating_hours_text,
    contact_text,
    precautions,
    age_requirement,
    materials,
    cancellation_policy_text,
    publish_at,
    deleted_at,
    created_at,
    updated_at,
    representative_image_object_id,
    representative_image_assigned_at
) VALUES (
    @content_id,
    @region_id,
    @operator_user_id,
    'EVENT_EXPERIENCE',
    'PUBLISHED',
    0,
    'K6 Local Performance Content',
    'K6 local performance seed content.',
    'K6 Test Venue',
    '09:00-18:00',
    '010-9000-0000',
    'Follow staff instructions.',
    'All ages',
    'No materials required.',
    'Free cancellation before session start.',
    @now - INTERVAL 1 DAY,
    NULL,
    @now,
    @now,
    @image_object_id,
    @now
) ON DUPLICATE KEY UPDATE
    region_id = VALUES(region_id),
    operator_id = VALUES(operator_id),
    content_type = VALUES(content_type),
    status = VALUES(status),
    version_no = VALUES(version_no),
    title = VALUES(title),
    description = VALUES(description),
    location_text = VALUES(location_text),
    operating_hours_text = VALUES(operating_hours_text),
    contact_text = VALUES(contact_text),
    precautions = VALUES(precautions),
    age_requirement = VALUES(age_requirement),
    materials = VALUES(materials),
    cancellation_policy_text = VALUES(cancellation_policy_text),
    publish_at = VALUES(publish_at),
    deleted_at = VALUES(deleted_at),
    updated_at = VALUES(updated_at),
    representative_image_object_id = VALUES(representative_image_object_id),
    representative_image_assigned_at = VALUES(representative_image_assigned_at);

INSERT INTO content_session (
    session_id,
    content_id,
    region_id,
    status,
    starts_at,
    ends_at,
    checkin_open_at,
    checkin_close_at,
    capacity,
    remaining_capacity,
    cancelled_at,
    cancelled_by_user_id,
    cancellation_reason,
    completed_at,
    version_no,
    created_at,
    updated_at,
    reviewed_at,
    reviewed_by_user_id,
    reject_reason
) VALUES
    (
        @reservation_session_id,
        @content_id,
        @region_id,
        'SCHEDULED',
        @now + INTERVAL 7 DAY,
        @now + INTERVAL 7 DAY + INTERVAL 3 HOUR,
        @now + INTERVAL 7 DAY - INTERVAL 30 MINUTE,
        @now + INTERVAL 7 DAY + INTERVAL 2 HOUR,
        10000,
        9998,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        @now,
        @now,
        @now,
        @admin_user_id,
        NULL
    ),
    (
        @checkin_session_id,
        @content_id,
        @region_id,
        'SCHEDULED',
        @now + INTERVAL 30 MINUTE,
        @now + INTERVAL 2 HOUR,
        @now - INTERVAL 30 MINUTE,
        @now + INTERVAL 1 HOUR,
        100,
        97,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        @now,
        @now,
        @now,
        @admin_user_id,
        NULL
    ),
    (
        @reservation_concurrency_session_id,
        @content_id,
        @region_id,
        'SCHEDULED',
        @now + INTERVAL 7 DAY,
        @now + INTERVAL 7 DAY + INTERVAL 3 HOUR,
        @now + INTERVAL 7 DAY - INTERVAL 30 MINUTE,
        @now + INTERVAL 7 DAY + INTERVAL 2 HOUR,
        1,
        1,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        @now,
        @now,
        @now,
        @admin_user_id,
        NULL
    )
ON DUPLICATE KEY UPDATE
    content_id = VALUES(content_id),
    region_id = VALUES(region_id),
    status = VALUES(status),
    starts_at = VALUES(starts_at),
    ends_at = VALUES(ends_at),
    checkin_open_at = VALUES(checkin_open_at),
    checkin_close_at = VALUES(checkin_close_at),
    capacity = VALUES(capacity),
    remaining_capacity = VALUES(remaining_capacity),
    cancelled_at = VALUES(cancelled_at),
    cancelled_by_user_id = VALUES(cancelled_by_user_id),
    cancellation_reason = VALUES(cancellation_reason),
    completed_at = VALUES(completed_at),
    version_no = VALUES(version_no),
    updated_at = VALUES(updated_at),
    reviewed_at = VALUES(reviewed_at),
    reviewed_by_user_id = VALUES(reviewed_by_user_id),
    reject_reason = VALUES(reject_reason);

UPDATE content
SET reservation_price = 10000
WHERE content_id = @content_id;

INSERT INTO capacity_hold (
    hold_id,
    region_id,
    session_id,
    user_id,
    quantity,
    status,
    expires_at,
    terminal_at,
    invalidation_reason,
    capacity_released_at,
    created_at
) VALUES
    (
        @qr_hold_id,
        @region_id,
        @checkin_session_id,
        @qr_visitor_user_id,
        1,
        'CONSUMED',
        @now + INTERVAL 10 MINUTE,
        @now - INTERVAL 10 MINUTE,
        NULL,
        NULL,
        @now - INTERVAL 20 MINUTE
    ),
    (
        @manual_hold_id,
        @region_id,
        @checkin_session_id,
        @manual_visitor_user_id,
        1,
        'CONSUMED',
        @now + INTERVAL 10 MINUTE,
        @now - INTERVAL 10 MINUTE,
        NULL,
        NULL,
        @now - INTERVAL 20 MINUTE
    )
ON DUPLICATE KEY UPDATE
    region_id = VALUES(region_id),
    session_id = VALUES(session_id),
    user_id = VALUES(user_id),
    quantity = VALUES(quantity),
    status = VALUES(status),
    expires_at = VALUES(expires_at),
    terminal_at = VALUES(terminal_at),
    invalidation_reason = VALUES(invalidation_reason),
    capacity_released_at = VALUES(capacity_released_at);

INSERT INTO reservation (
    reservation_id,
    reservation_no,
    qr_reference,
    region_id,
    hold_id,
    session_id,
    user_id,
    status,
    confirmed_at,
    cancelled_at,
    cancellation_reason,
    expired_at,
    capacity_released_at,
    updated_at
) VALUES
    (
        @qr_reservation_id,
        'K6QR20260806000001',
        '11111111-1111-4111-8111-111111111111',
        @region_id,
        @qr_hold_id,
        @checkin_session_id,
        @qr_visitor_user_id,
        'CONFIRMED',
        @now - INTERVAL 10 MINUTE,
        NULL,
        NULL,
        NULL,
        NULL,
        @now
    ),
    (
        @manual_reservation_id,
        'K6MN20260806000001',
        '22222222-2222-4222-8222-222222222222',
        @region_id,
        @manual_hold_id,
        @checkin_session_id,
        @manual_visitor_user_id,
        'CONFIRMED',
        @now - INTERVAL 10 MINUTE,
        NULL,
        NULL,
        NULL,
        NULL,
        @now
    )
ON DUPLICATE KEY UPDATE
    reservation_no = VALUES(reservation_no),
    qr_reference = VALUES(qr_reference),
    region_id = VALUES(region_id),
    hold_id = VALUES(hold_id),
    session_id = VALUES(session_id),
    user_id = VALUES(user_id),
    status = VALUES(status),
    confirmed_at = VALUES(confirmed_at),
    cancelled_at = VALUES(cancelled_at),
    cancellation_reason = VALUES(cancellation_reason),
    expired_at = VALUES(expired_at),
    capacity_released_at = VALUES(capacity_released_at),
    updated_at = VALUES(updated_at);

INSERT INTO capacity_hold (
    hold_id, region_id, session_id, user_id, quantity, status,
    expires_at, terminal_at, invalidation_reason, capacity_released_at, created_at
) VALUES (
    @payment_create_hold_id, @region_id, @reservation_session_id, @visitor_user_id, 1, 'ACTIVE',
    @now + INTERVAL 30 MINUTE, NULL, NULL, NULL, @now
), (
    @paid_hold_id, @region_id, @reservation_session_id, @visitor_user_id, 1, 'CONSUMED',
    @now + INTERVAL 30 MINUTE, @now - INTERVAL 10 MINUTE, NULL, NULL, @now - INTERVAL 20 MINUTE
), (
    @coupon_source_hold_id, @region_id, @checkin_session_id, @visitor_user_id, 1, 'CONSUMED',
    @now + INTERVAL 30 MINUTE, @now - INTERVAL 10 MINUTE, NULL, NULL, @now - INTERVAL 20 MINUTE
);

INSERT INTO reservation (
    reservation_id, reservation_no, qr_reference, region_id, hold_id, session_id, user_id,
    status, confirmed_at, cancelled_at, cancellation_reason, expired_at, capacity_released_at, updated_at
) VALUES (
    @paid_reservation_id, 'K6PY20260813000001', '90000000-0000-4000-8000-000000000010',
    @region_id, @paid_hold_id, @reservation_session_id, @visitor_user_id,
    'CONFIRMED', @now - INTERVAL 10 MINUTE, NULL, NULL, NULL, NULL, @now
), (
    @coupon_source_reservation_id, 'K6CP20260813000001', '90000000-0000-4000-8000-000000000012',
    @region_id, @coupon_source_hold_id, @checkin_session_id, @visitor_user_id,
    'CHECKED_IN', @now - INTERVAL 1 HOUR, NULL, NULL, NULL, NULL, @now
);

INSERT INTO reservation_price_snapshot (
    reservation_price_snapshot_id, hold_id, coupon_id,
    base_amount, discount_amount, final_amount, currency, created_at
) VALUES (
    @paid_snapshot_id, @paid_hold_id, NULL, 10000, 0, 10000, 'KRW', @now
);

INSERT INTO payment (
    payment_id, hold_id, reservation_price_snapshot_id, reservation_id,
    order_id, portone_payment_id, status, finalized_at, created_at
) VALUES (
    @paid_payment_id, @paid_hold_id, @paid_snapshot_id, @paid_reservation_id,
    'k6-order-900010', 'k6-portone-payment-900010', 'APPROVED', @now, @now
);

INSERT INTO visit (
    visit_id, region_id, reservation_id, user_id, content_id, session_id,
    checked_in_by_user_id, checkin_method, checked_at, author_unlinked_at
) VALUES (
    @coupon_source_visit_id, @region_id, @coupon_source_reservation_id, @visitor_user_id,
    @content_id, @checkin_session_id, @operator_user_id, 'QR', @now - INTERVAL 30 MINUTE, NULL
);

INSERT INTO coupon_policy (
    coupon_policy_id, content_id, region_id, name, description, issuance_type,
    discount_amount, minimum_payment_amount, valid_days,
    issue_starts_at, issue_ends_at, total_issue_limit, issued_count,
    status, published_at, ended_at, updated_at
) VALUES
    (
        @coupon_policy_id, @content_id, @region_id, 'K6 Visit Coupon', 'K6 visit concurrency policy', 'VISIT',
        1000, 10000, 30, @now - INTERVAL 1 DAY, @now + INTERVAL 30 DAY,
        100, 0, 'PUBLISHED', @now - INTERVAL 1 DAY, NULL, @now
    ),
    (
        @mission_reward_policy_id, @content_id, @region_id, 'K6 Mission Reward', 'K6 mission reward policy', 'MISSION_REWARD',
        2000, 10000, 30, @now - INTERVAL 1 DAY, @now + INTERVAL 30 DAY,
        100, 0, 'PUBLISHED', @now - INTERVAL 1 DAY, NULL, @now
    ),
    (
        @mission_progress_policy_id, @content_id, @region_id, 'K6 Progress Reward', 'K6 progress reward policy', 'MISSION_REWARD',
        2000, 10000, 30, @now - INTERVAL 1 DAY, @now + INTERVAL 30 DAY,
        100, 0, 'PUBLISHED', @now - INTERVAL 1 DAY, NULL, @now
    );

INSERT INTO mission (
    mission_id, region_id, condition_type, required_visit_count,
    reward_coupon_policy_id, status, ends_at, published_at, ended_at
) VALUES (
    @mission_id, @region_id, 'VISIT_COUNT', 1,
    @mission_reward_policy_id, 'PUBLISHED', @now + INTERVAL 30 DAY, @now - INTERVAL 1 DAY, NULL
), (
    @mission_progress_id, @region_id, 'VISIT_COUNT', 1,
    @mission_progress_policy_id, 'PUBLISHED', @now + INTERVAL 30 DAY, @now - INTERVAL 1 DAY, NULL
);

INSERT INTO mission_participation (
    mission_participation_id, mission_id, user_id, status, joined_at, completed_at
) VALUES (
    @mission_participation_id, @mission_id, @visitor_user_id,
    'COMPLETED', @now - INTERVAL 2 DAY, @now - INTERVAL 1 HOUR
), (
    @mission_progress_participation_id, @mission_progress_id, @qr_visitor_user_id,
    'IN_PROGRESS', @now - INTERVAL 2 DAY, NULL
);

SELECT
    @region_id AS perf_region_id,
    @image_object_id AS perf_image_object_id,
    @content_id AS perf_content_id,
    @reservation_session_id AS perf_session_id,
    @reservation_concurrency_session_id AS perf_concurrency_session_id,
    @qr_reservation_id AS perf_qr_reservation_id,
    @manual_reservation_id AS perf_manual_reservation_id,
    @payment_create_hold_id AS perf_payment_hold_id,
    @paid_reservation_id AS perf_paid_reservation_id,
    @coupon_policy_id AS perf_coupon_policy_id,
    @coupon_source_visit_id AS perf_coupon_source_visit_id,
    @mission_id AS perf_mission_id,
    @mission_participation_id AS perf_mission_participation_id,
    @mission_progress_participation_id AS perf_mission_progress_participation_id,
    'k6-visitor@example.com' AS perf_user_email,
    'k6-qr-visitor@example.com' AS perf_qr_user_email,
    'k6-manual-visitor@example.com' AS perf_manual_user_email,
    CONCAT_WS(
        ',',
        'k6-visitor@example.com',
        'k6-qr-visitor@example.com',
        'k6-manual-visitor@example.com',
        'k6-concurrency-visitor-04@example.com',
        'k6-concurrency-visitor-05@example.com',
        'k6-concurrency-visitor-06@example.com',
        'k6-concurrency-visitor-07@example.com',
        'k6-concurrency-visitor-08@example.com',
        'k6-concurrency-visitor-09@example.com',
        'k6-concurrency-visitor-10@example.com'
    ) AS perf_concurrency_user_emails,
    'K6MN20260806000001' AS perf_manual_reservation_no,
    'Password1!' AS perf_user_password;
