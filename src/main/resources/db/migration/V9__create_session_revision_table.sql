CREATE TABLE session_revision (
    session_revision_id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    target_session_id BIGINT NOT NULL,
    base_session_version INT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    checkin_open_at TIMESTAMP(6) NOT NULL,
    checkin_close_at TIMESTAMP(6) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    reviewed_by_user_id BIGINT,
    reject_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_session_revision PRIMARY KEY (session_revision_id),
    CONSTRAINT fk_session_revision_content_region
        FOREIGN KEY (content_id, region_id) REFERENCES content (content_id, region_id),
    CONSTRAINT fk_session_revision_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_session_revision_target_session_content_region
        FOREIGN KEY (target_session_id, content_id, region_id)
        REFERENCES content_session (session_id, content_id, region_id),
    CONSTRAINT fk_session_revision_requested_by_user
        FOREIGN KEY (requested_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_session_revision_reviewed_by_user
        FOREIGN KEY (reviewed_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_session_revision_time_range
        CHECK (
            starts_at < ends_at
            AND checkin_open_at < checkin_close_at
            AND ends_at > checkin_close_at
        ),
    CONSTRAINT ck_session_revision_capacity CHECK (capacity > 0),
    CONSTRAINT ck_session_revision_status CHECK (status REGEXP '^(PENDING|APPROVED|REJECTED)$'),
    CONSTRAINT ck_session_revision_review_state
        CHECK (
            (status = 'PENDING'
                AND reviewed_at IS NULL
                AND reviewed_by_user_id IS NULL
                AND reject_reason IS NULL)
            OR (status = 'APPROVED'
                AND reviewed_at IS NOT NULL
                AND reviewed_by_user_id IS NOT NULL
                AND reject_reason IS NULL)
            OR (status = 'REJECTED'
                AND reviewed_at IS NOT NULL
                AND reviewed_by_user_id IS NOT NULL
                AND reject_reason IS NOT NULL
                AND TRIM(reject_reason) <> '')
        )
);

CREATE INDEX idx_session_revision_target_session_status
    ON session_revision (target_session_id, status);
