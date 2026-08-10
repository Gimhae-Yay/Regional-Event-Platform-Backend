ALTER TABLE content
    ADD COLUMN reservation_price BIGINT NOT NULL DEFAULT 0;

ALTER TABLE content
    ADD CONSTRAINT ck_content_reservation_price
        CHECK (reservation_price >= 0);

ALTER TABLE content_revision
    ADD COLUMN reservation_price BIGINT NOT NULL DEFAULT 0;

ALTER TABLE content_revision
    ADD CONSTRAINT ck_content_revision_reservation_price
        CHECK (reservation_price >= 0);
