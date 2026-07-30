ALTER TABLE idempotency_record
    DROP CONSTRAINT ck_idempotency_record_result;

ALTER TABLE idempotency_record
    ADD CONSTRAINT ck_idempotency_record_processing_result
        CHECK (
            status <> 'PROCESSING'
            OR (result_reservation_id IS NULL AND result_visit_id IS NULL)
        );

ALTER TABLE idempotency_record
    ADD CONSTRAINT ck_idempotency_record_failed_result
        CHECK (
            status <> 'FAILED'
            OR (result_reservation_id IS NULL AND result_visit_id IS NULL)
        );

ALTER TABLE idempotency_record
    ADD CONSTRAINT ck_idempotency_record_reservation_result
        CHECK (
            status <> 'SUCCEEDED'
            OR operation <> 'RESERVATION_CONFIRM'
            OR (result_reservation_id IS NOT NULL AND result_visit_id IS NULL)
        );

ALTER TABLE idempotency_record
    ADD CONSTRAINT ck_idempotency_record_visit_result
        CHECK (
            status <> 'SUCCEEDED'
            OR operation <> 'CHECK_IN'
            OR (result_reservation_id IS NULL AND result_visit_id IS NOT NULL)
        );
