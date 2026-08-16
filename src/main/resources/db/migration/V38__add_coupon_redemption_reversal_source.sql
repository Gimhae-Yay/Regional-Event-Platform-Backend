CREATE TEMPORARY TABLE coupon_redemption_reversal_validation (
    is_valid BOOLEAN NOT NULL,
    CONSTRAINT ck_coupon_redemption_reversal_validation CHECK (is_valid = FALSE)
);

INSERT INTO coupon_redemption_reversal_validation (is_valid)
SELECT TRUE
WHERE EXISTS (
    SELECT 1
    FROM coupon_redemption
    WHERE status = 'REVERSED'
);

DROP TABLE coupon_redemption_reversal_validation;

ALTER TABLE coupon_redemption
    ADD COLUMN refund_id BIGINT NULL;

ALTER TABLE coupon_redemption
    ADD COLUMN reversal_reason_code VARCHAR(50) NULL;

ALTER TABLE coupon_redemption
    ADD CONSTRAINT uk_coupon_redemption_refund UNIQUE (refund_id);

ALTER TABLE coupon_redemption
    ADD CONSTRAINT fk_coupon_redemption_refund
        FOREIGN KEY (refund_id) REFERENCES refund (refund_id) ON DELETE RESTRICT;

ALTER TABLE coupon_redemption
    DROP CONSTRAINT ck_coupon_redemption_reversed_at;

ALTER TABLE coupon_redemption
    ADD CONSTRAINT ck_coupon_redemption_reversal
        CHECK (
            (status = 'CONFIRMED'
                AND refund_id IS NULL
                AND reversal_reason_code IS NULL
                AND reversed_at IS NULL)
            OR (status = 'REVERSED'
                AND refund_id IS NOT NULL
                AND reversal_reason_code = 'REFUND_SUCCEEDED'
                AND reversed_at IS NOT NULL)
            OR (status = 'REVERSED'
                AND refund_id IS NULL
                AND reversal_reason_code = 'RESERVATION_CANCELLED'
                AND reversed_at IS NOT NULL)
        );
