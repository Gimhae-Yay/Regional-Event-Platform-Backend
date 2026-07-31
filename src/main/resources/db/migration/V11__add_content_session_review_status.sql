ALTER TABLE content_session
    ADD COLUMN reviewed_at TIMESTAMP(6);

ALTER TABLE content_session
    ADD COLUMN reviewed_by_user_id BIGINT;

ALTER TABLE content_session
    ADD COLUMN reject_reason TEXT;

ALTER TABLE content_session
    ADD CONSTRAINT fk_content_session_reviewed_by_user
        FOREIGN KEY (reviewed_by_user_id) REFERENCES app_user (user_id);

ALTER TABLE content_session
    DROP CONSTRAINT ck_content_session_status;

ALTER TABLE content_session
    ADD CONSTRAINT ck_content_session_status_v2
        CHECK (status REGEXP '^(PENDING|SCHEDULED|REJECTED|COMPLETED|CANCELLED)$');

ALTER TABLE content_session
    ADD CONSTRAINT ck_content_session_review_state
        CHECK (
            (status <> 'PENDING'
                OR (reviewed_at IS NULL
                    AND reviewed_by_user_id IS NULL
                    AND reject_reason IS NULL))
            AND (status <> 'REJECTED'
                OR (reviewed_at IS NOT NULL
                    AND reviewed_by_user_id IS NOT NULL
                    AND reject_reason IS NOT NULL
                    AND TRIM(reject_reason) <> ''))
            AND (status = 'REJECTED' OR reject_reason IS NULL)
            AND (
                (reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
                OR (reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL)
            )
        );
