CREATE TABLE mission_reward_claim (
    mission_reward_claim_id BIGINT NOT NULL AUTO_INCREMENT,
    mission_participation_id BIGINT NOT NULL,
    coupon_policy_id BIGINT NOT NULL,
    claimed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_mission_reward_claim PRIMARY KEY (mission_reward_claim_id),
    CONSTRAINT uk_mission_reward_claim_participation UNIQUE (mission_participation_id),
    CONSTRAINT fk_mission_reward_claim_participation
        FOREIGN KEY (mission_participation_id) REFERENCES mission_participation (mission_participation_id),
    CONSTRAINT fk_mission_reward_claim_coupon_policy
        FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id)
);

CREATE TABLE coupon (
    coupon_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_policy_id BIGINT NOT NULL,
    user_id BIGINT,
    status VARCHAR(30) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_coupon PRIMARY KEY (coupon_id),
    CONSTRAINT fk_coupon_policy
        FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id),
    CONSTRAINT fk_coupon_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_coupon_status
        CHECK (status REGEXP '^(AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED)$')
);

CREATE INDEX idx_coupon_status_expires_at
    ON coupon (status, expires_at);

CREATE TABLE coupon_issuance (
    coupon_issuance_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    coupon_policy_id BIGINT NOT NULL,
    recipient_user_id BIGINT,
    visit_id BIGINT,
    mission_reward_claim_id BIGINT,
    stampbook_reward_grant_id BIGINT,
    issuance_identity_hash VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_coupon_issuance PRIMARY KEY (coupon_issuance_id),
    CONSTRAINT uk_coupon_issuance_coupon UNIQUE (coupon_id),
    CONSTRAINT uk_coupon_issuance_identity_hash UNIQUE (issuance_identity_hash),
    CONSTRAINT uk_coupon_issuance_mission_reward_claim UNIQUE (mission_reward_claim_id),
    CONSTRAINT uk_coupon_issuance_stampbook_reward_grant UNIQUE (stampbook_reward_grant_id),
    CONSTRAINT fk_coupon_issuance_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT fk_coupon_issuance_coupon_policy
        FOREIGN KEY (coupon_policy_id) REFERENCES coupon_policy (coupon_policy_id),
    CONSTRAINT fk_coupon_issuance_recipient_user
        FOREIGN KEY (recipient_user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_coupon_issuance_visit
        FOREIGN KEY (visit_id) REFERENCES visit (visit_id),
    CONSTRAINT fk_coupon_issuance_mission_reward_claim
        FOREIGN KEY (mission_reward_claim_id) REFERENCES mission_reward_claim (mission_reward_claim_id),
    CONSTRAINT fk_coupon_issuance_stampbook_reward_grant
        FOREIGN KEY (stampbook_reward_grant_id) REFERENCES stampbook_reward_grant (stampbook_reward_grant_id),
    CONSTRAINT ck_coupon_issuance_exactly_one_source
        CHECK (
            (CASE WHEN visit_id IS NOT NULL THEN 1 ELSE 0 END)
            + (CASE WHEN mission_reward_claim_id IS NOT NULL THEN 1 ELSE 0 END)
            + (CASE WHEN stampbook_reward_grant_id IS NOT NULL THEN 1 ELSE 0 END) = 1
        )
);

CREATE TABLE coupon_status_history (
    coupon_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    previous_status VARCHAR(30),
    next_status VARCHAR(30) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    actor_kind VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_coupon_status_history PRIMARY KEY (coupon_status_history_id),
    CONSTRAINT fk_coupon_status_history_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT ck_coupon_status_history_previous_status
        CHECK (previous_status IS NULL OR previous_status REGEXP '^(AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED)$'),
    CONSTRAINT ck_coupon_status_history_next_status
        CHECK (next_status REGEXP '^(AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED)$')
);
