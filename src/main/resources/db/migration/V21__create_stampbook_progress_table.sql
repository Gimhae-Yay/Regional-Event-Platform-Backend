CREATE TABLE stampbook_progress (
    stampbook_progress_id BIGINT NOT NULL AUTO_INCREMENT,
    stampbook_id BIGINT NOT NULL,
    user_id BIGINT,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT pk_stampbook_progress PRIMARY KEY (stampbook_progress_id),
    CONSTRAINT uk_stampbook_progress_stampbook_user UNIQUE (stampbook_id, user_id),
    CONSTRAINT fk_stampbook_progress_stampbook
        FOREIGN KEY (stampbook_id) REFERENCES stampbook (stampbook_id),
    CONSTRAINT fk_stampbook_progress_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_stampbook_progress_status
        CHECK (REGEXP_LIKE(status, '^(IN_PROGRESS|COMPLETED|ENDED_INCOMPLETE)$', 'c')),
    CONSTRAINT ck_stampbook_progress_status_completed_at
        CHECK (
            CASE
                WHEN REGEXP_LIKE(status, '^IN_PROGRESS$', 'c')
                    AND completed_at IS NULL THEN 1
                WHEN REGEXP_LIKE(status, '^COMPLETED$', 'c')
                    AND completed_at IS NOT NULL THEN 1
                WHEN REGEXP_LIKE(status, '^ENDED_INCOMPLETE$', 'c')
                    AND completed_at IS NULL THEN 1
                ELSE 0
            END = 1
        )
);
