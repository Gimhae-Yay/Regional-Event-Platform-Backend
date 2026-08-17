ALTER TABLE app_user
    ADD COLUMN account_kind VARCHAR(30) NOT NULL DEFAULT 'ORDINARY';

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_account_kind
        CHECK (account_kind REGEXP '^(ORDINARY|PRIVILEGED)$');

CREATE TABLE platform_admin_assignment (
    platform_admin_assignment_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    grade VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    granted_at TIMESTAMP(6) NOT NULL,
    inactivated_at TIMESTAMP(6),
    inactive_reason_code VARCHAR(255),
    CONSTRAINT pk_platform_admin_assignment PRIMARY KEY (platform_admin_assignment_id),
    CONSTRAINT uk_platform_admin_assignment_user UNIQUE (user_id),
    CONSTRAINT fk_platform_admin_assignment_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_platform_admin_assignment_grade
        CHECK (grade REGEXP '^(SUPER_ADMIN|PLATFORM_ADMIN)$'),
    CONSTRAINT ck_platform_admin_assignment_status
        CHECK (status REGEXP '^(ACTIVE|INACTIVE)$'),
    CONSTRAINT ck_platform_admin_assignment_inactivation
        CHECK (
            CASE
                WHEN status = 'ACTIVE'
                    AND inactivated_at IS NULL
                    AND inactive_reason_code IS NULL THEN 1
                WHEN status = 'INACTIVE'
                    AND inactivated_at IS NOT NULL
                    AND inactive_reason_code IS NOT NULL THEN 1
                ELSE 0
            END = 1
        )
);

CREATE INDEX idx_platform_admin_assignment_user_status
    ON platform_admin_assignment (user_id, status);
