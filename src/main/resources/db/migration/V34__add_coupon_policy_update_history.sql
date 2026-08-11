ALTER TABLE coupon_policy
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE TABLE coupon_policy_update_history (
    coupon_policy_update_history_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_policy_id BIGINT NOT NULL,
    audit_event_id BIGINT,
    actor_kind VARCHAR(30) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    previous_name VARCHAR(255) NOT NULL,
    next_name VARCHAR(255) NOT NULL,
    previous_description VARCHAR(1000),
    next_description VARCHAR(1000),
    previous_discount_amount BIGINT NOT NULL,
    next_discount_amount BIGINT NOT NULL,
    previous_minimum_payment_amount BIGINT NOT NULL,
    next_minimum_payment_amount BIGINT NOT NULL,
    previous_valid_days INT NOT NULL,
    next_valid_days INT NOT NULL,
    previous_issue_starts_at TIMESTAMP(6) NOT NULL,
    next_issue_starts_at TIMESTAMP(6) NOT NULL,
    previous_issue_ends_at TIMESTAMP(6) NOT NULL,
    next_issue_ends_at TIMESTAMP(6) NOT NULL,
    previous_total_issue_limit BIGINT,
    next_total_issue_limit BIGINT,
    CONSTRAINT pk_coupon_policy_update_history PRIMARY KEY (coupon_policy_update_history_id),
    CONSTRAINT uk_coupon_policy_update_history_audit_event UNIQUE (audit_event_id),
    CONSTRAINT fk_coupon_policy_update_history_policy
        FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id),
    CONSTRAINT fk_coupon_policy_update_history_audit
        FOREIGN KEY (audit_event_id) REFERENCES audit_event (audit_event_id) ON DELETE SET NULL,
    CONSTRAINT ck_coupon_policy_update_history_actor_kind
        CHECK (actor_kind = 'USER'),
    CONSTRAINT ck_coupon_policy_update_history_actor_role
        CHECK (actor_role = 'OPERATOR'),
    CONSTRAINT ck_coupon_policy_update_history_reason
        CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_coupon_policy_update_history_previous_discount
        CHECK (previous_discount_amount >= 1),
    CONSTRAINT ck_coupon_policy_update_history_next_discount
        CHECK (next_discount_amount >= 1),
    CONSTRAINT ck_coupon_policy_update_history_previous_minimum_payment
        CHECK (previous_minimum_payment_amount >= previous_discount_amount),
    CONSTRAINT ck_coupon_policy_update_history_next_minimum_payment
        CHECK (next_minimum_payment_amount >= next_discount_amount),
    CONSTRAINT ck_coupon_policy_update_history_previous_valid_days
        CHECK (previous_valid_days BETWEEN 1 AND 365),
    CONSTRAINT ck_coupon_policy_update_history_next_valid_days
        CHECK (next_valid_days BETWEEN 1 AND 365),
    CONSTRAINT ck_coupon_policy_update_history_previous_issue_period
        CHECK (previous_issue_starts_at < previous_issue_ends_at),
    CONSTRAINT ck_coupon_policy_update_history_next_issue_period
        CHECK (next_issue_starts_at < next_issue_ends_at),
    CONSTRAINT ck_coupon_policy_update_history_previous_total_issue_limit
        CHECK (previous_total_issue_limit IS NULL OR previous_total_issue_limit >= 1),
    CONSTRAINT ck_coupon_policy_update_history_next_total_issue_limit
        CHECK (next_total_issue_limit IS NULL OR next_total_issue_limit >= 1)
);
