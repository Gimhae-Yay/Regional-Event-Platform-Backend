ALTER TABLE image_object
    ADD COLUMN created_by_user_id BIGINT;

ALTER TABLE image_object
    ADD COLUMN region_id BIGINT;

ALTER TABLE image_object
    ADD COLUMN upload_expires_at TIMESTAMP(6);

ALTER TABLE image_object
    ADD COLUMN linked_at TIMESTAMP(6);

ALTER TABLE image_object
    ADD CONSTRAINT fk_image_object_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (user_id);

ALTER TABLE image_object
    ADD CONSTRAINT fk_image_object_region
        FOREIGN KEY (region_id) REFERENCES region (region_id);

CREATE INDEX idx_image_object_upload_candidate
    ON image_object (region_id, created_by_user_id, lifecycle_status, upload_expires_at, linked_at);
