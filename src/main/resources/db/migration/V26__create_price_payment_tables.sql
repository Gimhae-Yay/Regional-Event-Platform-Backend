CREATE TABLE reservation_price_snapshot (
    reservation_price_snapshot_id BIGINT NOT NULL AUTO_INCREMENT,
    hold_id BIGINT NOT NULL,
    coupon_id BIGINT,
    base_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    final_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_reservation_price_snapshot PRIMARY KEY (reservation_price_snapshot_id),
    CONSTRAINT uk_reservation_price_snapshot_hold UNIQUE (hold_id),
    CONSTRAINT uk_reservation_price_snapshot_id_coupon UNIQUE (reservation_price_snapshot_id, coupon_id),
    CONSTRAINT fk_reservation_price_snapshot_hold
        FOREIGN KEY (hold_id) REFERENCES capacity_hold (hold_id),
    CONSTRAINT fk_reservation_price_snapshot_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT ck_reservation_price_snapshot_amount
        CHECK (
            base_amount >= 0
            AND discount_amount >= 0
            AND base_amount - discount_amount = final_amount
            AND final_amount >= 0
        )
);

CREATE TABLE payment (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    hold_id BIGINT NOT NULL,
    reservation_price_snapshot_id BIGINT NOT NULL,
    reservation_id BIGINT,
    order_id VARCHAR(255) NOT NULL,
    portone_payment_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    finalized_at TIMESTAMP(6),
    pending_hold_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN hold_id ELSE NULL END
    ),
    CONSTRAINT pk_payment PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT uk_payment_portone_payment UNIQUE (portone_payment_id),
    CONSTRAINT uk_payment_reservation UNIQUE (reservation_id),
    CONSTRAINT uk_payment_pending_hold UNIQUE (pending_hold_id),
    CONSTRAINT fk_payment_hold
        FOREIGN KEY (hold_id) REFERENCES capacity_hold (hold_id),
    CONSTRAINT fk_payment_reservation_price_snapshot
        FOREIGN KEY (reservation_price_snapshot_id)
        REFERENCES reservation_price_snapshot (reservation_price_snapshot_id),
    CONSTRAINT fk_payment_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id),
    CONSTRAINT ck_payment_status
        CHECK (status REGEXP '^(PENDING|APPROVED|DECLINED|CANCELLED|EXPIRED|DISCREPANT)$'),
    CONSTRAINT ck_payment_finalized_at
        CHECK (
            (status = 'PENDING' AND finalized_at IS NULL)
            OR (status <> 'PENDING' AND finalized_at IS NOT NULL)
        )
);

CREATE TABLE payment_idempotency (
    payment_idempotency_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    operation VARCHAR(30) NOT NULL,
    idempotency_key_hash VARCHAR(255) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_id BIGINT,
    completed_at TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    CONSTRAINT pk_payment_idempotency PRIMARY KEY (payment_idempotency_id),
    CONSTRAINT uk_payment_idempotency_actor_operation_key
        UNIQUE (actor_user_id, operation, idempotency_key_hash),
    CONSTRAINT uk_payment_idempotency_payment UNIQUE (payment_id),
    CONSTRAINT fk_payment_idempotency_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id),
    CONSTRAINT ck_payment_idempotency_operation
        CHECK (operation = 'PAYMENT_CREATE'),
    CONSTRAINT ck_payment_idempotency_status
        CHECK (status REGEXP '^(PROCESSING|SUCCEEDED|FAILED)$'),
    CONSTRAINT ck_payment_idempotency_result
        CHECK (
            (status = 'PROCESSING' AND payment_id IS NULL AND completed_at IS NULL)
            OR (status = 'SUCCEEDED' AND payment_id IS NOT NULL AND completed_at IS NOT NULL)
            OR (status = 'FAILED' AND payment_id IS NULL AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_payment_idempotency_expires_at
    ON payment_idempotency (expires_at);

CREATE TABLE payment_verification (
    payment_verification_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    verification_reason VARCHAR(100) NOT NULL,
    observed_amount BIGINT NOT NULL,
    observed_currency VARCHAR(3) NOT NULL,
    observed_order_id VARCHAR(255) NOT NULL,
    external_status VARCHAR(100) NOT NULL,
    internal_decision VARCHAR(100) NOT NULL,
    response_hash VARCHAR(255) NOT NULL,
    verified_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_payment_verification PRIMARY KEY (payment_verification_id),
    CONSTRAINT fk_payment_verification_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id)
);

CREATE TABLE payment_webhook (
    payment_webhook_id BIGINT NOT NULL AUTO_INCREMENT,
    provider_event_id VARCHAR(255) NOT NULL,
    payment_id BIGINT,
    authentication_result VARCHAR(100) NOT NULL,
    processing_result VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(255) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_payment_webhook PRIMARY KEY (payment_webhook_id),
    CONSTRAINT uk_payment_webhook_provider_event UNIQUE (provider_event_id),
    CONSTRAINT fk_payment_webhook_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id)
);

CREATE TABLE payment_discrepancy (
    payment_discrepancy_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    discrepancy_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    detected_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_payment_discrepancy PRIMARY KEY (payment_discrepancy_id),
    CONSTRAINT uk_payment_discrepancy_payment UNIQUE (payment_id),
    CONSTRAINT fk_payment_discrepancy_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id)
);

CREATE TABLE payment_discrepancy_action (
    payment_discrepancy_action_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_discrepancy_id BIGINT NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    evidence_reference VARCHAR(255),
    reason_code VARCHAR(100) NOT NULL,
    result_code VARCHAR(100) NOT NULL,
    acted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_payment_discrepancy_action PRIMARY KEY (payment_discrepancy_action_id),
    CONSTRAINT fk_payment_discrepancy_action_discrepancy
        FOREIGN KEY (payment_discrepancy_id) REFERENCES payment_discrepancy (payment_discrepancy_id)
);
