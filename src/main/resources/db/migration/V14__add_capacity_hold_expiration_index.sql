CREATE INDEX idx_capacity_hold_status_expires_at
    ON capacity_hold (status, expires_at);
