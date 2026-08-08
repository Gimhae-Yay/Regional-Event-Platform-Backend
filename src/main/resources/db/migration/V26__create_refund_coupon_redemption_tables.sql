CREATE TABLE refund (
    refund_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT pk_refund PRIMARY KEY (refund_id),
    CONSTRAINT uk_refund_payment UNIQUE (payment_id),
    CONSTRAINT fk_refund_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id),
    CONSTRAINT ck_refund_amount CHECK (amount >= 0),
    CONSTRAINT ck_refund_status
        CHECK (status REGEXP '^(REQUESTED|PROCESSING|SUCCEEDED|FAILED|DISCREPANT)$')
);

CREATE TABLE refund_attempt (
    refund_attempt_id BIGINT NOT NULL AUTO_INCREMENT,
    refund_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    initiator_kind VARCHAR(30) NOT NULL,
    portone_cancellation_id VARCHAR(255),
    outcome_kind VARCHAR(30) NOT NULL,
    failure_reason_code VARCHAR(100),
    external_status VARCHAR(100),
    result_hash VARCHAR(255),
    attempted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_refund_attempt PRIMARY KEY (refund_attempt_id),
    CONSTRAINT uk_refund_attempt_refund_no UNIQUE (refund_id, attempt_no),
    CONSTRAINT fk_refund_attempt_refund
        FOREIGN KEY (refund_id) REFERENCES refund (refund_id),
    CONSTRAINT ck_refund_attempt_no CHECK (attempt_no BETWEEN 1 AND 3),
    CONSTRAINT ck_refund_attempt_initiator_kind
        CHECK (initiator_kind REGEXP '^(SYSTEM|SUPER_ADMIN|PLATFORM_ADMIN)$'),
    CONSTRAINT ck_refund_attempt_outcome_kind
        CHECK (outcome_kind REGEXP '^(PENDING|RESPONDED|NO_RESPONSE)$'),
    CONSTRAINT ck_refund_attempt_failure_reason_code
        CHECK (
            failure_reason_code IS NULL
            OR failure_reason_code REGEXP '^(TIMEOUT|CONNECTION|NETWORK|PROCESS_INTERRUPTED|UNKNOWN)$'
        ),
    CONSTRAINT ck_refund_attempt_outcome_values
        CHECK (
            (outcome_kind = 'PENDING'
                AND failure_reason_code IS NULL
                AND external_status IS NULL
                AND result_hash IS NULL)
            OR (outcome_kind = 'RESPONDED'
                AND failure_reason_code IS NULL
                AND external_status IS NOT NULL
                AND result_hash IS NOT NULL)
            OR (outcome_kind = 'NO_RESPONSE'
                AND failure_reason_code IS NOT NULL
                AND external_status IS NULL
                AND result_hash IS NULL)
        )
);

CREATE INDEX idx_refund_attempt_outcome_attempted_at
    ON refund_attempt (outcome_kind, attempted_at);

CREATE TABLE coupon_redemption (
    coupon_redemption_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    reservation_price_snapshot_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    redeemed_at TIMESTAMP(6) NOT NULL,
    reversed_at TIMESTAMP(6),
    confirmed_coupon_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'CONFIRMED' THEN coupon_id ELSE NULL END
    ),
    CONSTRAINT pk_coupon_redemption PRIMARY KEY (coupon_redemption_id),
    CONSTRAINT uk_coupon_redemption_reservation UNIQUE (reservation_id),
    CONSTRAINT uk_coupon_redemption_snapshot UNIQUE (reservation_price_snapshot_id),
    CONSTRAINT uk_coupon_redemption_confirmed_coupon UNIQUE (confirmed_coupon_id),
    CONSTRAINT fk_coupon_redemption_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT fk_coupon_redemption_snapshot_coupon
        FOREIGN KEY (reservation_price_snapshot_id, coupon_id)
        REFERENCES reservation_price_snapshot (reservation_price_snapshot_id, coupon_id),
    CONSTRAINT fk_coupon_redemption_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id),
    CONSTRAINT ck_coupon_redemption_status
        CHECK (status REGEXP '^(CONFIRMED|REVERSED)$'),
    CONSTRAINT ck_coupon_redemption_reversed_at
        CHECK (
            (status = 'CONFIRMED' AND reversed_at IS NULL)
            OR (status = 'REVERSED' AND reversed_at IS NOT NULL)
        )
);
