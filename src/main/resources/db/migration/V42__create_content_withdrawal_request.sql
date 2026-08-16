CREATE TABLE content_withdrawal_request (
    content_withdrawal_request_id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    requested_by_user_id BIGINT,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_reason TEXT NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    reviewed_by_user_id BIGINT,
    rejection_reason TEXT,
    invalidated_at TIMESTAMP(6),
    invalidated_by_user_id BIGINT,
    invalidation_reason VARCHAR(30),
    active_request_content_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN content_id ELSE NULL END
    ),
    CONSTRAINT pk_content_withdrawal_request
        PRIMARY KEY (content_withdrawal_request_id),
    CONSTRAINT uk_content_withdrawal_request_content_key
        UNIQUE (content_id, idempotency_key_hash),
    CONSTRAINT uk_content_withdrawal_request_active_content
        UNIQUE (active_request_content_id),
    CONSTRAINT fk_content_withdrawal_request_content
        FOREIGN KEY (content_id) REFERENCES content (content_id),
    CONSTRAINT fk_content_withdrawal_request_requester
        FOREIGN KEY (requested_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_content_withdrawal_request_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_content_withdrawal_request_invalidator
        FOREIGN KEY (invalidated_by_user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_content_withdrawal_request_key_hash
        CHECK (idempotency_key_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_content_withdrawal_request_reason
        CHECK (CHAR_LENGTH(TRIM(request_reason)) > 0),
    CONSTRAINT ck_content_withdrawal_request_status
        CHECK (status REGEXP '^(PENDING|APPROVED|REJECTED|INVALIDATED)$'),
    CONSTRAINT ck_content_withdrawal_request_invalidation_reason
        CHECK (
            invalidation_reason IS NULL
            OR invalidation_reason REGEXP '^(CONTENT_SUSPENDED|CONTENT_ENDED)$'
        ),
    CONSTRAINT ck_content_withdrawal_request_pending_fields
        CHECK (status <> 'PENDING' OR (
            reviewed_at IS NULL
            AND reviewed_by_user_id IS NULL
            AND rejection_reason IS NULL
            AND invalidated_at IS NULL
            AND invalidated_by_user_id IS NULL
            AND invalidation_reason IS NULL
        )),
    CONSTRAINT ck_content_withdrawal_request_approved_fields
        CHECK (status <> 'APPROVED' OR (
            reviewed_at IS NOT NULL
            AND rejection_reason IS NULL
            AND invalidated_at IS NULL
            AND invalidated_by_user_id IS NULL
            AND invalidation_reason IS NULL
        )),
    CONSTRAINT ck_content_withdrawal_request_rejected_fields
        CHECK (status <> 'REJECTED' OR (
            reviewed_at IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(rejection_reason)) > 0
            AND invalidated_at IS NULL
            AND invalidated_by_user_id IS NULL
            AND invalidation_reason IS NULL
        )),
    CONSTRAINT ck_content_withdrawal_request_invalidated_fields
        CHECK (status <> 'INVALIDATED' OR (
            reviewed_at IS NULL
            AND reviewed_by_user_id IS NULL
            AND rejection_reason IS NULL
            AND invalidated_at IS NOT NULL
            AND invalidation_reason IS NOT NULL
        ))
);

CREATE INDEX idx_content_withdrawal_request_history
    ON content_withdrawal_request (
        content_id,
        requested_at,
        content_withdrawal_request_id
    );

ALTER TABLE audit_event
    DROP CONSTRAINT ck_audit_event_target_type;

ALTER TABLE audit_event
    ADD CONSTRAINT ck_audit_event_target_type
        CHECK (
            target_type REGEXP '^(REGION|OPERATOR_APPLICATION|CONTENT|CONTENT_SESSION|CONTENT_WITHDRAWAL_REQUEST|CAPACITY_HOLD|RESERVATION|VISIT|REVIEW|PLATFORM_ADMIN_ASSIGNMENT|USER_ROLE_ASSIGNMENT|STAMPBOOK|MISSION|COUPON_POLICY|COUPON|RESERVATION_PRICE_SNAPSHOT|PAYMENT|REFUND|PAYMENT_DISCREPANCY)$'
        );
