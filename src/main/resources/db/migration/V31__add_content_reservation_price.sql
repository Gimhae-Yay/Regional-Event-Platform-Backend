ALTER TABLE content
    ADD COLUMN reservation_price INT NOT NULL DEFAULT 0
        AFTER cancellation_policy_text,
    ADD CONSTRAINT ck_content_reservation_price
        CHECK (reservation_price >= 0);

ALTER TABLE content_revision
    ADD COLUMN reservation_price INT NOT NULL DEFAULT 0
        AFTER cancellation_policy_text,
    ADD CONSTRAINT ck_content_revision_reservation_price
        CHECK (reservation_price >= 0);
