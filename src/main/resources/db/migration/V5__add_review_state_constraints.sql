ALTER TABLE review
    ADD CONSTRAINT ck_review_state
        CHECK (
            (status = 'PUBLISHED'
                AND rating IS NOT NULL
                AND rating BETWEEN 1 AND 5
                AND review_text IS NOT NULL
                AND CHAR_LENGTH(review_text) BETWEEN 1 AND 2000
                AND TRIM(review_text) <> ''
                AND deleted_at IS NULL)
            OR (status = 'DELETED'
                AND deleted_at IS NOT NULL
                AND ((rating IS NOT NULL
                        AND rating BETWEEN 1 AND 5
                        AND review_text IS NOT NULL
                        AND CHAR_LENGTH(review_text) BETWEEN 1 AND 2000
                        AND TRIM(review_text) <> '')
                    OR (rating IS NULL AND review_text IS NULL)))
        );
