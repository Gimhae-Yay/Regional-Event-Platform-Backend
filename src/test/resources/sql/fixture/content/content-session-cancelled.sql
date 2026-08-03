INSERT INTO region (
    region_id,
    region_code,
    name,
    is_public,
    created_at,
    updated_at
) VALUES (
    101,
    'GIMHAE',
    '김해시',
    TRUE,
    TIMESTAMP '2026-08-01 00:00:00',
    TIMESTAMP '2026-08-01 00:00:00'
);

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
        102,
        'fixture-operator@example.com',
        'hashed-password',
        '운영자',
        '010-1234-5678',
        'ACTIVE',
        TIMESTAMP '2026-08-01 00:00:00',
        TIMESTAMP '2026-08-01 00:00:00'
    ),
    (
        103,
        'fixture-admin@example.com',
        'hashed-password',
        '관리자',
        '010-9876-5432',
        'ACTIVE',
        TIMESTAMP '2026-08-01 00:00:00',
        TIMESTAMP '2026-08-01 00:00:00'
    );

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
    updated_at
) VALUES (
    101,
    101,
    102,
    'EVENT_EXPERIENCE',
    'PENDING',
    0,
    '김해 가야 문화 체험',
    '김해 가야 문화를 체험하는 행사입니다.',
    '김해문화의전당',
    '매일 10:00~18:00',
    '055-1234-5678',
    '안전요원의 안내를 따라주세요.',
    '만 7세 이상',
    '편한 복장',
    '시작 하루 전까지 취소할 수 있습니다.',
    TIMESTAMP '2026-08-01 00:00:00',
    NULL,
    TIMESTAMP '2026-08-01 00:00:00',
    TIMESTAMP '2026-08-01 00:00:00'
);

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
    updated_at
) VALUES (
    1001,
    101,
    101,
    'CANCELLED',
    TIMESTAMP '2026-08-02 01:00:00',
    TIMESTAMP '2026-08-02 03:00:00',
    TIMESTAMP '2026-08-02 00:30:00',
    TIMESTAMP '2026-08-02 02:30:00',
    20,
    20,
    TIMESTAMP '2026-08-01 00:00:00',
    103,
    '기상 악화',
    NULL,
    0,
    TIMESTAMP '2026-08-01 00:00:00',
    TIMESTAMP '2026-08-01 00:00:00'
);
