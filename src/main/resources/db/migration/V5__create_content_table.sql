CREATE TABLE content (
    content_id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location_text VARCHAR(255) NOT NULL,
    operating_hours_text TEXT NOT NULL,
    contact_text VARCHAR(255) NOT NULL,
    precautions TEXT NOT NULL,
    age_requirement VARCHAR(255) NOT NULL,
    materials TEXT NOT NULL,
    cancellation_policy_text TEXT NOT NULL,
    publish_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_content PRIMARY KEY (content_id),
    CONSTRAINT fk_content_region
        FOREIGN KEY (region_id) REFERENCES region (region_id),
    CONSTRAINT fk_content_operator
        FOREIGN KEY (operator_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_content_type
        CHECK (content_type = 'EVENT_EXPERIENCE'),
    CONSTRAINT ck_content_status
        CHECK (
            status IN (
                'PENDING',
                'REJECTED',
                'APPROVED',
                'PUBLISHED',
                'SUSPENDED',
                'WITHDRAWN',
                'ENDED'
            )
        ),
    CONSTRAINT ck_content_soft_delete_status
        CHECK (deleted_at IS NULL OR status IN ('PENDING', 'APPROVED'))
);

CREATE INDEX idx_content_region_status_type_publish_deleted
    ON content (region_id, status, content_type, publish_at, deleted_at);

CREATE INDEX idx_content_status_publish_deleted
    ON content (status, publish_at, deleted_at);
