CREATE TABLE mission (
    mission_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    condition_type VARCHAR(30) NOT NULL,
    required_visit_count INT,
    reward_coupon_policy_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    ended_at TIMESTAMP(6),
    CONSTRAINT pk_mission PRIMARY KEY (mission_id),
    CONSTRAINT fk_mission_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_mission_reward_coupon_policy
        FOREIGN KEY (reward_coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id),
    CONSTRAINT ck_mission_condition_type
        CHECK (condition_type REGEXP '^(VISIT_COUNT|CONTENT_SET)$'),
    CONSTRAINT ck_mission_required_visit_count
        CHECK (
            CASE
                WHEN condition_type = 'VISIT_COUNT' AND required_visit_count > 0 THEN 1
                WHEN condition_type = 'CONTENT_SET' AND required_visit_count IS NULL THEN 1
                ELSE 0
            END = 1
        ),
    CONSTRAINT ck_mission_status
        CHECK (status REGEXP '^(DRAFT|PENDING_REVIEW|PUBLISHED|ENDED)$'),
    CONSTRAINT ck_mission_status_timestamps
        CHECK (
            CASE
        WHEN status = 'DRAFT'
            AND published_at IS NULL
            AND ended_at IS NULL THEN 1
        WHEN status = 'PENDING_REVIEW'
            AND published_at IS NULL
            AND ended_at IS NULL THEN 1
                WHEN status = 'PUBLISHED'
                    AND published_at IS NOT NULL
                    AND published_at < ends_at
                    AND ended_at IS NULL THEN 1
                WHEN status = 'ENDED'
                    AND published_at IS NOT NULL
                    AND published_at < ends_at
                    AND ended_at IS NOT NULL THEN 1
                ELSE 0
            END = 1
        )
);

CREATE INDEX idx_mission_status_ends_at
    ON mission (status, ends_at);

CREATE TABLE mission_target_content (
    mission_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    CONSTRAINT pk_mission_target_content PRIMARY KEY (mission_id, content_id),
    CONSTRAINT fk_mission_target_content_mission
        FOREIGN KEY (mission_id) REFERENCES mission (mission_id),
    CONSTRAINT fk_mission_target_content_content
        FOREIGN KEY (content_id) REFERENCES content (content_id)
);
