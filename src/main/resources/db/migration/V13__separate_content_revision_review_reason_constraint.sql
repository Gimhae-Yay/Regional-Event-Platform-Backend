ALTER TABLE content_revision
    DROP CONSTRAINT ck_content_revision_reviewed;

ALTER TABLE content_revision
    ADD CONSTRAINT ck_content_revision_reviewed
        CHECK (
            (status <> 'EDIT_APPROVED' AND status <> 'EDIT_REJECTED')
            OR (status = 'EDIT_APPROVED'
                AND reviewed_at IS NOT NULL
                AND reviewed_by_user_id IS NOT NULL
                AND review_reason IS NULL)
            OR (status = 'EDIT_REJECTED'
                AND reviewed_at IS NOT NULL
                AND reviewed_by_user_id IS NOT NULL
                AND review_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(review_reason)) > 0)
        );
