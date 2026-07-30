CREATE INDEX idx_idempotency_record_status_expires_at
    ON idempotency_record (status, expires_at);
