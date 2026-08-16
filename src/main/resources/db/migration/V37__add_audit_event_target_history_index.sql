CREATE INDEX idx_audit_event_target_history
    ON audit_event (target_type, target_id, occurred_at);
