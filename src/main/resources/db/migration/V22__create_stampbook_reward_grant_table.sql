CREATE TABLE stampbook_reward_grant (
    stampbook_reward_grant_id BIGINT NOT NULL AUTO_INCREMENT,
    stampbook_progress_id BIGINT NOT NULL,
    coupon_policy_id BIGINT NOT NULL,
    granted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_stampbook_reward_grant PRIMARY KEY (stampbook_reward_grant_id),
    CONSTRAINT uk_stampbook_reward_grant_progress UNIQUE (stampbook_progress_id),
    CONSTRAINT fk_stampbook_reward_grant_progress
        FOREIGN KEY (stampbook_progress_id) REFERENCES stampbook_progress (stampbook_progress_id),
    CONSTRAINT fk_stampbook_reward_grant_coupon_policy
        FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id)
);
