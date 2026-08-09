CREATE TABLE stamp_earn (
    stamp_earn_id BIGINT NOT NULL AUTO_INCREMENT,
    stampbook_progress_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    earned_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_stamp_earn PRIMARY KEY (stamp_earn_id),
    CONSTRAINT uk_stamp_earn_progress_visit UNIQUE (stampbook_progress_id, visit_id),
    CONSTRAINT uk_stamp_earn_progress_content UNIQUE (stampbook_progress_id, content_id),
    CONSTRAINT fk_stamp_earn_progress
        FOREIGN KEY (stampbook_progress_id) REFERENCES stampbook_progress (stampbook_progress_id),
    CONSTRAINT fk_stamp_earn_visit
        FOREIGN KEY (visit_id) REFERENCES visit (visit_id),
    CONSTRAINT fk_stamp_earn_content
        FOREIGN KEY (content_id) REFERENCES content (content_id)
);
