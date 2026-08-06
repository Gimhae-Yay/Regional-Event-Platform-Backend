ALTER TABLE session_revision
    ADD COLUMN pending_target_session_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN target_session_id ELSE NULL END
    );

ALTER TABLE session_revision
    ADD CONSTRAINT uk_session_revision_pending_target_session UNIQUE (pending_target_session_id);
