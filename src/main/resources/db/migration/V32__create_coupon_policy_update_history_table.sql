CREATE TABLE coupon_policy_update_history (
    coupon_policy_update_history_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_policy_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    previous_name VARCHAR(255) NOT NULL,
    previous_description VARCHAR(1000),
    previous_discount_amount BIGINT NOT NULL,
    previous_minimum_payment_amount BIGINT NOT NULL,
    previous_valid_days INT NOT NULL,
    previous_issue_starts_at TIMESTAMP(6) NOT NULL,
    previous_issue_ends_at TIMESTAMP(6) NOT NULL,
    previous_total_issue_limit BIGINT,
    next_name VARCHAR(255) NOT NULL,
    next_description VARCHAR(1000),
    next_discount_amount BIGINT NOT NULL,
    next_minimum_payment_amount BIGINT NOT NULL,
    next_valid_days INT NOT NULL,
    next_issue_starts_at TIMESTAMP(6) NOT NULL,
    next_issue_ends_at TIMESTAMP(6) NOT NULL,
    next_total_issue_limit BIGINT,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_coupon_policy_update_history PRIMARY KEY (coupon_policy_update_history_id),
    CONSTRAINT fk_coupon_policy_update_history_policy
        FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id),
    CONSTRAINT fk_coupon_policy_update_history_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_user (user_id)
);

CREATE INDEX idx_coupon_policy_update_history_policy_occurred
    ON coupon_policy_update_history (coupon_policy_id, occurred_at, coupon_policy_update_history_id);
