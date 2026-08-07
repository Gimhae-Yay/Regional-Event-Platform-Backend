ALTER TABLE content_session
    ADD CONSTRAINT ck_content_session_time_range_v2
        CHECK (
            starts_at < ends_at
            AND checkin_open_at < checkin_close_at
            AND ends_at > checkin_close_at
        );

ALTER TABLE content_session
    DROP CONSTRAINT ck_content_session_time_range;
