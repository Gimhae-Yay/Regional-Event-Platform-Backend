CREATE TABLE mission_participation (
    mission_participation_id BIGINT NOT NULL AUTO_INCREMENT,
    mission_id BIGINT NOT NULL,
    user_id BIGINT,
    status VARCHAR(30) NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT pk_mission_participation PRIMARY KEY (mission_participation_id),
    CONSTRAINT uk_mission_participation_mission_user UNIQUE (mission_id, user_id),
    CONSTRAINT fk_mission_participation_mission
        FOREIGN KEY (mission_id) REFERENCES mission (mission_id),
    CONSTRAINT fk_mission_participation_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_mission_participation_status
        CHECK (REGEXP_LIKE(status, '^(IN_PROGRESS|COMPLETED|ENDED_INCOMPLETE)$', 'c')),
    CONSTRAINT ck_mission_participation_status_completed_at
        CHECK (
            CASE
                WHEN REGEXP_LIKE(status, '^IN_PROGRESS$', 'c')
                    AND completed_at IS NULL THEN 1
                WHEN REGEXP_LIKE(status, '^COMPLETED$', 'c')
                    AND completed_at IS NOT NULL THEN 1
                WHEN REGEXP_LIKE(status, '^ENDED_INCOMPLETE$', 'c')
                    AND completed_at IS NULL THEN 1
                ELSE 0
            END = 1
        )
);

CREATE TABLE mission_progress (
    mission_participation_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_mission_progress PRIMARY KEY (mission_participation_id, visit_id),
    CONSTRAINT fk_mission_progress_participation
        FOREIGN KEY (mission_participation_id) REFERENCES mission_participation (mission_participation_id),
    CONSTRAINT fk_mission_progress_visit
        FOREIGN KEY (visit_id) REFERENCES visit (visit_id),
    CONSTRAINT fk_mission_progress_content
        FOREIGN KEY (content_id) REFERENCES content (content_id)
);
