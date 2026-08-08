CREATE TABLE user_role_assignment_v2 (
    role_assignment_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    role VARCHAR(30) NOT NULL,
    region_id BIGINT,
    status VARCHAR(30) NOT NULL,
    granted_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    revoke_reason_code VARCHAR(100),
    active_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN user_id ELSE NULL END
    ),
    CONSTRAINT pk_user_role_assignment_v2 PRIMARY KEY (role_assignment_id),
    CONSTRAINT fk_user_role_assignment_v2_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_user_role_assignment_v2_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_user_role_assignment_v2_role
        CHECK (role REGEXP '^(VISITOR|OPERATOR|REGION_ADMIN)$'),
    CONSTRAINT ck_user_role_assignment_v2_status
        CHECK (status REGEXP '^(ACTIVE|REVOKED)$'),
    CONSTRAINT ck_user_role_assignment_v2_region
        CHECK (
            (role = 'VISITOR' AND region_id IS NULL)
            OR (role <> 'VISITOR' AND region_id IS NOT NULL)
        ),
    CONSTRAINT ck_user_role_assignment_v2_revocation
        CHECK (
            (status = 'ACTIVE' AND revoked_at IS NULL AND revoke_reason_code IS NULL)
            OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoke_reason_code IS NOT NULL)
        )
);

INSERT INTO user_role_assignment_v2 (
    user_id,
    role,
    region_id,
    status,
    granted_at,
    revoked_at,
    revoke_reason_code
)
SELECT
    user_id,
    role,
    region_id,
    'ACTIVE',
    granted_at,
    NULL,
    NULL
FROM user_role_assignment;

DROP TABLE user_role_assignment;

ALTER TABLE user_role_assignment_v2 RENAME TO user_role_assignment;

CREATE UNIQUE INDEX uk_user_role_assignment_active_user_role
    ON user_role_assignment (active_user_id, role);

CREATE INDEX idx_user_role_assignment_region_status
    ON user_role_assignment (region_id, status);
