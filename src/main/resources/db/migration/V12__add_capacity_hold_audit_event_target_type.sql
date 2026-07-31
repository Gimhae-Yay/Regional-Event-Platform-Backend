ALTER TABLE audit_event
    DROP CONSTRAINT ck_audit_event_target_type;

ALTER TABLE audit_event
    ADD CONSTRAINT ck_audit_event_target_type
        CHECK (target_type REGEXP '^(REGION|OPERATOR_APPLICATION|CONTENT|CONTENT_SESSION|CAPACITY_HOLD|RESERVATION|VISIT|REVIEW)$');
