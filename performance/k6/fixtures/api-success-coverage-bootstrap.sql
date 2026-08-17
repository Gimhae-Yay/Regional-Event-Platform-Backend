SET @perf_platform_admin_id = 900013;
SET @perf_now = UTC_TIMESTAMP(6);
SET @perf_password_hash = '{bcrypt}$2a$12$/SenwR03QWMkim.0.mDq7uE3vB75E5egW2.A5FQPVmlBU9VEUlmm2';

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
    'k6-platform-admin@example.com',
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
