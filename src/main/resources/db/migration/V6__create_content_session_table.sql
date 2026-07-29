ALTER TABLE content
    ADD CONSTRAINT uk_content_content_id_region_id UNIQUE (content_id, region_id);

CREATE TABLE content_session (
    session_id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    checkin_open_at TIMESTAMP(6) NOT NULL,
    checkin_close_at TIMESTAMP(6) NOT NULL,
    capacity INT NOT NULL,
    remaining_capacity INT NOT NULL,
    cancelled_at TIMESTAMP(6),
    cancelled_by_user_id BIGINT,
    cancellation_reason TEXT,
    completed_at TIMESTAMP(6),
    version_no INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content_session PRIMARY KEY (session_id),
    CONSTRAINT ck_content_session_status
        CHECK (status REGEXP '^(SCHEDULED|COMPLETED|CANCELLED)$'),
    CONSTRAINT ck_content_session_time_range
        CHECK (
            starts_at < ends_at
            AND checkin_open_at < checkin_close_at
            AND ends_at <= checkin_close_at
        ),
    CONSTRAINT ck_content_session_capacity
        CHECK (capacity > 0 AND remaining_capacity >= 0 AND remaining_capacity <= capacity),
    CONSTRAINT ck_content_session_cancelled
        CHECK (
            CASE
                WHEN status = 'CANCELLED' THEN
                    cancelled_at IS NOT NULL
                    AND cancelled_by_user_id IS NOT NULL
                    AND cancellation_reason IS NOT NULL
                ELSE
                    cancelled_at IS NULL
                    AND cancelled_by_user_id IS NULL
                    AND cancellation_reason IS NULL
            END
        ),
    CONSTRAINT ck_content_session_completed
        CHECK (
            CASE
                WHEN status = 'COMPLETED' THEN completed_at IS NOT NULL
                ELSE completed_at IS NULL
            END
        ),
    CONSTRAINT fk_content_session_content_region
        FOREIGN KEY (content_id, region_id) REFERENCES content (content_id, region_id),
    CONSTRAINT fk_content_session_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_content_session_cancelled_by_user
        FOREIGN KEY (cancelled_by_user_id) REFERENCES app_user (user_id)
);

CREATE INDEX idx_content_session_content_status_starts
    ON content_session (content_id, status, starts_at);

CREATE INDEX idx_content_session_region_status_starts
    ON content_session (region_id, status, starts_at);
