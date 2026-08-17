ALTER TABLE content_revision
    DROP CONSTRAINT ck_content_revision_invalidated;

ALTER TABLE content_revision
    ADD CONSTRAINT ck_content_revision_invalidated
        CHECK (
            status <> 'EDIT_INVALIDATED'
            OR (
                invalidated_at IS NOT NULL
                AND invalidation_reason REGEXP
                    '^(CONTENT_SUSPENDED|CONTENT_ENDED|CONTENT_WITHDRAWN)$'
            )
        );
