-- P1 HTTP 시나리오 전용 데이터를 먼저 제거한다. 이후 P0 시드를 재적용할 수 있어야 한다.
START TRANSACTION;

DELETE FROM coupon_policy_update_history
WHERE coupon_policy_id >= 900101;
DELETE FROM coupon_redemption
WHERE coupon_id >= 900701
    OR reservation_price_snapshot_id >= 951001
    OR reservation_id >= 931001;
DELETE FROM refund_attempt
WHERE refund_id >= 981001;
DELETE FROM refund
WHERE refund_id >= 981001;
DELETE FROM payment_idempotency
WHERE payment_id >= 961001
    OR reservation_id >= 931001;
DELETE FROM payment_discrepancy_action
WHERE payment_discrepancy_id >= 971001;
DELETE FROM payment_discrepancy
WHERE payment_discrepancy_id >= 971001;
DELETE FROM payment_verification
WHERE payment_id >= 961001;
DELETE FROM payment_webhook
WHERE payment_id >= 961001;
DELETE FROM payment
WHERE payment_id >= 961001;
DELETE FROM reservation_price_snapshot
WHERE reservation_price_snapshot_id >= 951001;
DELETE FROM coupon_status_history
WHERE coupon_id >= 900701;
DELETE FROM coupon_issuance
WHERE coupon_id >= 900701;
DELETE FROM coupon
WHERE coupon_id >= 900701;
DELETE FROM mission_reward_claim
WHERE mission_participation_id >= 900601;
DELETE FROM mission_progress
WHERE mission_participation_id >= 900601;
DELETE FROM mission_participation
WHERE mission_participation_id >= 900601;
DELETE FROM stamp_earn
WHERE stampbook_progress_id >= 900301;
DELETE FROM stampbook_reward_grant
WHERE stampbook_progress_id >= 900301;
DELETE FROM stampbook_progress
WHERE stampbook_progress_id >= 900301;
DELETE FROM stampbook_content
WHERE stampbook_id >= 900201;
DELETE FROM stampbook
WHERE stampbook_id >= 900201;
DELETE FROM mission_target_content
WHERE mission_id >= 900501;
DELETE FROM mission
WHERE mission_id >= 900501;
DELETE FROM coupon_policy
WHERE coupon_policy_id >= 900101;

DELETE FROM visit
WHERE session_id IN (911001, 911002, 911003);
DELETE FROM reservation
WHERE session_id IN (911001, 911002, 911003);
DELETE FROM capacity_hold
WHERE session_id IN (911001, 911002, 911003);
DELETE FROM content_session
WHERE session_id IN (911001, 911002, 911003);

DELETE FROM audit_event_actor_link
WHERE audit_event_id IN (
    SELECT audit_event_id
    FROM audit_event
    WHERE region_id IN (900001, 900002)
        OR region_id IN (
            SELECT region_id
            FROM region
            WHERE region_code LIKE 'P1HTTP%'
        )
        OR target_type IN (
            'PLATFORM_ADMIN_ASSIGNMENT',
            'USER_ROLE_ASSIGNMENT',
            'STAMPBOOK',
            'MISSION',
            'COUPON_POLICY',
            'COUPON',
            'RESERVATION_PRICE_SNAPSHOT',
            'PAYMENT',
            'REFUND',
            'PAYMENT_DISCREPANCY'
        )
);
DELETE FROM audit_event
WHERE region_id IN (900001, 900002)
    OR region_id IN (
        SELECT region_id
        FROM region
        WHERE region_code LIKE 'P1HTTP%'
    )
    OR target_type IN (
        'PLATFORM_ADMIN_ASSIGNMENT',
        'USER_ROLE_ASSIGNMENT',
        'STAMPBOOK',
        'MISSION',
        'COUPON_POLICY',
        'COUPON',
        'RESERVATION_PRICE_SNAPSHOT',
        'PAYMENT',
        'REFUND',
        'PAYMENT_DISCREPANCY'
    );

DELETE FROM platform_admin_assignment
WHERE user_id IN (10, 11)
    OR user_id IN (
        SELECT user_id
        FROM app_user
        WHERE login_identifier LIKE 'p1-admin-%@example.com'
    );
DELETE FROM user_role_assignment
WHERE user_id IN (10, 11, 12);
DELETE FROM app_user
WHERE login_identifier LIKE 'p1-admin-%@example.com';
DELETE FROM region
WHERE region_code LIKE 'P1HTTP%';

COMMIT;
