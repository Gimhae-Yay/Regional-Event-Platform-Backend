ALTER TABLE content_revision
    ADD COLUMN invalidated_at TIMESTAMP(6);

ALTER TABLE content_revision
    ADD COLUMN invalidated_by_user_id BIGINT;

ALTER TABLE content_revision
    ADD COLUMN invalidation_reason VARCHAR(30);

ALTER TABLE content_revision
    ADD CONSTRAINT fk_content_revision_invalidator
        FOREIGN KEY (invalidated_by_user_id) REFERENCES app_user (user_id);

ALTER TABLE content_revision
    DROP CONSTRAINT ck_content_revision_status;

ALTER TABLE content_revision
    ADD CONSTRAINT ck_content_revision_status
        CHECK (status REGEXP '^(EDIT_REQUESTED|EDIT_APPROVED|EDIT_REJECTED|EDIT_WITHDRAWN|EDIT_INVALIDATED)$');
