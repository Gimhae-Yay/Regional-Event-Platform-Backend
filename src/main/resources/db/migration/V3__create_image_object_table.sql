CREATE TABLE image_object (
    image_object_id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(768) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    checksum VARCHAR(255) NOT NULL,
    lifecycle_status VARCHAR(30) NOT NULL,
    delete_attempt_count INT NOT NULL,
    last_delete_attempted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_image_object PRIMARY KEY (image_object_id),
    CONSTRAINT uk_image_object_object_key UNIQUE (object_key),
    CONSTRAINT ck_image_object_byte_size CHECK (byte_size >= 0),
    CONSTRAINT ck_image_object_delete_attempt_count CHECK (delete_attempt_count >= 0)
);

CREATE INDEX idx_image_object_lifecycle_status_last_delete_attempted_at
    ON image_object (lifecycle_status, last_delete_attempted_at);
