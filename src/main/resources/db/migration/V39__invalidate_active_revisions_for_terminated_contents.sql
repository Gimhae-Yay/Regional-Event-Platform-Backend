UPDATE content_revision revision
SET invalidated_at = (
        SELECT termination_log.date
        FROM content
        JOIN content_log termination_log
            ON termination_log.id = (
                SELECT latest_log.id
                FROM content_log latest_log
                WHERE latest_log.content_id = content.content_id
                    AND latest_log.status = content.status
                ORDER BY latest_log.date DESC, latest_log.id DESC
                LIMIT 1
            )
        WHERE content.content_id = revision.content_id
            AND content.status IN ('SUSPENDED', 'ENDED')
    ),
    invalidated_by_user_id = (
        SELECT termination_log.actor_id
        FROM content
        JOIN content_log termination_log
            ON termination_log.id = (
                SELECT latest_log.id
                FROM content_log latest_log
                WHERE latest_log.content_id = content.content_id
                    AND latest_log.status = content.status
                ORDER BY latest_log.date DESC, latest_log.id DESC
                LIMIT 1
            )
        WHERE content.content_id = revision.content_id
            AND content.status IN ('SUSPENDED', 'ENDED')
    ),
    invalidation_reason = (
        SELECT CASE content.status
            WHEN 'SUSPENDED' THEN 'CONTENT_SUSPENDED'
            WHEN 'ENDED' THEN 'CONTENT_ENDED'
        END
        FROM content
        WHERE content.content_id = revision.content_id
            AND content.status IN ('SUSPENDED', 'ENDED')
    ),
    status = 'EDIT_INVALIDATED'
WHERE revision.status = 'EDIT_REQUESTED'
    AND EXISTS (
        SELECT 1
        FROM content
        WHERE content.content_id = revision.content_id
            AND content.status IN ('SUSPENDED', 'ENDED')
    );

ALTER TABLE content_revision
    ADD CONSTRAINT ck_content_revision_invalidated
        CHECK (
            status <> 'EDIT_INVALIDATED'
            OR (
                invalidated_at IS NOT NULL
                AND invalidation_reason IN ('CONTENT_SUSPENDED', 'CONTENT_ENDED')
            )
        );
