ALTER TABLE capacity_hold
    DROP CONSTRAINT ck_capacity_hold_terminal;

ALTER TABLE capacity_hold
    ADD CONSTRAINT ck_capacity_hold_terminal
        CHECK (
            CASE
                WHEN status = 'ACTIVE'
                    AND terminal_at IS NULL
                    AND capacity_released_at IS NULL
                    AND invalidation_reason IS NULL THEN 1
                WHEN status = 'CONSUMED'
                    AND terminal_at IS NOT NULL
                    AND capacity_released_at IS NULL
                    AND invalidation_reason IS NULL THEN 1
                WHEN status = 'EXPIRED'
                    AND terminal_at IS NOT NULL
                    AND capacity_released_at IS NOT NULL
                    AND invalidation_reason IS NULL THEN 1
                WHEN status = 'INVALIDATED'
                    AND terminal_at IS NOT NULL
                    AND capacity_released_at IS NOT NULL
                    AND invalidation_reason IS NOT NULL
                    AND REGEXP_LIKE(invalidation_reason, CONCAT(CAST(CHAR(92) AS CHAR), 'S')) THEN 1
                ELSE 0
            END = 1
        );
