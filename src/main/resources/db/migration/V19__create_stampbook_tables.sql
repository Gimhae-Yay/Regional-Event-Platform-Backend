CREATE TABLE stampbook (
    stampbook_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    reward_coupon_policy_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP(6),
    ended_at TIMESTAMP(6),
    CONSTRAINT pk_stampbook PRIMARY KEY (stampbook_id),
    CONSTRAINT fk_stampbook_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_stampbook_reward_coupon_policy
        FOREIGN KEY (reward_coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id),
    CONSTRAINT ck_stampbook_status
        CHECK (REGEXP_LIKE(status, '^(DRAFT|PENDING_REVIEW|PUBLISHED|ENDED)$', 'c')),
    CONSTRAINT ck_stampbook_status_timestamps
        CHECK (
            CASE
                WHEN REGEXP_LIKE(status, '^DRAFT$', 'c')
                    AND published_at IS NULL
                    AND ended_at IS NULL THEN 1
                WHEN REGEXP_LIKE(status, '^PENDING_REVIEW$', 'c')
                    AND published_at IS NULL
                    AND ended_at IS NULL THEN 1
                WHEN REGEXP_LIKE(status, '^PUBLISHED$', 'c')
                    AND published_at IS NOT NULL
                    AND ended_at IS NULL THEN 1
                WHEN REGEXP_LIKE(status, '^ENDED$', 'c')
                    AND published_at IS NOT NULL
                    AND ended_at IS NOT NULL THEN 1
                ELSE 0
            END = 1
        )
);

CREATE TABLE stampbook_content (
    stampbook_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    CONSTRAINT pk_stampbook_content PRIMARY KEY (stampbook_id, content_id),
    CONSTRAINT fk_stampbook_content_stampbook
        FOREIGN KEY (stampbook_id) REFERENCES stampbook (stampbook_id),
    CONSTRAINT fk_stampbook_content_content
        FOREIGN KEY (content_id) REFERENCES content (content_id)
);
