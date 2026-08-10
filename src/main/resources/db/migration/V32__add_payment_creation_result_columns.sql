ALTER TABLE payment
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE payment_idempotency
    ADD COLUMN reservation_id BIGINT NULL;

ALTER TABLE payment_idempotency
    ADD CONSTRAINT uk_payment_idempotency_reservation UNIQUE (reservation_id);

ALTER TABLE payment_idempotency
    ADD CONSTRAINT fk_payment_idempotency_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id);

ALTER TABLE payment_idempotency
    DROP CONSTRAINT ck_payment_idempotency_result;

ALTER TABLE payment_idempotency
    ADD CONSTRAINT ck_payment_idempotency_result
        CHECK (
            (status = 'PROCESSING'
                AND payment_id IS NULL
                AND reservation_id IS NULL
                AND completed_at IS NULL)
            OR (status = 'SUCCEEDED'
                AND (
                    (payment_id IS NOT NULL AND reservation_id IS NULL)
                    OR (payment_id IS NULL AND reservation_id IS NOT NULL)
                )
                AND completed_at IS NOT NULL)
            OR (status = 'FAILED'
                AND payment_id IS NULL
                AND reservation_id IS NULL
                AND completed_at IS NOT NULL)
        );
