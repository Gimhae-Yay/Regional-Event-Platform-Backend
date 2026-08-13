SET @perf_scenario = COALESCE(@perf_scenario, '');

DROP TEMPORARY TABLE IF EXISTS perf_new_invariant_result;
CREATE TEMPORARY TABLE perf_new_invariant_result (
    invariant_name VARCHAR(120) NOT NULL,
    actual_value VARCHAR(255) NOT NULL,
    expected_value VARCHAR(255) NOT NULL,
    is_valid BOOLEAN NOT NULL
);

INSERT INTO perf_new_invariant_result
SELECT 'supported scenario', @perf_scenario,
       'payment, paid-cancel, reward-claim, coupon-issue, webhook, mission-progress, or mission-readonly',
       @perf_scenario IN ('payment', 'paid-cancel', 'reward-claim', 'coupon-issue', 'webhook', 'mission-progress', 'mission-readonly');

INSERT INTO perf_new_invariant_result
SELECT 'payment idempotency result', CONCAT(COUNT(*), ':', COALESCE(MAX(status), 'NONE')),
       '1:SUCCEEDED', COUNT(*) = 1 AND MAX(status) = 'SUCCEEDED'
FROM payment_idempotency
WHERE actor_user_id = 900002 AND operation = 'PAYMENT_CREATE'
HAVING @perf_scenario = 'payment';

INSERT INTO perf_new_invariant_result
SELECT 'payment single domain result', CONCAT('payment=', COUNT(DISTINCT payment_id), ',reservation=', COUNT(DISTINCT reservation_id)),
       'exactly one linked result', COUNT(*) = 1 AND (COUNT(payment_id) + COUNT(reservation_id)) = 1
FROM payment_idempotency
WHERE actor_user_id = 900002 AND operation = 'PAYMENT_CREATE'
HAVING @perf_scenario = 'payment';

INSERT INTO perf_new_invariant_result
SELECT 'payment hold snapshot convergence', CONCAT(
           'payments=', (SELECT COUNT(*) FROM payment WHERE hold_id = 900010),
           ',snapshots=', (SELECT COUNT(*) FROM reservation_price_snapshot WHERE hold_id = 900010)
       ),
       'payments=1,snapshots=1',
       (SELECT COUNT(*) FROM payment WHERE hold_id = 900010) = 1
           AND (SELECT COUNT(*) FROM reservation_price_snapshot WHERE hold_id = 900010) = 1
HAVING @perf_scenario = 'payment';

INSERT INTO perf_new_invariant_result
SELECT 'paid cancellation state', CONCAT(status, ':', capacity_released_at IS NOT NULL),
       'CANCELLED:true', status = 'CANCELLED' AND capacity_released_at IS NOT NULL
FROM reservation WHERE reservation_id = 900010
HAVING @perf_scenario = 'paid-cancel';

INSERT INTO perf_new_invariant_result
SELECT 'paid cancellation capacity restoration', CAST(remaining_capacity AS CHAR), '9999', remaining_capacity = 9999
FROM content_session WHERE session_id = 900001
HAVING @perf_scenario = 'paid-cancel';

INSERT INTO perf_new_invariant_result
SELECT 'paid cancellation refund', CONCAT(COUNT(*), ':', COALESCE(MAX(status), 'NONE')),
       '1 terminal refund', COUNT(*) = 1 AND MAX(status) IN ('SUCCEEDED', 'FAILED', 'DISCREPANT')
FROM refund WHERE payment_id = 900010
HAVING @perf_scenario = 'paid-cancel';

INSERT INTO perf_new_invariant_result
SELECT 'mission reward convergence', CONCAT('claims=', COUNT(DISTINCT mrc.mission_reward_claim_id), ',coupons=', COUNT(DISTINCT ci.coupon_id)),
       'claims=1,coupons=1', COUNT(DISTINCT mrc.mission_reward_claim_id) = 1 AND COUNT(DISTINCT ci.coupon_id) = 1
FROM mission_reward_claim mrc
LEFT JOIN coupon_issuance ci ON ci.mission_reward_claim_id = mrc.mission_reward_claim_id
WHERE mrc.mission_participation_id = 900010
HAVING @perf_scenario = 'reward-claim';

INSERT INTO perf_new_invariant_result
SELECT 'mission reward policy count', CAST(issued_count AS CHAR), '1', issued_count = 1
FROM coupon_policy WHERE coupon_policy_id = 900011
HAVING @perf_scenario = 'reward-claim';

INSERT INTO perf_new_invariant_result
SELECT 'coupon issue convergence', CONCAT('issuances=', COUNT(*), ',coupons=', COUNT(DISTINCT coupon_id)),
       'issuances=1,coupons=1', COUNT(*) = 1 AND COUNT(DISTINCT coupon_id) = 1
FROM coupon_issuance WHERE coupon_policy_id = 900010
HAVING @perf_scenario = 'coupon-issue';

INSERT INTO perf_new_invariant_result
SELECT 'coupon issue policy count', CAST(issued_count AS CHAR), '1', issued_count = 1
FROM coupon_policy WHERE coupon_policy_id = 900010
HAVING @perf_scenario = 'coupon-issue';

INSERT INTO perf_new_invariant_result
SELECT 'webhook duplicate convergence', CAST(COUNT(*) AS CHAR), '1', COUNT(*) = 1
FROM payment_webhook WHERE provider_event_id = @perf_webhook_id
HAVING @perf_scenario = 'webhook';

INSERT INTO perf_new_invariant_result
SELECT 'mission progress visit convergence', CONCAT('visits=', COUNT(*), ',progress=', (
           SELECT COUNT(*) FROM mission_progress WHERE mission_participation_id = 900011
       )),
       'visits=1,progress=1', COUNT(*) = 1 AND (
           SELECT COUNT(*) FROM mission_progress WHERE mission_participation_id = 900011
       ) = 1
FROM visit WHERE reservation_id = 900001
HAVING @perf_scenario = 'mission-progress';

INSERT INTO perf_new_invariant_result
SELECT 'mission progress completion', status, 'COMPLETED', status = 'COMPLETED'
FROM mission_participation WHERE mission_participation_id = 900011
HAVING @perf_scenario = 'mission-progress';

INSERT INTO perf_new_invariant_result
SELECT 'public mission fixture', CONCAT(status, ':', region_id), 'PUBLISHED:900001',
       status = 'PUBLISHED' AND region_id = 900001
FROM mission WHERE mission_id = 900010
HAVING @perf_scenario = 'mission-readonly';

SELECT invariant_name, actual_value, expected_value,
       IF(is_valid, 'PASS', 'FAIL') AS result
FROM perf_new_invariant_result
ORDER BY invariant_name;

DROP TEMPORARY TABLE IF EXISTS perf_new_invariant_assertion;
CREATE TEMPORARY TABLE perf_new_invariant_assertion (
    is_valid BOOLEAN NOT NULL,
    CONSTRAINT ck_perf_new_invariant_assertion CHECK (is_valid = TRUE)
);

INSERT INTO perf_new_invariant_assertion (is_valid)
SELECT COUNT(*) > 1 AND COALESCE(MIN(is_valid), FALSE)
FROM perf_new_invariant_result;
