CREATE TABLE audit_event (
    audit_event_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(36) NOT NULL,
    region_id BIGINT,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT,
    previous_state VARCHAR(30),
    next_state VARCHAR(30),
    result VARCHAR(10) NOT NULL,
    reason_code VARCHAR(100),
    actor_kind VARCHAR(30) NOT NULL,
    actor_role VARCHAR(30),
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (audit_event_id),
    CONSTRAINT fk_audit_event_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_audit_event_result CHECK (
        CASE result
            WHEN 'SUCCESS' THEN TRUE
            WHEN 'FAILURE' THEN TRUE
            ELSE FALSE
        END
    ),
    CONSTRAINT ck_audit_event_target_type CHECK (
        CASE target_type
            WHEN 'REGION' THEN TRUE
            WHEN 'OPERATOR_APPLICATION' THEN TRUE
            WHEN 'CONTENT' THEN TRUE
            WHEN 'CONTENT_SESSION' THEN TRUE
            WHEN 'RESERVATION' THEN TRUE
            WHEN 'VISIT' THEN TRUE
            WHEN 'REVIEW' THEN TRUE
            ELSE FALSE
        END
    )
);

CREATE INDEX idx_audit_event_region_id_occurred_at
    ON audit_event (region_id, occurred_at);

CREATE INDEX idx_audit_event_target_type_target_id_occurred_at
    ON audit_event (target_type, target_id, occurred_at);

CREATE INDEX idx_audit_event_occurred_at
    ON audit_event (occurred_at);
