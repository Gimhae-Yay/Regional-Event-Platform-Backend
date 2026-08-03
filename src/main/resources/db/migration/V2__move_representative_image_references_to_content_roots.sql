ALTER TABLE content
    ADD COLUMN representative_image_object_id BIGINT;

ALTER TABLE content
    ADD COLUMN representative_image_assigned_at TIMESTAMP(6);

ALTER TABLE content_revision
    ADD COLUMN candidate_image_object_id BIGINT;

ALTER TABLE content_revision
    ADD COLUMN candidate_image_assigned_at TIMESTAMP(6);

UPDATE content
SET representative_image_object_id = (
        SELECT image_object_id
        FROM content_representative_image
        WHERE content_representative_image.content_id = content.content_id
    ),
    representative_image_assigned_at = (
        SELECT assigned_at
        FROM content_representative_image
        WHERE content_representative_image.content_id = content.content_id
    )
WHERE EXISTS (
    SELECT 1
    FROM content_representative_image
    WHERE content_representative_image.content_id = content.content_id
);

UPDATE content_revision
SET candidate_image_object_id = (
        SELECT image_object_id
        FROM content_revision_representative_image
        WHERE content_revision_representative_image.content_revision_id = content_revision.content_revision_id
    ),
    candidate_image_assigned_at = (
        SELECT assigned_at
        FROM content_revision_representative_image
        WHERE content_revision_representative_image.content_revision_id = content_revision.content_revision_id
    )
WHERE EXISTS (
    SELECT 1
    FROM content_revision_representative_image
    WHERE content_revision_representative_image.content_revision_id = content_revision.content_revision_id
);

DROP TABLE content_representative_image;

DROP TABLE content_revision_representative_image;

ALTER TABLE content
    ADD CONSTRAINT fk_content_representative_image_object
        FOREIGN KEY (representative_image_object_id) REFERENCES image_object (image_object_id);

ALTER TABLE content_revision
    ADD CONSTRAINT fk_content_revision_candidate_image_object
        FOREIGN KEY (candidate_image_object_id) REFERENCES image_object (image_object_id);
