SET @perf_scenario = COALESCE(@perf_scenario, '');
SET @perf_expected_requests = COALESCE(@perf_expected_requests, 0);

DROP TEMPORARY TABLE IF EXISTS perf_invariant_result;
CREATE TEMPORARY TABLE perf_invariant_result (
    invariant_name VARCHAR(100) NOT NULL,
    actual_value VARCHAR(255) NOT NULL,
    expected_value VARCHAR(255) NOT NULL,
    is_valid BOOLEAN NOT NULL
);

INSERT INTO perf_invariant_result
SELECT
    'supported scenario',
    @perf_scenario,
    'reservation-hold, qr-checkin, or manual-checkin',
    @perf_scenario IN ('reservation-hold', 'qr-checkin', 'manual-checkin');

INSERT INTO perf_invariant_result
SELECT
    'remaining capacity',
    CAST(COALESCE(MAX(remaining_capacity), -1) AS CHAR),
    '0',
    COALESCE(MAX(remaining_capacity), -1) = 0
FROM content_session
WHERE session_id = 900003
HAVING @perf_scenario = 'reservation-hold';

INSERT INTO perf_invariant_result
SELECT
    'active hold count',
    CAST(COUNT(*) AS CHAR),
    '1',
    COUNT(*) = 1
FROM capacity_hold
WHERE session_id = 900003
    AND status = 'ACTIVE'
HAVING @perf_scenario = 'reservation-hold';

INSERT INTO perf_invariant_result
SELECT
    'active hold quantity sum',
    CAST(COALESCE(SUM(quantity), 0) AS CHAR),
    '1',
    COALESCE(SUM(quantity), 0) = 1
FROM capacity_hold
WHERE session_id = 900003
    AND status = 'ACTIVE'
HAVING @perf_scenario = 'reservation-hold';

INSERT INTO perf_invariant_result
SELECT
    'QR visit count',
    CAST(COUNT(*) AS CHAR),
    '1',
    COUNT(*) = 1
FROM visit
WHERE reservation_id = 900001
HAVING @perf_scenario = 'qr-checkin';

INSERT INTO perf_invariant_result
SELECT
    'QR check-in method count',
    CAST(COALESCE(SUM(checkin_method = 'QR'), 0) AS CHAR),
    '1',
    COUNT(*) = 1 AND COALESCE(SUM(checkin_method = 'QR'), 0) = 1
FROM visit
WHERE reservation_id = 900001
HAVING @perf_scenario = 'qr-checkin';

INSERT INTO perf_invariant_result
SELECT
    'QR successful idempotency count',
    CAST(COALESCE(SUM(status = 'SUCCEEDED'), 0) AS CHAR),
    CAST(@perf_expected_requests AS CHAR),
    COUNT(*) = @perf_expected_requests
        AND COALESCE(SUM(status = 'SUCCEEDED'), 0) = @perf_expected_requests
FROM idempotency_record
WHERE actor_user_id = 900001
    AND operation = 'CHECK_IN'
HAVING @perf_scenario = 'qr-checkin';

INSERT INTO perf_invariant_result
SELECT
    'QR idempotency visit convergence',
    CONCAT(
        'distinct=',
        COUNT(DISTINCT result_visit_id),
        ', visit=',
        COALESCE(CAST(MIN(result_visit_id) AS CHAR), 'NULL')
    ),
    'one distinct result matching reservation visit',
    COUNT(*) = @perf_expected_requests
        AND COUNT(DISTINCT result_visit_id) = 1
        AND MIN(result_visit_id) = (
            SELECT MIN(visit_id)
            FROM visit
            WHERE reservation_id = 900001
        )
FROM idempotency_record
WHERE actor_user_id = 900001
    AND operation = 'CHECK_IN'
    AND status = 'SUCCEEDED'
HAVING @perf_scenario = 'qr-checkin';

INSERT INTO perf_invariant_result
SELECT
    'manual visit count',
    CAST(COUNT(*) AS CHAR),
    '1',
    COUNT(*) = 1
FROM visit
WHERE reservation_id = 900002
HAVING @perf_scenario = 'manual-checkin';

INSERT INTO perf_invariant_result
SELECT
    'manual check-in method count',
    CAST(COALESCE(SUM(checkin_method = 'RESERVATION_NUMBER'), 0) AS CHAR),
    '1',
    COUNT(*) = 1 AND COALESCE(SUM(checkin_method = 'RESERVATION_NUMBER'), 0) = 1
FROM visit
WHERE reservation_id = 900002
HAVING @perf_scenario = 'manual-checkin';

INSERT INTO perf_invariant_result
SELECT
    'manual successful idempotency count',
    CAST(COALESCE(SUM(status = 'SUCCEEDED'), 0) AS CHAR),
    '1',
    COALESCE(SUM(status = 'SUCCEEDED'), 0) = 1
FROM idempotency_record
WHERE actor_user_id = 900001
    AND operation = 'CHECK_IN'
HAVING @perf_scenario = 'manual-checkin';

INSERT INTO perf_invariant_result
SELECT
    'manual conflict idempotency count',
    CAST(COALESCE(SUM(status = 'FAILED' AND result_code = 'CHECK_IN_CONFLICT'), 0) AS CHAR),
    CAST(@perf_expected_requests - 1 AS CHAR),
    COUNT(*) = @perf_expected_requests
        AND COALESCE(SUM(status = 'FAILED' AND result_code = 'CHECK_IN_CONFLICT'), 0)
            = @perf_expected_requests - 1
FROM idempotency_record
WHERE actor_user_id = 900001
    AND operation = 'CHECK_IN'
HAVING @perf_scenario = 'manual-checkin';

SELECT
    invariant_name,
    actual_value,
    expected_value,
    IF(is_valid, 'PASS', 'FAIL') AS result
FROM perf_invariant_result
ORDER BY invariant_name;

DROP TEMPORARY TABLE IF EXISTS perf_invariant_assertion;
CREATE TEMPORARY TABLE perf_invariant_assertion (
    is_valid BOOLEAN NOT NULL,
    CONSTRAINT ck_perf_invariant_assertion CHECK (is_valid = TRUE)
);

INSERT INTO perf_invariant_assertion (is_valid)
SELECT COUNT(*) > 1 AND COALESCE(MIN(is_valid), FALSE)
FROM perf_invariant_result;
