CREATE TABLE coupon_policy (
    coupon_policy_id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    issuance_type VARCHAR(30) NOT NULL,
    discount_amount BIGINT NOT NULL,
    minimum_payment_amount BIGINT NOT NULL,
    valid_days INT NOT NULL,
    issue_starts_at TIMESTAMP(6) NOT NULL,
    issue_ends_at TIMESTAMP(6) NOT NULL,
    total_issue_limit BIGINT,
    issued_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP(6),
    ended_at TIMESTAMP(6),
    CONSTRAINT pk_coupon_policy PRIMARY KEY (coupon_policy_id),
    CONSTRAINT fk_coupon_policy_content_region
        FOREIGN KEY (content_id, region_id) REFERENCES content (content_id, region_id),
    CONSTRAINT fk_coupon_policy_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT ck_coupon_policy_issuance_type
        CHECK (issuance_type REGEXP '^(VISIT|MISSION_REWARD|STAMPBOOK_COMPLETION)$'),
    CONSTRAINT ck_coupon_policy_discount_amount
        CHECK (discount_amount >= 1),
    CONSTRAINT ck_coupon_policy_minimum_payment_amount
        CHECK (minimum_payment_amount >= discount_amount),
    CONSTRAINT ck_coupon_policy_valid_days
        CHECK (valid_days BETWEEN 1 AND 365),
    CONSTRAINT ck_coupon_policy_issue_period
        CHECK (issue_starts_at < issue_ends_at),
    CONSTRAINT ck_coupon_policy_total_issue_limit
        CHECK (total_issue_limit IS NULL OR total_issue_limit >= 1),
    CONSTRAINT ck_coupon_policy_issued_count
        CHECK (issued_count >= 0),
    CONSTRAINT ck_coupon_policy_issued_count_limit
        CHECK (total_issue_limit IS NULL OR issued_count <= total_issue_limit),
    CONSTRAINT ck_coupon_policy_status
        CHECK (status REGEXP '^(DRAFT|PUBLISHED|ENDED)$'),
    CONSTRAINT ck_coupon_policy_status_timestamps
        CHECK (
            CASE
                WHEN status = 'DRAFT' AND published_at IS NULL AND ended_at IS NULL THEN 1
                WHEN status = 'PUBLISHED' AND published_at IS NOT NULL AND ended_at IS NULL THEN 1
                WHEN status = 'ENDED' AND published_at IS NOT NULL AND ended_at IS NOT NULL THEN 1
                ELSE 0
            END = 1
        )
);
